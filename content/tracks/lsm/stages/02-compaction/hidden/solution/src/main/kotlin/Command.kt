sealed interface Command {
    data class Reset(val capacity: Int) : Command
    data class Put(val key: String, val value: String) : Command
    data class Delete(val key: String) : Command
    data class Get(val key: String) : Command
    data object Flush : Command
    data object Compact : Command
    data object Metrics : Command
    data object Layout : Command
    data object Invalid : Command
}

private val keyPattern = Regex("[a-z][a-z0-9]{0,15}")
private val valuePattern = Regex("[A-Za-z0-9_]{1,32}")
private val integerPattern = Regex("[0-9]+")
private val separator = Regex("[ \\t]+")

fun parseCommand(line: String): Command? {
    val trimmed = line.trim(' ', '\t')
    if (trimmed.isEmpty()) return null
    val tokens = trimmed.split(separator)
    val arguments = tokens.drop(1)
    return when (tokens.first()) {
        "RESET" -> arguments.singleOrNull()?.let { boundedInteger(it, 1..4096) }
            ?.let(Command::Reset) ?: Command.Invalid
        "PUT" -> if (arguments.size == 2 && validKey(arguments[0]) && valuePattern.matches(arguments[1])) {
            Command.Put(arguments[0], arguments[1])
        } else Command.Invalid
        "DEL" -> arguments.singleOrNull()?.takeIf(::validKey)?.let(Command::Delete) ?: Command.Invalid
        "GET" -> arguments.singleOrNull()?.takeIf(::validKey)?.let(Command::Get) ?: Command.Invalid
        "FLUSH" -> if (arguments.isEmpty()) Command.Flush else Command.Invalid
        "COMPACT" -> if (arguments.isEmpty()) Command.Compact else Command.Invalid
        "METRICS" -> if (arguments.isEmpty()) Command.Metrics else Command.Invalid
        "LAYOUT" -> if (arguments.isEmpty()) Command.Layout else Command.Invalid
        else -> Command.Invalid
    }
}

private fun validKey(token: String): Boolean = keyPattern.matches(token)

private fun boundedInteger(token: String, bounds: IntRange): Int? {
    if (!integerPattern.matches(token)) return null
    // Leading zeros do not constrain the mathematical value's length.
    val significant = token.trimStart('0').ifEmpty { "0" }
    return significant.toIntOrNull()?.takeIf { it in bounds }
}
