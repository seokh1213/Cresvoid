class Planner(private val catalog: Catalog, private val settings: Settings) {
    fun plan(name: String): Plan = Plan(StageCutter(lineageOf(name)).stages())

    private fun lineageOf(name: String): PlanNode {
        catalog.dataset(name)?.let { return SourceNode(it) }
        val transforms = catalog.pipeline(name)?.transforms ?: error("$name is neither a dataset nor a pipeline")
        val source = transforms.first() as Source
        return transforms.drop(1).fold(lineageOf(source.name)) { input, transform ->
            when (transform) {
                is UnaryTransform -> transformNode(transform, input)
                is Join -> joinNode(transform, input, lineageOf(transform.name))
                is Source -> error("source comes only first")
            }
        }
    }

    private fun transformNode(transform: UnaryTransform, input: PlanNode): TransformNode = when (transform) {
        is ReduceByKey -> TransformNode(transform, keyedInput(input, transform.partitions), transform.partitions, transform.partitions)
        is GroupByKey -> TransformNode(transform, keyedInput(input, transform.partitions), transform.partitions, transform.partitions)
        is Repartition -> TransformNode(transform, PlanInput(input, Dependency.SHUFFLE), transform.partitions, null)
        is Coalesce -> TransformNode(transform, narrow(input), minOf(transform.partitions, input.partitions), null)
        is FilterKeyEquals, is FilterKeyDiffers, is FilterValue -> TransformNode(transform, narrow(input), input.partitions, input.hashPartitions)
        is MapKeyPrefix, is MapValueArithmetic, is MapValueSummary -> TransformNode(transform, narrow(input), input.partitions, null)
    }

    private fun joinNode(join: Join, left: PlanNode, right: PlanNode): JoinNode {
        val buildSide = buildSideOf(left.estimatedBytes, right.estimatedBytes, settings.broadcastThreshold)
            ?: return JoinNode(
                join,
                JoinStrategy.SortMerge,
                keyedInput(left, join.partitions),
                keyedInput(right, join.partitions),
                join.partitions,
                join.partitions,
            )
        val stream = if (buildSide == Side.LEFT) right else left
        return JoinNode(
            join,
            JoinStrategy.Broadcast(buildSide),
            PlanInput(left, if (buildSide == Side.LEFT) Dependency.BROADCAST else Dependency.NARROW),
            PlanInput(right, if (buildSide == Side.RIGHT) Dependency.BROADCAST else Dependency.NARROW),
            stream.partitions,
            stream.hashPartitions,
        )
    }

    private fun keyedInput(input: PlanNode, partitions: Int): PlanInput =
        PlanInput(input, if (input.hashPartitions == partitions) Dependency.NARROW else Dependency.SHUFFLE)

    private fun narrow(input: PlanNode): PlanInput = PlanInput(input, Dependency.NARROW)
}

fun buildSideOf(leftBytes: Long?, rightBytes: Long?, threshold: Long?): Side? {
    if (threshold == null) return null
    val left = leftBytes?.takeIf { it <= threshold }
    val right = rightBytes?.takeIf { it <= threshold }
    return when {
        left != null && right != null -> if (left < right) Side.LEFT else Side.RIGHT
        left != null -> Side.LEFT
        right != null -> Side.RIGHT
        else -> null
    }
}

private class Boundary(val reader: PlanNode, val dependency: Dependency)

private class StageCutter(root: PlanNode) {
    private val stageEnds = mutableListOf<PlanNode>()
    private val members = mutableMapOf<PlanNode, MutableList<PlanNode>>()
    private val stageEndOf = mutableMapOf<PlanNode, PlanNode>()
    private val boundaries = mutableMapOf<PlanNode, Boundary>()

    init {
        visit(root, root)
    }

    fun stages(): List<Stage> {
        val numbers = stageEnds.withIndex().associate { (number, end) -> end to number }
        return stageEnds.mapIndexed { number, end -> Stage(number, end, members.getValue(end), destinationOf(end, numbers)) }
    }

    private fun visit(node: PlanNode, stageEnd: PlanNode) {
        for (input in node.inputs) {
            if (input.dependency == Dependency.NARROW) {
                visit(input.node, stageEnd)
            } else {
                boundaries[input.node] = Boundary(node, input.dependency)
                visit(input.node, input.node)
            }
        }
        members.getOrPut(stageEnd) { mutableListOf() } += node
        stageEndOf[node] = stageEnd
        if (node === stageEnd) stageEnds += node
    }

    private fun destinationOf(stageEnd: PlanNode, numbers: Map<PlanNode, Int>): Destination {
        val boundary = boundaries[stageEnd] ?: return Destination.Result
        val readerStage = numbers.getValue(stageEndOf.getValue(boundary.reader))
        return when (boundary.dependency) {
            Dependency.SHUFFLE -> Destination.Shuffle(readerStage, exchangeOf(boundary.reader))
            Dependency.BROADCAST -> Destination.Broadcast(readerStage)
            Dependency.NARROW -> error("a narrow input stays in its reader's stage")
        }
    }

    private fun exchangeOf(reader: PlanNode): Exchange = when (reader) {
        is JoinNode -> HashExchange(reader.join.partitions, combiner = null)
        is TransformNode -> when (val transform = reader.transform) {
            is ReduceByKey -> HashExchange(transform.partitions, transform.reducer)
            is GroupByKey -> HashExchange(transform.partitions, combiner = null)
            is Repartition -> RoundRobinExchange(transform.partitions)
            else -> error("$transform reads its input narrowly")
        }
        is SourceNode -> error("a source reads no input")
    }
}
