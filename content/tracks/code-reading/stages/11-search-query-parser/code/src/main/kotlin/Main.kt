fun main() {
    System.`in`.bufferedReader().use { input ->
        input.lineSequence().forEach { line ->
            println(execute(line))
        }
    }
}

private fun execute(line: String): String {
    val command = line.trim().split(Regex("\\s+"), limit = 2)
    if (command.first() != "PARSE") return "ERROR command"
    val source = command.getOrNull(1) ?: ""
    return try {
        val tokens = QueryTokenizer(source).tokenize()
        val query = QueryParser(tokens).parse()
        QueryOutput.render(query)
    } catch (failure: QuerySyntaxException) {
        "ERROR ${failure.reason}@${failure.offset}"
    }
}
