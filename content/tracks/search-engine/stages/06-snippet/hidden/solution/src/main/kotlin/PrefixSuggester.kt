import java.util.PriorityQueue

data class Suggestion(val token: String, val documentFrequency: Int)

private sealed interface Candidate {
    val rankingNode: TrieNode

    data class Token(val node: TrieNode) : Candidate {
        override val rankingNode: TrieNode get() = node
    }

    data class Subtree(val node: TrieNode) : Candidate {
        override val rankingNode: TrieNode get() = checkNotNull(node.topRanked)
    }
}

private val BY_RANKING_NODE = compareBy(BY_FREQUENCY_THEN_TOKEN, Candidate::rankingNode)

class PrefixSuggester(private val vocabulary: TokenTrie) {

    fun suggestions(prefix: String, limit: Int): List<Suggestion> {
        val prefixNode = vocabulary.nodeFor(prefix) ?: return emptyList()
        val frontier = PriorityQueue(BY_RANKING_NODE)
        frontier.add(Candidate.Subtree(prefixNode))
        val suggestions = mutableListOf<Suggestion>()
        while (suggestions.size < limit) {
            when (val candidate = frontier.poll() ?: break) {
                is Candidate.Token -> suggestions += Suggestion(checkNotNull(candidate.node.token), candidate.node.documentFrequency)
                is Candidate.Subtree -> {
                    if (candidate.node.token != null) frontier.add(Candidate.Token(candidate.node))
                    candidate.node.children.mapTo(frontier, Candidate::Subtree)
                }
            }
        }
        return suggestions
    }
}
