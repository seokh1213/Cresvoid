enum class Dependency {
    NARROW,
    SHUFFLE,
    BROADCAST,
}

enum class Side {
    LEFT,
    RIGHT;

    val opposite: Side get() = if (this == LEFT) RIGHT else LEFT
}

sealed interface JoinStrategy {
    val label: String

    data object SortMerge : JoinStrategy {
        override val label: String get() = "join(sortmerge)"
    }

    data class Broadcast(val buildSide: Side) : JoinStrategy {
        override val label: String get() = "join(broadcast-${buildSide.name.lowercase()})"
    }
}

class PlanInput(val node: PlanNode, val dependency: Dependency)

sealed class PlanNode(val partitions: Int, val hashPartitions: Int?, val estimatedBytes: Long) {
    abstract val inputs: List<PlanInput>
    abstract val label: String
}

class SourceNode(val dataset: Dataset) : PlanNode(dataset.partitionCount, null, dataset.bytes) {
    override val inputs: List<PlanInput> get() = emptyList()
    override val label: String get() = "source(${dataset.name})"
}

class TransformNode(
    val transform: UnaryTransform,
    val input: PlanInput,
    partitions: Int,
    hashPartitions: Int?,
) : PlanNode(partitions, hashPartitions, input.node.estimatedBytes) {
    override val inputs: List<PlanInput> get() = listOf(input)
    override val label: String get() = transform.label
}

class JoinNode(
    val join: Join,
    val strategy: JoinStrategy,
    val left: PlanInput,
    val right: PlanInput,
    partitions: Int,
    hashPartitions: Int?,
) : PlanNode(partitions, hashPartitions, left.node.estimatedBytes + right.node.estimatedBytes) {
    override val inputs: List<PlanInput> get() = listOf(left, right)
    override val label: String get() = strategy.label

    fun input(side: Side): PlanInput = if (side == Side.LEFT) left else right
}

sealed interface Destination {
    data class Shuffle(val stage: Int, val exchange: Exchange) : Destination

    data class Broadcast(val stage: Int) : Destination

    data object Result : Destination
}

class Stage(val number: Int, val output: PlanNode, val nodes: List<PlanNode>, val destination: Destination)

class Plan(val stages: List<Stage>)
