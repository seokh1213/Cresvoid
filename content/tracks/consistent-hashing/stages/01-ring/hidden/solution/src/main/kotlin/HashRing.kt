fun hashPosition(identifier: String): Int {
    var hash = 14695981039346656037uL
    for (byte in identifier.toByteArray(Charsets.UTF_8)) {
        hash = (hash xor (byte.toInt() and 255).toULong()) * 1099511628211uL
    }
    return (hash and 65535uL).toInt()
}

data class Token(val position: Int, val node: String)

class HashRing(nodeNames: Collection<String>) {
    val nodes: List<String> = nodeNames.sorted()
    private val tokens = nodes.map { Token(hashPosition(it), it) }
        .sortedWith(compareBy<Token> { it.position }.thenBy { it.node })

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
