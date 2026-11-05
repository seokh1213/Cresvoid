class CatalogCommands {
    private val versions = CatalogVersions()
    private val planner = RolloverPlanner(versions)
    private val executor = RolloverExecutor(versions)

    fun execute(line: String): String {
        val tokens = line.trim().split(Regex("\\s+"))
        return when (tokens.first()) {
            "PUT" -> put(tokens)
            "DEL" -> delete(tokens)
            "GET" -> {
                require(tokens.size == 3)
                versions.read(tokens[1], tokens[2].toInt())
            }
            "HISTORY" -> {
                require(tokens.size == 2)
                versions.history(tokens[1])
            }
            "PLAN" -> {
                require(tokens.size == 4)
                planner.create(tokens[1], tokens[2].toInt(), tokens[3].toInt())
            }
            "LOAD" -> {
                require(tokens.size == 4)
                planner.find(tokens[1]).recordLoad(tokens[2], tokens[3])
            }
            "APPLY" -> {
                require(tokens.size == 2)
                executor.apply(planner.find(tokens[1]))
            }
            else -> error("Unknown command")
        }
    }

    private fun put(tokens: List<String>): String {
        require(tokens.size == 5)
        versions.append(tokens[1], tokens[2].toInt(), tokens[3], tokens[4])
        return "OK"
    }

    private fun delete(tokens: List<String>): String {
        require(tokens.size == 4)
        versions.append(tokens[1], tokens[2].toInt(), tokens[3], null)
        return "OK"
    }
}

fun main() {
    val commands = CatalogCommands()
    System.`in`.bufferedReader().forEachLine { line ->
        if (line.isNotBlank()) println(commands.execute(line))
    }
}
