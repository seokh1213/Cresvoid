sealed interface CatalogCommand {
    data class Open(val placement: CachePlacement, val maxAttempts: Int) : CatalogCommand
    data class Plan(val sku: String, val replies: List<StoreReply>) : CatalogCommand
    data class Get(val sku: String) : CatalogCommand
    data class Many(val skus: List<String>) : CatalogCommand
    data object Report : CatalogCommand
}

fun parseCommand(line: String): CatalogCommand {
    val fields = line.trim().split(Regex("\\s+"))
    return when (fields.first()) {
        "OPEN" -> {
            require(fields.size == 3)
            val placement = CachePlacement.valueOf(fields[1])
            val maxAttempts = fields[2].toInt()
            require(maxAttempts in 1..5)
            CatalogCommand.Open(placement, maxAttempts)
        }
        "PLAN" -> {
            require(fields.size == 3)
            CatalogCommand.Plan(fields[1], fields[2].split(',').map(::parseReply))
        }
        "GET" -> {
            require(fields.size == 2)
            CatalogCommand.Get(fields[1])
        }
        "MANY" -> {
            require(fields.size == 2)
            val skus = fields[1].split(',')
            require(skus.all { it.isNotEmpty() })
            CatalogCommand.Many(skus)
        }
        "REPORT" -> {
            require(fields.size == 1)
            CatalogCommand.Report
        }
        else -> error("Unknown catalog command")
    }
}

class CatalogConsole {
    private var session: CatalogSession? = null

    fun execute(command: CatalogCommand): String? {
        if (command is CatalogCommand.Open) {
            session = CatalogSession(command.placement, command.maxAttempts)
            return null
        }
        val current = checkNotNull(session) { "Open a catalog session first" }
        when (command) {
            is CatalogCommand.Plan -> current.plan(command.sku, command.replies)
            is CatalogCommand.Get -> current.get(command.sku)
            is CatalogCommand.Many -> current.getMany(command.skus)
            CatalogCommand.Report -> return current.report()
            is CatalogCommand.Open -> error("Catalog session already opened")
        }
        return null
    }
}

fun main() {
    val console = CatalogConsole()
    generateSequence(::readlnOrNull)
        .filter { it.isNotBlank() }
        .map(::parseCommand)
        .forEach { command ->
            console.execute(command)?.let(::println)
        }
}
