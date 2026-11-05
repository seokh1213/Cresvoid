sealed interface Command {
    data class Add(val orderId: String, val failures: Int) : Command
    data class Show(val orderId: String) : Command
    data class Run(val pageSize: Int, val maxPages: Int) : Command
    data object Queue : Command
    data object Invalid : Command
}

private val orderIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,31}")
private val integerPattern = Regex("[0-9]+")
private val wordBoundary = Regex("\\s+")

fun parseCommand(line: String): Command {
    val words = line.trim().split(wordBoundary)
    return when (words.first()) {
        "ADD" -> parseAdd(words)
        "SHOW" -> parseShow(words)
        "RUN" -> parseRun(words)
        "QUEUE" -> if (words.size == 1) Command.Queue else Command.Invalid
        else -> Command.Invalid
    }
}

private fun parseAdd(words: List<String>): Command {
    if (words.size != 3 || !orderIdPattern.matches(words[1])) {
        return Command.Invalid
    }
    val failures = parseInteger(words[2], 0..1000) ?: return Command.Invalid
    return Command.Add(words[1], failures)
}

private fun parseShow(words: List<String>): Command {
    if (words.size != 2 || !orderIdPattern.matches(words[1])) {
        return Command.Invalid
    }
    return Command.Show(words[1])
}

private fun parseRun(words: List<String>): Command {
    if (words.size != 3) return Command.Invalid
    val pageSize = parseInteger(words[1], 1..1000) ?: return Command.Invalid
    val maxPages = parseInteger(words[2], 1..1000) ?: return Command.Invalid
    return Command.Run(pageSize, maxPages)
}

private fun parseInteger(word: String, range: IntRange): Int? {
    if (!integerPattern.matches(word)) return null
    val value = word.trimStart('0').ifEmpty { "0" }.toIntOrNull() ?: return null
    return value.takeIf { it in range }
}
