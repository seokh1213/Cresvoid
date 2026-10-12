private const val ROOT_LETTER = '\u0000'

class TrieNode(val letter: Char, val parent: TrieNode?) {
    private val sortedChildren = ArrayList<TrieNode>(2)

    val children: List<TrieNode> get() = sortedChildren

    var token: String? = null
        private set

    var documentFrequency = 0
        private set

    val isEmpty: Boolean get() = token == null && sortedChildren.isEmpty()

    fun childOrCreate(letter: Char): TrieNode {
        val index = firstChildIndexAtOrAfter(letter)
        sortedChildren.getOrNull(index)?.takeIf { it.letter == letter }?.let { return it }
        return TrieNode(letter, parent = this).also { sortedChildren.add(index, it) }
    }

    fun removeChild(letter: Char) {
        sortedChildren.removeAt(firstChildIndexAtOrAfter(letter))
    }

    fun record(token: String, documentFrequency: Int) {
        this.token = token
        this.documentFrequency = documentFrequency
    }

    fun clear() {
        token = null
        documentFrequency = 0
    }

    private fun firstChildIndexAtOrAfter(letter: Char): Int {
        var low = 0
        var high = sortedChildren.size
        while (low < high) {
            val middle = (low + high) / 2
            if (sortedChildren[middle].letter < letter) low = middle + 1 else high = middle
        }
        return low
    }
}

class TokenTrie {
    val root = TrieNode(ROOT_LETTER, parent = null)
    private val nodesByToken = HashMap<String, TrieNode>()

    fun updateDocumentFrequency(token: String, documentFrequency: Int) {
        if (documentFrequency > 0) {
            nodesByToken.getOrPut(token) { token.fold(root, TrieNode::childOrCreate) }.record(token, documentFrequency)
        } else {
            remove(checkNotNull(nodesByToken.remove(token)))
        }
    }

    private fun remove(tokenNode: TrieNode) {
        tokenNode.clear()
        var node = tokenNode
        while (node.isEmpty) {
            val parent = node.parent ?: return
            parent.removeChild(node.letter)
            node = parent
        }
    }
}
