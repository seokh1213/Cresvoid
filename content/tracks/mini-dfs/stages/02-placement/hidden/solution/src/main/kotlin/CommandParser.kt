private val INTEGER_FORMAT = Regex("0|[1-9][0-9]*")
private val NODE_OR_RACK_NAME_FORMAT = Regex("[A-Za-z][A-Za-z0-9_-]{0,31}")
private val FILE_SIZE_RANGE = 0L..1_000_000L
private val REPLICATION_RANGE = 1L..10L
private val NAME_QUOTA_RANGE = 1L..1_000_000_000_000L
private val SPACE_QUOTA_RANGE = 0L..1_000_000_000_000L
private val CAPACITY_RANGE = 1L..1_000_000_000L
private const val NO_QUOTA = "-"

fun parseCommand(request: String): Command {
    val words = request.split(' ').filter(String::isNotEmpty)
    val arguments = Arguments(words.drop(1))
    return when (words.first()) {
        "MKDIR" -> arguments.read(1..1) { Command.MakeDirectory(path(0)) }
        "CREATE" -> arguments.read(3..4) {
            Command.CreateFile(
                path(0),
                size = number(1, FILE_SIZE_RANGE),
                replication = number(2, REPLICATION_RANGE).toInt(),
                writer = optionalNodeName(3),
            )
        }
        "RM" -> arguments.read(1..1) { Command.Remove(path(0)) }
        "MV" -> arguments.read(2..2) { Command.Move(source = path(0), target = path(1)) }
        "QUOTA" -> arguments.read(3..3) {
            Command.SetQuota(path(0), nameQuota = quota(1, NAME_QUOTA_RANGE), spaceQuota = quota(2, SPACE_QUOTA_RANGE))
        }
        "LS" -> arguments.read(1..1) { Command.ListEntries(path(0)) }
        "COUNT" -> arguments.read(1..1) { Command.Count(path(0)) }
        "BLOCKS" -> arguments.read(1..1) { Command.ShowBlocks(path(0)) }
        "REGISTER" -> arguments.read(3..3) {
            Command.RegisterNode(nodeName(0), rack = rackName(1), capacity = number(2, CAPACITY_RANGE))
        }
        "LOCATE" -> arguments.read(1..1) { Command.Locate(path(0)) }
        "NODE" -> arguments.read(1..1) { Command.ShowNode(nodeName(0)) }
        "FSCK" -> arguments.read(0..0) { Command.Fsck }
        else -> reject(ErrorCode.UNKNOWN_COMMAND)
    }
}

private class Arguments(private val words: List<String>) {
    fun <T> read(counts: IntRange, build: Arguments.() -> T): T {
        if (words.size !in counts) reject(ErrorCode.BAD_ARGUMENT)
        return build()
    }

    fun path(index: Int): DfsPath = DfsPath.parse(words[index]) ?: reject(ErrorCode.BAD_ARGUMENT)

    fun number(index: Int, range: LongRange): Long = parseNumber(words[index], range) ?: reject(ErrorCode.BAD_ARGUMENT)

    fun quota(index: Int, range: LongRange): Long? = if (words[index] == NO_QUOTA) null else number(index, range)

    fun nodeName(index: Int): String = clusterName(index)

    fun optionalNodeName(index: Int): String? = if (index < words.size) nodeName(index) else null

    fun rackName(index: Int): String = clusterName(index)

    private fun clusterName(index: Int): String =
        words[index].takeIf(NODE_OR_RACK_NAME_FORMAT::matches) ?: reject(ErrorCode.BAD_ARGUMENT)
}

private fun parseNumber(word: String, range: LongRange): Long? {
    if (!INTEGER_FORMAT.matches(word)) return null
    return word.toLongOrNull()?.takeIf { it in range }
}
