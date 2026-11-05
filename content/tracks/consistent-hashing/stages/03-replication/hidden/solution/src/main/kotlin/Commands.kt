sealed interface Command {
    data class Hash(val identifier: String) : Command
    data class Put(val key: String) : Command
    data class Owner(val key: String) : Command
    data class Add(val node: String) : Command
    data class Remove(val node: String) : Command
    data class VirtualAdd(val node: String, val capacity: Int) : Command
    data class Weight(val node: String, val capacity: Int) : Command
    data class Replicas(val key: String, val count: Int) : Command
    data class Fail(val node: String, val count: Int) : Command
    data object Load : Command
    data object Invalid : Command
}

private val identifierPattern = Regex("[a-z][a-z0-9_-]{0,23}")
private val separators = Regex("[ \\t]+")

fun parseCommand(line: String): Command? {
    val trimmed = line.trim(' ', '\t')
    if (trimmed.isEmpty()) return null
    val fields = trimmed.split(separators)
    if (fields[0] == "REPLICAS" || fields[0] == "FAIL") return parseReplicationCommand(fields)
    if (fields[0] == "LOAD") return if (fields.size == 1) Command.Load else Command.Invalid
    if (fields[0] == "VADD" || fields[0] == "WEIGHT") return parseCapacityCommand(fields)
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

private fun parseReplicationCommand(fields: List<String>): Command {
    if (fields.size != 3 || !identifierPattern.matches(fields[1])) return Command.Invalid
    val count = parseSmallNumber(fields[2]) ?: return Command.Invalid
    return when (fields[0]) {
        "REPLICAS" -> Command.Replicas(fields[1], count)
        else -> Command.Fail(fields[1], count)
    }
}

private fun parseCapacityCommand(fields: List<String>): Command {
    if (fields.size != 3 || !identifierPattern.matches(fields[1])) return Command.Invalid
    val capacity = parseSmallNumber(fields[2]) ?: return Command.Invalid
    return when (fields[0]) {
        "VADD" -> Command.VirtualAdd(fields[1], capacity)
        else -> Command.Weight(fields[1], capacity)
    }
}

fun parseSmallNumber(text: String): Int? {
    if (text.isEmpty() || text.any { it !in '0'..'9' }) return null
    val value = text.trimStart('0').toIntOrNull() ?: return null
    return value.takeIf { it in 1..8 }
}
