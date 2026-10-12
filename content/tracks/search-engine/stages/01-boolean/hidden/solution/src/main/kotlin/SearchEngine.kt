private const val UNKNOWN_COMMAND = "ERR UNKNOWN_COMMAND"
private const val BAD_ARGUMENT = "ERR BAD_ARGUMENT"
private const val NOT_FOUND = "ERR NOT_FOUND"
private const val SYNTAX_ERROR = "ERR SYNTAX"
private const val NO_MATCHES = "(none)"

class SearchEngine {
    private val index = InvertedIndex()
    private val evaluator = QueryEvaluator(index)

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
            "SEARCH" -> null // 5.2: SEARCH 는 2단계 명령이라 1단계는 이 줄의 출력을 정하지 않는다
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

    private fun splitArguments(rest: String): List<String> = rest.split(' ').filter { it.isNotEmpty() }
}
