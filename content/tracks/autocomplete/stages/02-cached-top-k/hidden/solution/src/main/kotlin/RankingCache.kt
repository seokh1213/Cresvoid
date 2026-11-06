enum class CacheMode { IMMEDIATE, BATCH }

class RankingCache(
    private val index: SearchIndex,
    val mode: CacheMode,
    val capacity: Int,
) {
    private var incremental = 0L
    private var rebuilt = 0L

    init {
        refresh()
    }

    fun afterAdd(term: String, path: List<TrieNode>) {
        if (mode == CacheMode.BATCH) return
        val snapshot = path.last().terminal?.snapshot() ?: error("Missing registered term")
        for (node in path) {
            val candidates = node.cached.orEmpty().filter { it.text != term } + snapshot
            node.cached = candidates.sortedWith(ranking).take(capacity)
        }
        incremental += term.length + 1
    }

    fun refresh() {
        rebuild(index.root)
        rebuilt += index.rebuildWork
    }

    fun query(count: Int, prefix: String): String =
        if (count > capacity) "ERR" else render(index.find(prefix)?.cached.orEmpty().take(count))

    fun cost(): String = "incremental=$incremental rebuilt=$rebuilt"

    private fun rebuild(node: TrieNode): List<RankedTerm> {
        if (node.children.isEmpty()) {
            val result = node.terminal?.let { listOf(it.snapshot()) }.orEmpty()
            node.cached = result
            return result
        }
        val candidates = ArrayList<RankedTerm>()
        node.terminal?.let { candidates.add(it.snapshot()) }
        for (child in node.children) candidates.addAll(rebuild(child))
        val result = candidates.sortedWith(ranking).take(capacity)
        node.cached = result
        return result
    }
}
