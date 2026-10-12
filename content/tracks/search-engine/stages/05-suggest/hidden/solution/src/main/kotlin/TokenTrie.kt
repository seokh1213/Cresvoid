private const val ROOT_LETTER = '\u0000'

val BY_FREQUENCY_THEN_TOKEN = Comparator<TrieNode> { first, second ->
    if (first.documentFrequency != second.documentFrequency) {
        second.documentFrequency.compareTo(first.documentFrequency)
    } else {
        checkNotNull(first.token).compareTo(checkNotNull(second.token))
    }
}

class TrieNode(val letter: Char, val parent: TrieNode?) {
    private val sortedChildren = ArrayList<TrieNode>(2)

    val children: List<TrieNode> get() = sortedChildren

    var token: String? = null
        private set

    var documentFrequency = 0
        private set

    var topRanked: TrieNode? = null
        private set

    val isEmpty: Boolean get() = token == null && sortedChildren.isEmpty()

    fun child(letter: Char): TrieNode? =
        sortedChildren.getOrNull(firstChildIndexAtOrAfter(letter))?.takeIf { it.letter == letter }

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

    // 바뀐 토큰이 이 노드의 최상위 후보였던 적도 새로 된 적도 없으면 위쪽 노드의 최상위 후보도 그대로다
    fun updateTopRanked(changed: TrieNode, rankRaised: Boolean): Boolean {
        val current = topRanked
        if (rankRaised) {
            if (current === changed) return true
            if (current != null && BY_FREQUENCY_THEN_TOKEN.compare(changed, current) > 0) return false
            topRanked = changed
            return true
        }
        if (current !== changed) return false
        topRanked = bestOfOwnTokenAndChildren()
        return true
    }

    private fun bestOfOwnTokenAndChildren(): TrieNode? {
        var best: TrieNode? = if (token != null) this else null
        for (child in sortedChildren) {
            val candidate = checkNotNull(child.topRanked)
            if (best == null || BY_FREQUENCY_THEN_TOKEN.compare(candidate, best) < 0) best = candidate
        }
        return best
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

    fun nodeFor(prefix: String): TrieNode? = prefix.fold<TrieNode?>(root) { node, letter -> node?.child(letter) }

    fun updateDocumentFrequency(token: String, documentFrequency: Int) {
        val tokenNode = if (documentFrequency > 0) {
            nodesByToken.getOrPut(token) { token.fold(root, TrieNode::childOrCreate) }
        } else {
            checkNotNull(nodesByToken.remove(token))
        }
        val rankRaised = documentFrequency > tokenNode.documentFrequency
        if (documentFrequency > 0) tokenNode.record(token, documentFrequency) else tokenNode.clear()
        var node = tokenNode
        // 접두는 한 글자 이상이라 루트의 최상위 후보는 쓰지 않는다
        while (node !== root) {
            val parent = checkNotNull(node.parent)
            if (node.isEmpty) {
                parent.removeChild(node.letter)
            } else {
                val parentMayChange = node.updateTopRanked(tokenNode, rankRaised)
                if (!parentMayChange) return
            }
            node = parent
        }
    }
}
