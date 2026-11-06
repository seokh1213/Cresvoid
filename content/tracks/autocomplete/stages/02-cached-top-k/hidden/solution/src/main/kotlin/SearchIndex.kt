import java.util.PriorityQueue

class SearchTerm(val text: String, var frequency: Long) {
    fun snapshot() = RankedTerm(text, frequency)
}

data class RankedTerm(val text: String, val frequency: Long)

val ranking: Comparator<RankedTerm> = compareByDescending<RankedTerm> { it.frequency }.thenBy { it.text }

fun render(terms: List<RankedTerm>): String =
    if (terms.isEmpty()) "(none)" else terms.joinToString(" ") { "${it.text}:${it.frequency}" }

class TrieNode {
    private var branches: MutableMap<Char, TrieNode>? = null
    val children: Collection<TrieNode> get() = branches?.values ?: emptyList()
    var terminal: SearchTerm? = null
    var best: SearchTerm? = null
    var cached: List<RankedTerm>? = null

    fun child(letter: Char): TrieNode? = branches?.get(letter)

    fun createChild(letter: Char): TrieNode {
        val existing = branches ?: HashMap<Char, TrieNode>().also { branches = it }
        return existing.getOrPut(letter) { TrieNode() }
    }
}

class SearchIndex {
    val root = TrieNode()
    var rebuildWork = 0L
        private set

    fun add(term: String, amount: Long): List<TrieNode>? {
        val previous = find(term)?.terminal
        if (previous != null && previous.frequency > Long.MAX_VALUE - amount) return null
        val path = ArrayList<TrieNode>(term.length + 1)
        var node = root
        path.add(node)
        for (letter in term) {
            node = node.createChild(letter)
            path.add(node)
        }
        val entry = previous ?: SearchTerm(term, 0).also {
            node.terminal = it
            rebuildWork += term.length + 1
        }
        entry.frequency += amount
        for (ancestor in path) {
            if (better(entry, ancestor.best)) ancestor.best = entry
        }
        return path
    }

    fun find(prefix: String): TrieNode? {
        var node = root
        for (letter in prefix) node = node.child(letter) ?: return null
        return node
    }

    fun query(count: Int, prefix: String): List<RankedTerm> =
        select(count, find(prefix), { it.best }, { true })

    private sealed interface Candidate {
        val best: SearchTerm
        data class Branch(val node: TrieNode, override val best: SearchTerm) : Candidate
        data class Word(override val best: SearchTerm) : Candidate
    }

    private fun select(
        count: Int,
        node: TrieNode?,
        bestOf: (TrieNode) -> SearchTerm?,
        permitted: (SearchTerm) -> Boolean,
    ): List<RankedTerm> {
        val pending = PriorityQueue<Candidate> { left, right -> compare(left.best, right.best) }
        val first = node?.let(bestOf) ?: return emptyList()
        pending.add(Candidate.Branch(node, first))
        val result = ArrayList<RankedTerm>(count)
        while (pending.isNotEmpty() && result.size < count) {
            when (val candidate = pending.remove()) {
                is Candidate.Word -> result.add(candidate.best.snapshot())
                is Candidate.Branch -> {
                    candidate.node.terminal?.takeIf(permitted)?.let { pending.add(Candidate.Word(it)) }
                    for (child in candidate.node.children) {
                        bestOf(child)?.let { pending.add(Candidate.Branch(child, it)) }
                    }
                }
            }
        }
        return result
    }
}

fun compare(left: SearchTerm, right: SearchTerm): Int {
    val frequencyOrder = right.frequency.compareTo(left.frequency)
    return if (frequencyOrder != 0) frequencyOrder else left.text.compareTo(right.text)
}

fun better(candidate: SearchTerm, current: SearchTerm?): Boolean =
    current == null || compare(candidate, current) < 0
