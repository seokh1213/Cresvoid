class Shard(override val number: Int) : ShardReader {
    private val documentsById = HashMap<Int, Document>()
    private val postings = HashMap<String, MutableSet<Document>>()
    private var nextInternalNumber = 0

    val brandCounts = BrandCounts()

    override var tokenCount = 0L
        private set

    override val documentCount: Int get() = documentsById.size

    override val documents: Collection<Document> get() = documentsById.values

    fun put(id: Int, brand: String, price: Int, titleTokens: List<String>): Document? {
        val replaced = documentsById[id]?.also(::unindex)
        val document = Document(id, brand, price, nextInternalNumber++, titleTokens)
        documentsById[id] = document
        tokenCount += document.length
        brandCounts.increment(brand)
        for (token in document.termFrequencies.keys) {
            postings.getOrPut(token) { HashSet() }.add(document)
        }
        return replaced
    }

    fun delete(id: Int): Document? = documentsById[id]?.also(::unindex)

    fun freeze(): FrozenShard = FrozenShard(number, documentsById.values.toList(), tokenCount)

    override fun documentsContaining(token: String): Collection<Document> = postings[token].orEmpty()

    private fun unindex(document: Document) {
        documentsById.remove(document.id)
        tokenCount -= document.length
        brandCounts.decrement(document.brand)
        for (token in document.termFrequencies.keys) {
            val holders = postings.getValue(token)
            holders.remove(document)
            if (holders.isEmpty()) postings.remove(token)
        }
    }
}
