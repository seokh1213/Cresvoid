class InvertedIndex {
    private val documentsById = HashMap<DocumentId, IndexedDocument>()
    private val postingsByToken = HashMap<String, MutableSet<DocumentId>>()
    private var totalLength = 0L

    val vocabulary = TokenTrie()

    val documentIds: Set<DocumentId> get() = documentsById.keys

    val documentCount: Int get() = documentsById.size

    val distinctTokenCount: Int get() = postingsByToken.size

    val averageDocumentLength: Double get() = totalLength.toDouble() / documentCount

    operator fun contains(id: DocumentId): Boolean = id in documentsById

    fun postings(token: String): Set<DocumentId> = postingsByToken[token].orEmpty()

    fun documentFrequency(token: String): Int = postings(token).size

    fun document(id: DocumentId): IndexedDocument = documentsById.getValue(id)

    fun termFrequency(id: DocumentId, token: String): Int = document(id).termFrequency(token)

    fun documentLength(id: DocumentId): Int = document(id).length

    fun addOrReplace(id: DocumentId, tokens: List<String>) {
        remove(id)
        val document = IndexedDocument(tokens)
        documentsById[id] = document
        totalLength += document.length
        for (token in document.distinctTokens) {
            val postings = postingsByToken.getOrPut(token) { HashSet() }
            postings.add(id)
            vocabulary.updateDocumentFrequency(token, postings.size)
        }
    }

    fun remove(id: DocumentId) {
        val document = documentsById.remove(id) ?: return
        totalLength -= document.length
        for (token in document.distinctTokens) {
            val postings = postingsByToken.getValue(token)
            postings.remove(id)
            if (postings.isEmpty()) postingsByToken.remove(token)
            vocabulary.updateDocumentFrequency(token, postings.size)
        }
    }
}
