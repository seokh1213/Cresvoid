class FrozenShard(
    override val number: Int,
    override val documents: List<Document>,
    override val tokenCount: Long,
) : ShardReader {
    private val postings = HashMap<String, List<Document>>()

    override val documentCount: Int get() = documents.size

    override fun documentsContaining(token: String): Collection<Document> =
        postings.getOrPut(token) { documents.filter { token in it.termFrequencies } }
}
