sealed interface StageOutput {
    class Shuffled(val shuffle: ShuffleOutput) : StageOutput

    class Result(val partitions: List<Partition>) : StageOutput
}

class StageReport(val stage: Stage, val tasks: Int, val output: StageOutput)

class JobOutcome(val stages: List<StageReport>) {
    val result: List<Partition> get() = (stages.last().output as StageOutput.Result).partitions
    val tasks: Int get() = stages.sumOf { it.tasks }
    val rows: Int get() = result.sumOf { it.size }
}

class JobExecution(
    private val plan: Plan,
    private val settings: Settings,
    private val dictionary: KeyDictionary,
    private val grouping: KeyGrouping,
) {
    private val writer = ShuffleWriter(dictionary, grouping)
    private val shuffles = mutableMapOf<PlanNode, ShuffleOutput>()

    fun run(): JobOutcome = JobOutcome(plan.stages.map(::runStage))

    private fun runStage(stage: Stage): StageReport {
        val partitions = evaluate(stage.output)
        return StageReport(stage, partitions.size, deliver(stage, partitions))
    }

    private fun deliver(stage: Stage, partitions: List<Partition>): StageOutput = when (val destination = stage.destination) {
        is Destination.Shuffle -> {
            val shuffle = writer.write(partitions, destination.exchange, settings.memoryLimit)
            shuffles[stage.output] = shuffle
            StageOutput.Shuffled(shuffle)
        }
        Destination.Result -> StageOutput.Result(partitions)
    }

    private fun evaluate(node: PlanNode): List<Partition> = when (node) {
        is SourceNode -> node.dataset.snapshot()
        is TransformNode -> transform(node.transform, inputOf(node.input))
    }

    private fun inputOf(input: PlanInput): List<Partition> = when (input.dependency) {
        Dependency.NARROW -> evaluate(input.node)
        Dependency.SHUFFLE -> shuffles.getValue(input.node).received
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
