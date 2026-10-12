enum class Dependency {
    NARROW,
    SHUFFLE,
}

class PlanInput(val node: PlanNode, val dependency: Dependency)

sealed class PlanNode(val partitions: Int, val hashPartitions: Int?) {
    abstract val label: String
}

class SourceNode(val dataset: Dataset) : PlanNode(dataset.partitionCount, null) {
    override val label: String get() = "source(${dataset.name})"
}

class TransformNode(
    val transform: UnaryTransform,
    val input: PlanInput,
    partitions: Int,
    hashPartitions: Int?,
) : PlanNode(partitions, hashPartitions) {
    override val label: String get() = transform.label
}

sealed interface Destination {
    data class Shuffle(val stage: Int, val exchange: Exchange) : Destination

    data object Result : Destination
}

class Stage(val number: Int, val output: PlanNode, val nodes: List<PlanNode>, val destination: Destination)

class Plan(val stages: List<Stage>)
