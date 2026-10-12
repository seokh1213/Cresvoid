sealed interface StageOutput {
    class Shuffled(val shuffle: ShuffleOutput) : StageOutput

    class Broadcast(val rows: Partition) : StageOutput

    class Result(val partitions: List<Partition>) : StageOutput
}

class StageReport(
    val stage: Stage,
    val tasks: Int,
    val labels: List<String>,
    val decisions: StageDecisions,
    val output: StageOutput,
)

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
    private val broadcasts = mutableMapOf<PlanNode, Partition>()
    private val decider = StageDecider(settings, shuffles)

    fun run(): JobOutcome = JobOutcome(plan.stages.map(::runStage))

    private fun runStage(stage: Stage): StageReport {
        val decisions = decider.decide(stage)
        val partitions = StageRun(decisions).evaluate(stage.output)
        val labels = stage.nodes.map { node -> if (node is JoinNode) decisions.strategyOf(node).label else node.label }
        return StageReport(stage, partitions.size, labels, decisions, deliver(stage, partitions))
    }

    private fun deliver(stage: Stage, partitions: List<Partition>): StageOutput = when (val destination = stage.destination) {
        is Destination.Shuffle -> {
            val shuffle = writer.write(partitions, destination.exchange, settings.memoryLimit)
            shuffles[stage.output] = shuffle
            StageOutput.Shuffled(shuffle)
        }
        is Destination.Broadcast -> {
            val rows = Partition.concat(partitions)
            broadcasts[stage.output] = rows
            StageOutput.Broadcast(rows)
        }
        Destination.Result -> StageOutput.Result(partitions)
    }

    private inner class StageRun(private val decisions: StageDecisions) {
        fun evaluate(node: PlanNode): List<Partition> = when (node) {
            is SourceNode -> node.dataset.snapshot()
            is TransformNode -> transform(node.transform, inputOf(node.input))
            is JoinNode -> join(node)
        }

        private fun inputOf(input: PlanInput): List<Partition> = when (input.dependency) {
            Dependency.NARROW -> evaluate(input.node)
            Dependency.SHUFFLE -> readByNumber(shuffles.getValue(input.node))
            Dependency.BROADCAST -> error("a join reads its broadcast input whole")
        }

        private fun readByNumber(shuffle: ShuffleOutput): List<Partition> {
            val merge = decisions.merge ?: return shuffle.received
            return merge.groups.map { group -> Partition.concat(shuffle.received.subList(group.first, group.last + 1)) }
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

        private fun join(node: JoinNode): List<Partition> {
            val strategy = decisions.strategyOf(node)
            val skew = decisions.skew?.takeIf { it.join === node }
            return when {
                strategy is JoinStrategy.Broadcast -> broadcastJoin(node, strategy.buildSide)
                skew != null -> skewJoin(node, skew)
                else -> sortMergeJoin(node)
            }
        }

        private fun broadcastJoin(node: JoinNode, buildSide: Side): List<Partition> {
            val build = grouping.group(buildRows(node.input(buildSide)))
            return inputOf(node.input(buildSide.opposite)).map { stream ->
                val streamGroups = grouping.group(stream)
                if (buildSide == Side.LEFT) {
                    joinGroups(build, streamGroups, node.join.nullSafe, dictionary)
                } else {
                    joinGroups(streamGroups, build, node.join.nullSafe, dictionary)
                }
            }
        }

        private fun buildRows(input: PlanInput): Partition = when (input.dependency) {
            Dependency.BROADCAST -> broadcasts.getValue(input.node)
            Dependency.SHUFFLE -> Partition.concat(shuffles.getValue(input.node).received)
            Dependency.NARROW -> error("a narrow input never becomes the build side")
        }

        private fun sortMergeJoin(node: JoinNode): List<Partition> {
            val left = inputOf(node.left)
            val right = inputOf(node.right)
            return left.indices.map { partition ->
                joinGroups(grouping.group(left[partition]), grouping.group(right[partition]), node.join.nullSafe, dictionary)
            }
        }

        private fun skewJoin(node: JoinNode, skew: SkewSplit): List<Partition> {
            val left = shuffles.getValue(node.left.node).received
            val right = shuffles.getValue(node.right.node).received
            return left.indices.flatMap { partition ->
                val leftPieces = skew.left[partition].map { grouping.group(left[partition].slice(it.first, it.last + 1)) }
                val rightPieces = skew.right[partition].map { grouping.group(right[partition].slice(it.first, it.last + 1)) }
                leftPieces.flatMap { leftPiece ->
                    rightPieces.map { rightPiece -> joinGroups(leftPiece, rightPiece, node.join.nullSafe, dictionary) }
                }
            }
        }
    }
}
