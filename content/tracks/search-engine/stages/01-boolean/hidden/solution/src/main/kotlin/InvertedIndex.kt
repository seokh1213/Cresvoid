class InvertedIndex {
    private val tokensByDocument = HashMap<DocumentId, Set<String>>()
    private val postingsByToken = HashMap<String, MutableSet<DocumentId>>()

    val documentIds: Set<DocumentId> get() = tokensByDocument.keys

    val documentCount: Int get() = tokensByDocument.size

    val distinctTokenCount: Int get() = postingsByToken.size

    operator fun contains(id: DocumentId): Boolean = id in tokensByDocument

    fun postings(token: String): Set<DocumentId> = postingsByToken[token].orEmpty()

    fun addOrReplace(id: DocumentId, tokens: List<String>) {
        remove(id)
        val distinctTokens = tokens.toSet()
        tokensByDocument[id] = distinctTokens
        for (token in distinctTokens) {
            postingsByToken.getOrPut(token) { HashSet() }.add(id)
        }
    }

    fun remove(id: DocumentId) {
        val tokens = tokensByDocument.remove(id) ?: return
        for (token in tokens) {
            val postings = postingsByToken.getValue(token)
            postings.remove(id)
            if (postings.isEmpty()) postingsByToken.remove(token)
        }
    }
}
