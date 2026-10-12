private const val UNKNOWN_COMMAND = "ERR UNKNOWN_COMMAND"
private const val BAD_ARGUMENT = "ERR BAD_ARGUMENT"
private const val NOT_FOUND = "ERR NOT_FOUND"
private const val SYNTAX_ERROR = "ERR SYNTAX"
private const val NO_MATCHES = "(none)"
private const val MAX_RESULT_LIMIT = 1000

private val RESULT_LIMIT_FORMAT = Regex("[1-9][0-9]{0,3}")

private data class TokenRequest(val limit: Int, val token: String)

class SearchEngine {
    private val index = InvertedIndex()
    private val evaluator = QueryEvaluator(index)
    private val ranker = Bm25Ranker(index)
    private val fuzzyMatcher = FuzzyMatcher(index.vocabulary)
    private val suggester = PrefixSuggester(index.vocabulary)

    fun execute(line: String): String? {
        val commandLine = line.trim(' ')
        if (commandLine.isEmpty()) return null
        val name = commandLine.substringBefore(' ')
        val rest = commandLine.substringAfter(' ', missingDelimiterValue = "")
        return when (name) {
            "ADD" -> add(rest)
            "DEL" -> delete(rest)
            "STATS" -> stats(rest)
            "QUERY" -> query(rest)
            "SEARCH" -> search(rest)
            "FUZZY" -> fuzzy(rest)
            "SUGGEST" -> suggest(rest)
            else -> UNKNOWN_COMMAND
        }
    }

    private fun add(rest: String): String? {
        val idAndBody = rest.trimStart(' ')
        val id = DocumentId.parseOrNull(idAndBody.substringBefore(' ')) ?: return BAD_ARGUMENT
        val body = idAndBody.substringAfter(' ', missingDelimiterValue = "").trimStart(' ')
        index.addOrReplace(id, tokenize(body))
        return null
    }

    private fun delete(rest: String): String? {
        val id = splitArguments(rest).singleOrNull()?.let(DocumentId::parseOrNull) ?: return BAD_ARGUMENT
        if (id !in index) return NOT_FOUND
        index.remove(id)
        return null
    }

    private fun stats(rest: String): String {
        if (splitArguments(rest).isNotEmpty()) return BAD_ARGUMENT
        return "${index.documentCount} ${index.distinctTokenCount}"
    }

    private fun query(rest: String): String {
        val expression = parseQuery(rest.trimStart(' ')) ?: return SYNTAX_ERROR
        val matches = evaluator.matchingDocuments(expression)
        if (matches.isEmpty()) return NO_MATCHES
        return matches.sorted().joinToString(" ")
    }

    private fun search(rest: String): String {
        val arguments = splitArguments(rest)
        val limit = arguments.firstOrNull()?.let(::parseResultLimit) ?: return BAD_ARGUMENT
        val ranking = ranker.rank(parseSearchQuery(arguments.drop(1)), limit)
        if (ranking.isEmpty()) return NO_MATCHES
        return ranking.joinToString("\n") { "${it.id} ${it.score}" }
    }

    private fun fuzzy(rest: String): String {
        val request = parseTokenRequest(rest) ?: return BAD_ARGUMENT
        val matches = fuzzyMatcher.closestTokens(request.token, request.limit)
        if (matches.isEmpty()) return NO_MATCHES
        return matches.joinToString(" ") { "${it.token}:${it.distance}:${it.documentFrequency}" }
    }

    private fun suggest(rest: String): String {
        val request = parseTokenRequest(rest) ?: return BAD_ARGUMENT
        val suggestions = suggester.suggestions(request.token, request.limit)
        if (suggestions.isEmpty()) return NO_MATCHES
        return suggestions.joinToString(" ") { "${it.token}:${it.documentFrequency}" }
    }

    private fun parseTokenRequest(rest: String): TokenRequest? {
        val arguments = splitArguments(rest)
        if (arguments.size != 2) return null
        val limit = parseResultLimit(arguments[0]) ?: return null
        val token = tokenize(arguments[1]).singleOrNull() ?: return null
        return TokenRequest(limit, token)
    }

    private fun splitArguments(rest: String): List<String> = rest.split(' ').filter { it.isNotEmpty() }

    private fun parseResultLimit(text: String): Int? =
        if (RESULT_LIMIT_FORMAT.matches(text)) text.toInt().takeIf { it <= MAX_RESULT_LIMIT } else null
}
