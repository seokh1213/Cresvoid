sealed interface Command {
    data class Pool(val name: String, val candidates: List<Candidate>) : Command
    data class Pick(
        val resultName: String,
        val poolName: String,
        val request: RecommendationRequest,
    ) : Command
    data class Show(val resultNames: List<String>) : Command
}

class CommandParser {
    fun parse(line: String): Command {
        val tokens = line.trim().split(Regex("\\s+"))
        return when (tokens.first()) {
            "POOL" -> parsePool(tokens)
            "PICK" -> parsePick(tokens)
            "SHOW" -> parseShow(tokens)
            else -> throw IllegalArgumentException("unknown command")
        }
    }

    private fun parsePool(tokens: List<String>): Command.Pool {
        require(tokens.size >= 2)
        val name = name(tokens[1])
        val candidates = tokens.drop(2).map { token ->
            val fields = token.split(':')
            require(fields.size == 2)
            Candidate(fields[0], fields[1].toInt())
        }
        return Command.Pool(name, candidates)
    }

    private fun parsePick(tokens: List<String>): Command.Pick {
        require(tokens.size == 7)
        val resultName = name(tokens[1])
        val poolName = name(tokens[2])
        val request = RecommendationRequest(
            sessionId = tokens[3],
            page = tokens[4].toInt(),
            count = tokens[5].toInt(),
            qualityPercent = tokens[6].toInt(),
        )
        return Command.Pick(resultName, poolName, request)
    }

    private fun parseShow(tokens: List<String>): Command.Show {
        require(tokens.size >= 2)
        return Command.Show(tokens.drop(1).map(::name))
    }

    private fun name(token: String): String {
        require(token.matches(Regex("[A-Za-z][A-Za-z0-9_-]*")))
        return token
    }
}

class RecommendationConsole(
    private val catalog: PoolCatalog,
    private val selector: RecommendationSelector,
    private val archive: RecommendationArchive,
) {
    fun execute(command: Command): String? {
        return when (command) {
            is Command.Pool -> {
                catalog.replace(command.name, command.candidates)
                null
            }
            is Command.Pick -> {
                val pool = catalog.get(command.poolName)
                val ids = selector.select(pool, command.request)
                archive.save(command.resultName, ids)
                null
            }
            is Command.Show -> archive.render(command.resultNames)
        }
    }
}

fun main() {
    val parser = CommandParser()
    val console = RecommendationConsole(
        catalog = PoolCatalog(),
        selector = RecommendationSelector(),
        archive = RecommendationArchive(),
    )
    System.`in`.bufferedReader().useLines { lines ->
        for (line in lines) {
            if (line.isBlank()) continue
            try {
                val output = console.execute(parser.parse(line))
                if (output != null) println(output)
            } catch (_: IllegalArgumentException) {
                println("ERROR")
            }
        }
    }
}
