sealed interface Command {
    data class Hash(val identifier: String) : Command
    data class Put(val key: String) : Command
    data class Owner(val key: String) : Command
    data class Add(val node: String) : Command
    data class Remove(val node: String) : Command
    data object Invalid : Command
}

private val identifierPattern = Regex("[a-z][a-z0-9_-]{0,23}")
private val separators = Regex("[ \\t]+")

fun parseCommand(line: String): Command? {
    val trimmed = line.trim(' ', '\t')
    if (trimmed.isEmpty()) return null
    val fields = trimmed.split(separators)
    if (fields.size != 2 || !identifierPattern.matches(fields[1])) return Command.Invalid
    val identifier = fields[1]
    return when (fields[0]) {
        "HASH" -> Command.Hash(identifier)
        "PUT" -> Command.Put(identifier)
        "OWNER" -> Command.Owner(identifier)
        "ADD" -> Command.Add(identifier)
        "REMOVE" -> Command.Remove(identifier)
        else -> Command.Invalid
    }
}
