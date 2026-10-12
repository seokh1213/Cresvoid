class Pipeline(val name: String, val transforms: List<Transform>, val kind: ValueKind)

class Catalog {
    private val datasets = HashMap<String, Dataset>()
    private val pipelines = HashMap<String, Pipeline>()

    operator fun contains(name: String): Boolean = name in datasets || name in pipelines

    fun dataset(name: String): Dataset? = datasets[name]

    fun pipeline(name: String): Pipeline? = pipelines[name]

    fun addDataset(dataset: Dataset) {
        datasets[dataset.name] = dataset
    }

    fun definePipeline(definition: PipeDefinition) {
        if (definition.name in this) fail(ErrorCode.EXISTS)
        definition.referencedNames().firstOrNull { it !in this }?.let { fail(ErrorCode.NOT_FOUND, it) }
        val kind = outputKindOf(definition.transforms)
        pipelines[definition.name] = Pipeline(definition.name, definition.transforms, kind)
    }

    private fun outputKindOf(transforms: List<Transform>): ValueKind {
        val source = transforms.first() as Source
        return transforms.withIndex().drop(1).fold(kindOf(source.name)) { input, (index, transform) ->
            outputKindOf(transform, input) ?: fail(ErrorCode.TYPE, index + 1)
        }
    }

    private fun outputKindOf(transform: Transform, input: ValueKind): ValueKind? = when (transform) {
        is UnaryTransform -> transform.outputKind(input)
        is Join -> ValueKind.PAIR.takeIf { input == ValueKind.NUMBER && kindOf(transform.name) == ValueKind.NUMBER }
        is Source -> null
    }

    private fun kindOf(name: String): ValueKind = pipelines[name]?.kind ?: ValueKind.NUMBER
}
