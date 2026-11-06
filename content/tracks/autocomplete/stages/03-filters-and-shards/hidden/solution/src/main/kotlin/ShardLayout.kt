class ShardLayout {
    private val groupSizes = HashMap<Char, Int>()
    private val assignments = HashMap<Char, Int>()
    private var loads = IntArray(1)

    fun register(term: String) {
        val letter = term.first()
        groupSizes[letter] = (groupSizes[letter] ?: 0) + 1
        val shard = assignments.getOrPut(letter) { lightest() }
        loads[shard]++
    }

    fun repartition(count: Int) {
        loads = IntArray(count)
        assignments.clear()
        val groups = groupSizes.entries.sortedWith(
            compareByDescending<Map.Entry<Char, Int>> { it.value }.thenBy { it.key },
        )
        for (group in groups) {
            val shard = lightest()
            assignments[group.key] = shard
            loads[shard] += group.value
        }
    }

    fun route(prefix: String): String = if (prefix.isEmpty()) {
        loads.indices.joinToString(" ")
    } else {
        assignments[prefix.first()]?.toString() ?: "(none)"
    }

    fun render(): String = loads.indices.joinToString(" ") { "$it:${loads[it]}" }

    private fun lightest(): Int = loads.indices.minBy { loads[it] }
}
