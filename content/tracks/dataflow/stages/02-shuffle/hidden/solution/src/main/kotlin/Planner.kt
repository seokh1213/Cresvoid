class Planner(private val catalog: Catalog) {
    fun plan(name: String): Plan = Plan(stagesOf(lineageOf(name)))

    private fun lineageOf(name: String): PlanNode {
        catalog.dataset(name)?.let { return SourceNode(it) }
        val transforms = catalog.pipeline(name)?.transforms ?: error("$name is neither a dataset nor a pipeline")
        val source = transforms.first() as Source
        return transforms.drop(1).fold(lineageOf(source.name)) { input, transform ->
            when (transform) {
                is UnaryTransform -> transformNode(transform, input)
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

    private fun keyedInput(input: PlanNode, partitions: Int): PlanInput =
        PlanInput(input, if (input.hashPartitions == partitions) Dependency.NARROW else Dependency.SHUFFLE)

    private fun narrow(input: PlanNode): PlanInput = PlanInput(input, Dependency.NARROW)

    private fun stagesOf(output: PlanNode): List<Stage> {
        val lineage = generateSequence(output) { (it as? TransformNode)?.input?.node }.toList().asReversed()
        val stageNodes = mutableListOf<MutableList<PlanNode>>()
        for (node in lineage) {
            if (node !is TransformNode || node.input.dependency == Dependency.SHUFFLE) stageNodes += mutableListOf<PlanNode>()
            stageNodes.last() += node
        }
        return stageNodes.mapIndexed { number, nodes ->
            val reader = stageNodes.getOrNull(number + 1)?.first()
            val destination = if (reader == null) Destination.Result else Destination.Shuffle(number + 1, exchangeOf(reader))
            Stage(number, nodes.last(), nodes, destination)
        }
    }

    private fun exchangeOf(reader: PlanNode): Exchange = when (val transform = (reader as TransformNode).transform) {
        is ReduceByKey -> HashExchange(transform.partitions, transform.reducer)
        is GroupByKey -> HashExchange(transform.partitions, combiner = null)
        is Repartition -> RoundRobinExchange(transform.partitions)
        else -> error("$transform reads its input narrowly")
    }
}
