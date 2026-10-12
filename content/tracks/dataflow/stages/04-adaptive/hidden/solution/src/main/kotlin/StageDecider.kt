class StageDecisions(
    val conversions: Map<JoinNode, Side>,
    val skew: SkewSplit?,
    val merge: PartitionMerge?,
) {
    fun strategyOf(join: JoinNode): JoinStrategy = conversions[join]?.let { JoinStrategy.Broadcast(it) } ?: join.strategy
}

private class NumberedShuffle(val shuffle: ShuffleOutput, val forRepartition: Boolean)

class StageDecider(private val settings: Settings, private val shuffles: Map<PlanNode, ShuffleOutput>) {
    fun decide(stage: Stage): StageDecisions {
        val conversions = if (settings.adaptive) joinConversions(stage) else emptyMap()
        val skew = skewSplitOf(stage, conversions)
        val merge = if (settings.adaptive && skew == null) partitionMergeOf(stage, conversions) else null
        return StageDecisions(conversions, skew, merge)
    }

    private fun joinConversions(stage: Stage): Map<JoinNode, Side> = stage.nodes
        .filterIsInstance<JoinNode>()
        .filter { it.strategy == JoinStrategy.SortMerge }
        .mapNotNull { join -> convertedBuildSide(join)?.let { join to it } }
        .toMap()

    private fun convertedBuildSide(join: JoinNode): Side? {
        val leftBytes = shuffledBytes(join.left)
        val rightBytes = shuffledBytes(join.right)
        if (leftBytes == null && rightBytes == null) return null
        return buildSideOf(leftBytes, rightBytes, settings.broadcastThreshold)
    }

    private fun shuffledBytes(input: PlanInput): Long? =
        if (input.dependency == Dependency.SHUFFLE) shuffles.getValue(input.node).bytes else null

    private fun skewSplitOf(stage: Stage, conversions: Map<JoinNode, Side>): SkewSplit? {
        val join = stage.nodes.singleOrNull { it is JoinNode } as JoinNode? ?: return null
        val handlesSkew = join.strategy == JoinStrategy.SortMerge &&
            join !in conversions &&
            join.left.dependency == Dependency.SHUFFLE &&
            join.right.dependency == Dependency.SHUFFLE &&
            stage.nodes.all { it === join || (it is TransformNode && it.transform.isMapOrFilter) }
        if (!handlesSkew) return null
        val split = SkewSplit(
            join,
            skewPieces(shuffles.getValue(join.left.node), settings),
            skewPieces(shuffles.getValue(join.right.node), settings),
        )
        return split.takeIf { it.splitsAnyPartition }
    }

    private fun partitionMergeOf(stage: Stage, conversions: Map<JoinNode, Side>): PartitionMerge? {
        val numbered = numberedShuffles(stage, conversions)
        if (numbered.isEmpty() || numbered.any { it.forRepartition }) return null
        val sizes = LongArray(numbered.first().shuffle.sizes.size) { partition -> numbered.sumOf { it.shuffle.sizes[partition] } }
        return mergePartitions(sizes, settings.advisorySize, settings.minPartitionSize)
    }

    private fun numberedShuffles(stage: Stage, conversions: Map<JoinNode, Side>): List<NumberedShuffle> = stage.nodes.flatMap { node ->
        when (node) {
            is SourceNode -> emptyList()
            is TransformNode -> listOfNotNull(
                node.input.takeIf { it.dependency == Dependency.SHUFFLE }
                    ?.let { NumberedShuffle(shuffles.getValue(it.node), node.transform is Repartition) },
            )
            is JoinNode -> Side.entries
                .filter { side -> node.input(side).dependency == Dependency.SHUFFLE && conversions[node] != side }
                .map { side -> NumberedShuffle(shuffles.getValue(node.input(side).node), forRepartition = false) }
        }
    }
}
