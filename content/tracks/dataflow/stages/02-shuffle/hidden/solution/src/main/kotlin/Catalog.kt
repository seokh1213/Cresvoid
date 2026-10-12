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
        if (definition.source.name !in this) fail(ErrorCode.NOT_FOUND, definition.source.name)
        val kind = outputKindOf(definition.transforms)
        pipelines[definition.name] = Pipeline(definition.name, definition.transforms, kind)
    }

    private fun outputKindOf(transforms: List<Transform>): ValueKind {
        val source = transforms.first() as Source
        return transforms.withIndex().drop(1).fold(kindOf(source.name)) { input, (index, transform) ->
            (transform as? UnaryTransform)?.outputKind(input) ?: fail(ErrorCode.TYPE, index + 1)
        }
    }

    private fun kindOf(name: String): ValueKind = pipelines[name]?.kind ?: ValueKind.NUMBER
}
