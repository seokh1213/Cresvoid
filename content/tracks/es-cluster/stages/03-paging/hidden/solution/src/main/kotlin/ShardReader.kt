interface ShardReader {
    val number: Int
    val documentCount: Int
    val tokenCount: Long
    val documents: Collection<Document>

    fun documentsContaining(token: String): Collection<Document>

    fun documentFrequency(token: String): Int = documentsContaining(token).size

    fun documentsContainingAny(tokens: List<String>): List<Document> =
        tokens.flatMapIndexed { position, token ->
            val earlierTokens = tokens.subList(0, position)
            documentsContaining(token).filter { document -> earlierTokens.none(document.termFrequencies::containsKey) }
        }
}
