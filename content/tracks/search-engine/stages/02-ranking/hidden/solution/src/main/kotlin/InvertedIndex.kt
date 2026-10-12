class InvertedIndex {
    private val documentsById = HashMap<DocumentId, IndexedDocument>()
    private val postingsByToken = HashMap<String, MutableSet<DocumentId>>()
    private var totalLength = 0L

    val documentIds: Set<DocumentId> get() = documentsById.keys

    val documentCount: Int get() = documentsById.size

    val distinctTokenCount: Int get() = postingsByToken.size

    val averageDocumentLength: Double get() = totalLength.toDouble() / documentCount

    operator fun contains(id: DocumentId): Boolean = id in documentsById

    fun postings(token: String): Set<DocumentId> = postingsByToken[token].orEmpty()

    fun documentFrequency(token: String): Int = postings(token).size

    fun termFrequency(id: DocumentId, token: String): Int = documentsById.getValue(id).termFrequencies[token] ?: 0

    fun documentLength(id: DocumentId): Int = documentsById.getValue(id).length

    fun addOrReplace(id: DocumentId, tokens: List<String>) {
        remove(id)
        val document = IndexedDocument(termFrequencies = tokens.groupingBy { it }.eachCount(), length = tokens.size)
        documentsById[id] = document
        totalLength += document.length
        for (token in document.termFrequencies.keys) {
            postingsByToken.getOrPut(token) { HashSet() }.add(id)
        }
    }

    fun remove(id: DocumentId) {
        val document = documentsById.remove(id) ?: return
        totalLength -= document.length
        for (token in document.termFrequencies.keys) {
            val postings = postingsByToken.getValue(token)
            postings.remove(id)
            if (postings.isEmpty()) postingsByToken.remove(token)
        }
    }
}

private class IndexedDocument(val termFrequencies: Map<String, Int>, val length: Int)
