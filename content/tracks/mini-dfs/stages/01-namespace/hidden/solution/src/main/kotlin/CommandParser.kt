private val INTEGER_FORMAT = Regex("0|[1-9][0-9]*")
private val FILE_SIZE_RANGE = 0L..1_000_000L
private val REPLICATION_RANGE = 1L..10L
private val NAME_QUOTA_RANGE = 1L..1_000_000_000_000L
private val SPACE_QUOTA_RANGE = 0L..1_000_000_000_000L
private const val NO_QUOTA = "-"

fun parseCommand(request: String): Command {
    val words = request.split(' ').filter(String::isNotEmpty)
    val arguments = Arguments(words.drop(1))
    return when (words.first()) {
        "MKDIR" -> arguments.read(1) { Command.MakeDirectory(path(0)) }
        "CREATE" -> arguments.read(3) {
            Command.CreateFile(path(0), size = number(1, FILE_SIZE_RANGE), replication = number(2, REPLICATION_RANGE).toInt())
        }
        "RM" -> arguments.read(1) { Command.Remove(path(0)) }
        "MV" -> arguments.read(2) { Command.Move(source = path(0), target = path(1)) }
        "QUOTA" -> arguments.read(3) {
            Command.SetQuota(path(0), nameQuota = quota(1, NAME_QUOTA_RANGE), spaceQuota = quota(2, SPACE_QUOTA_RANGE))
        }
        "LS" -> arguments.read(1) { Command.ListEntries(path(0)) }
        "COUNT" -> arguments.read(1) { Command.Count(path(0)) }
        "BLOCKS" -> arguments.read(1) { Command.ShowBlocks(path(0)) }
        else -> reject(ErrorCode.UNKNOWN_COMMAND)
    }
}

private class Arguments(private val words: List<String>) {
    fun <T> read(count: Int, build: Arguments.() -> T): T {
        if (words.size != count) reject(ErrorCode.BAD_ARGUMENT)
        return build()
    }

    fun path(index: Int): DfsPath = DfsPath.parse(words[index]) ?: reject(ErrorCode.BAD_ARGUMENT)

    fun number(index: Int, range: LongRange): Long = parseNumber(words[index], range) ?: reject(ErrorCode.BAD_ARGUMENT)

    fun quota(index: Int, range: LongRange): Long? = if (words[index] == NO_QUOTA) null else number(index, range)
}

private fun parseNumber(word: String, range: LongRange): Long? {
    if (!INTEGER_FORMAT.matches(word)) return null
    return word.toLongOrNull()?.takeIf { it in range }
}
