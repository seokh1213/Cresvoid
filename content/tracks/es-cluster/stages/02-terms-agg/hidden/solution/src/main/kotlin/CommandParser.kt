private val SHARD_COUNT_FORMAT = Regex("[1-9][0-9]?")
private val DOCUMENT_ID_FORMAT = Regex("[1-9][0-9]{0,8}")
private val KEYWORD_FORMAT = Regex("[a-z0-9]{1,32}")
private val PRICE_FORMAT = Regex("0|[1-9][0-9]{0,8}")
private const val MAX_SHARD_COUNT = 64
private const val ROUTE_BY_ID = "-"

fun parseCommand(name: CommandName, line: RequestLine): Command =
    when (name) {
        CommandName.CREATE -> parseCreate(line.arguments)
        CommandName.PUT -> parsePut(line)
        CommandName.DEL -> parseDelete(line.arguments)
        CommandName.STATS -> parseStats(line.arguments)
        CommandName.SEARCH -> Command.Search(parseSearchRequest(line.arguments))
        CommandName.TERMS -> Command.Terms(parseTermsRequest(line.arguments))
    }

private fun parseCreate(arguments: List<String>): Command.Create {
    val shardCount = arguments.singleOrNull()?.takeIf(SHARD_COUNT_FORMAT::matches)?.toInt() ?: reject(ErrorCode.BAD_ARGUMENT)
    if (shardCount > MAX_SHARD_COUNT) reject(ErrorCode.BAD_ARGUMENT)
    return Command.Create(shardCount)
}

private fun parsePut(line: RequestLine): Command.Put {
    val arguments = line.arguments
    if (arguments.size < 4) reject(ErrorCode.BAD_ARGUMENT)
    val id = parseDocumentId(arguments[0])
    return Command.Put(
        id = id,
        routing = parseRouting(arguments[1], id),
        brand = arguments[2].takeIf(KEYWORD_FORMAT::matches) ?: reject(ErrorCode.BAD_ARGUMENT),
        price = arguments[3].takeIf(PRICE_FORMAT::matches)?.toInt() ?: reject(ErrorCode.BAD_ARGUMENT),
        title = line.textAfterArgument(3),
    )
}

private fun parseDelete(arguments: List<String>): Command.Delete {
    if (arguments.size != 2) reject(ErrorCode.BAD_ARGUMENT)
    val id = parseDocumentId(arguments[0])
    return Command.Delete(id, parseRouting(arguments[1], id))
}

private fun parseStats(arguments: List<String>): Command.Stats {
    if (arguments.isNotEmpty()) reject(ErrorCode.BAD_ARGUMENT)
    return Command.Stats
}

private fun parseDocumentId(argument: String): Int =
    argument.takeIf(DOCUMENT_ID_FORMAT::matches)?.toInt() ?: reject(ErrorCode.BAD_ARGUMENT)

private fun parseRouting(argument: String, id: Int): String =
    when {
        argument == ROUTE_BY_ID -> id.toString()
        KEYWORD_FORMAT.matches(argument) -> argument
        else -> reject(ErrorCode.BAD_ARGUMENT)
    }
