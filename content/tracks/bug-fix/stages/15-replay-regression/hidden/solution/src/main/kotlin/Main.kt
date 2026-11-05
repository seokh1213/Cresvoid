enum class CatalogSource {
    LIVE,
    TRIAL,
}

sealed interface CatalogCommand {
    data class Change(val record: CatalogChange) : CatalogCommand
    data class Replay(val round: Int) : CatalogCommand
}

private val catalogToken = Regex("[a-z0-9-]{1,40}")

private fun parseRound(token: String): Int {
    val round = token.toInt()
    require(round in 1..1_000_000)
    return round
}

private fun parseChange(tokens: List<String>): CatalogCommand.Change {
    require(tokens.size in 6..7)
    val source = CatalogSource.valueOf(tokens[1])
    val round = parseRound(tokens[2])
    val sequence = tokens[3].toLong()
    require(sequence in 1L..1_000_000_000_000L)
    val key = tokens[5]
    require(catalogToken.matches(key))
    val revision = when (tokens[4]) {
        "PUT" -> {
            require(tokens.size == 7)
            require(catalogToken.matches(tokens[6]))
            CatalogRevision.Content(tokens[6])
        }
        "DELETE" -> {
            require(tokens.size == 6)
            CatalogRevision.Deleted
        }
        else -> error("Unknown catalog operation")
    }
    return CatalogCommand.Change(
        CatalogChange(source, RecordPosition(round, sequence), key, revision)
    )
}

private fun parseCommand(line: String): CatalogCommand {
    val tokens = line.trim().split(Regex("\\s+"))
    return when (tokens[0]) {
        "CHANGE" -> parseChange(tokens)
        "REPLAY" -> {
            require(tokens.size == 2)
            CatalogCommand.Replay(parseRound(tokens[1]))
        }
        else -> error("Unknown catalog command")
    }
}

fun main() {
    val history = ChangeHistory()
    val merger = CatalogMerger(history)
    val planner = DeliveryPlanner(history, merger)
    val output = System.out.bufferedWriter()
    System.`in`.bufferedReader().useLines { lines ->
        for (line in lines) {
            if (line.isBlank()) continue
            when (val command = parseCommand(line)) {
                is CatalogCommand.Change -> history.append(command.record)
                is CatalogCommand.Replay -> {
                    val batch = planner.plan(command.round)
                    for (row in batch.render()) {
                        output.appendLine(row)
                    }
                }
            }
        }
    }
    output.flush()
}
