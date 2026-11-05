sealed interface CatalogCommand {
    data class Register(val productId: String, val code: String) : CatalogCommand
    data class Get(val productId: String) : CatalogCommand
    data class ListCategory(val category: String) : CatalogCommand
    data class Remove(val productId: String) : CatalogCommand
    data class Stage(val batchId: String, val productId: String, val code: String) : CatalogCommand
    data class Check(val batchId: String) : CatalogCommand
    data class Apply(val batchId: String) : CatalogCommand
    data class Scan(val text: String) : CatalogCommand
    data object Invalid : CatalogCommand
}

class CatalogCommandReader {
    fun parse(line: String): CatalogCommand {
        val fields = line.split('\t')
        return when (fields[0]) {
            "REGISTER" -> readRegistration(fields)
            "GET" -> readLookup(fields)
            "LIST" -> readCategory(fields)
            "REMOVE" -> readRemoval(fields)
            "STAGE" -> readStagedRegistration(fields)
            "CHECK" -> if (fields.size == 2) CatalogCommand.Check(fields[1]) else CatalogCommand.Invalid
            "APPLY" -> if (fields.size == 2) CatalogCommand.Apply(fields[1]) else CatalogCommand.Invalid
            "SCAN" -> if (fields.size == 2) CatalogCommand.Scan(fields[1]) else CatalogCommand.Invalid
            else -> CatalogCommand.Invalid
        }
    }

    private fun readRegistration(fields: List<String>): CatalogCommand {
        if (fields.size != 3) return CatalogCommand.Invalid
        return CatalogCommand.Register(
            productId = fields[1],
            code = fields[2],
        )
    }

    private fun readStagedRegistration(fields: List<String>): CatalogCommand {
        if (fields.size != 4) return CatalogCommand.Invalid
        return CatalogCommand.Stage(fields[1], fields[2], fields[3])
    }

    private fun readLookup(fields: List<String>): CatalogCommand {
        if (fields.size != 2) return CatalogCommand.Invalid
        return CatalogCommand.Get(fields[1])
    }

    private fun readCategory(fields: List<String>): CatalogCommand {
        if (fields.size != 2) return CatalogCommand.Invalid
        return CatalogCommand.ListCategory(fields[1])
    }

    private fun readRemoval(fields: List<String>): CatalogCommand {
        if (fields.size != 2) return CatalogCommand.Invalid
        return CatalogCommand.Remove(fields[1])
    }
}

fun main() {
    val catalog = ProductCatalog()
    val commands = CatalogCommands(catalog, ProductCodeParser())
    val reader = CatalogCommandReader()
    val output = System.out.bufferedWriter()
    System.`in`.bufferedReader().use { input ->
        input.lineSequence().forEach { line ->
            val command = reader.parse(line)
            output.appendLine(commands.execute(command))
        }
    }
    output.flush()
}
