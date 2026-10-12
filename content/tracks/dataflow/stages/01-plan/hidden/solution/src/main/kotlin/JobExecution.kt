class StageReport(val stage: Stage, val partitions: List<Partition>)

class JobOutcome(val stages: List<StageReport>) {
    val result: List<Partition> get() = stages.last().partitions
    val tasks: Int get() = stages.sumOf { it.partitions.size }
    val rows: Int get() = result.sumOf { it.size }
}

class JobExecution(
    private val plan: Plan,
    private val dictionary: KeyDictionary,
    private val grouping: KeyGrouping,
) {
    private val writer = ShuffleWriter(dictionary, grouping)
    private val shuffles = mutableMapOf<PlanNode, List<Partition>>()

    fun run(): JobOutcome = JobOutcome(plan.stages.map(::runStage))

    private fun runStage(stage: Stage): StageReport {
        val partitions = evaluate(stage.output)
        val destination = stage.destination
        if (destination is Destination.Shuffle) shuffles[stage.output] = writer.write(partitions, destination.exchange)
        return StageReport(stage, partitions)
    }

    private fun evaluate(node: PlanNode): List<Partition> = when (node) {
        is SourceNode -> node.dataset.snapshot()
        is TransformNode -> transform(node.transform, inputOf(node.input))
    }

    private fun inputOf(input: PlanInput): List<Partition> = when (input.dependency) {
        Dependency.NARROW -> evaluate(input.node)
        Dependency.SHUFFLE -> shuffles.getValue(input.node)
    }

    private fun transform(transform: UnaryTransform, partitions: List<Partition>): List<Partition> = when (transform) {
        is MapKeyPrefix -> partitions.map { it.withKeyPrefix(transform.length, dictionary) }
        is MapValueArithmetic -> partitions.map { partition ->
            partition.mapNumbers { transform.arithmetic.apply(it, transform.operand) }
        }
        is MapValueSummary -> partitions.map { it.summarize(transform.summary) }
        is FilterKeyEquals -> {
            val key = dictionary.keyOf(transform.key)
            partitions.map { partition -> partition.keepRows { partition.keys[it] == key } }
        }
        is FilterKeyDiffers -> {
            val key = dictionary.keyOf(transform.key)
            partitions.map { partition -> partition.keepRows { partition.keys[it] != key && partition.keys[it] != NULL_KEY } }
        }
        is FilterValue -> partitions.map { partition ->
            val numbers = (partition.values as Numbers).numbers
            partition.keepRows { transform.comparison.holds(numbers[it], transform.operand) }
        }
        is ReduceByKey -> partitions.map { grouping.reduce(it, transform.reducer) }
        is GroupByKey -> partitions.map { grouping.group(it) }
        is Repartition -> partitions
        is Coalesce -> coalesce(partitions, transform.partitions)
    }
}
