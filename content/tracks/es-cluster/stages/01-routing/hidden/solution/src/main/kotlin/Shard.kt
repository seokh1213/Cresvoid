class Shard(val number: Int) {
    private val documentsById = HashMap<Int, Document>()
    private val postings = HashMap<String, MutableSet<Document>>()
    private var nextInternalNumber = 0

    var tokenCount = 0L
        private set

    val documentCount: Int get() = documentsById.size

    val documents: Collection<Document> get() = documentsById.values

    fun put(id: Int, brand: String, price: Int, titleTokens: List<String>) {
        documentsById[id]?.let(::unindex)
        val document = Document(id, brand, price, nextInternalNumber++, titleTokens)
        documentsById[id] = document
        tokenCount += document.length
        for (token in document.termFrequencies.keys) {
            postings.getOrPut(token) { HashSet() }.add(document)
        }
    }

    fun delete(id: Int): Boolean {
        val document = documentsById[id] ?: return false
        unindex(document)
        return true
    }

    fun documentFrequency(token: String): Int = postings[token]?.size ?: 0

    fun documentsContainingAny(tokens: List<String>): List<Document> =
        tokens.flatMapIndexed { position, token ->
            val earlierTokens = tokens.subList(0, position)
            postings[token].orEmpty().filter { document -> earlierTokens.none(document.termFrequencies::containsKey) }
        }

    private fun unindex(document: Document) {
        documentsById.remove(document.id)
        tokenCount -= document.length
        for (token in document.termFrequencies.keys) {
            val holders = postings.getValue(token)
            holders.remove(document)
            if (holders.isEmpty()) postings.remove(token)
        }
    }
}
