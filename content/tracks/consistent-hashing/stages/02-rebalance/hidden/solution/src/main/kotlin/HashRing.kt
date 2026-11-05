fun hashPosition(identifier: String): Int {
    var hash = 14695981039346656037uL
    for (byte in identifier.toByteArray(Charsets.UTF_8)) {
        hash = (hash xor (byte.toInt() and 255).toULong()) * 1099511628211uL
    }
    return (hash and 65535uL).toInt()
}

data class Token(val position: Int, val node: String, val index: Int)

class HashRing(tokenCounts: Map<String, Int>) {
    val nodes: List<String> = tokenCounts.keys.sorted()
    private val tokens = tokenCounts.flatMap { (node, count) ->
        (0 until count).map { index ->
            val name = if (index == 0) node else "$node#$index"
            Token(hashPosition(name), node, index)
        }
    }.sortedWith(compareBy<Token> { it.position }.thenBy { it.node }.thenBy { it.index })

    fun owner(position: Int): String? {
        if (tokens.isEmpty()) return null
        return tokens[firstTokenIndex(position)].node
    }

    fun moduloOwner(position: Int): String? =
        if (nodes.isEmpty()) null else nodes[position % nodes.size]

    private fun firstTokenIndex(position: Int): Int {
        var lower = 0
        var upper = tokens.size
        while (lower < upper) {
            val middle = (lower + upper) / 2
            if (tokens[middle].position < position) lower = middle + 1 else upper = middle
        }
        return if (lower == tokens.size) 0 else lower
    }
}
