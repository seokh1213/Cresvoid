fun main() {
    val parser = CommandParser()
    val catalog = Catalog()
    val recommendations = Recommendations(catalog)
    val service = CatalogService(catalog, recommendations, CatalogOutput())
    val reader = System.`in`.bufferedReader()
    val writer = System.out.bufferedWriter()

    reader.useLines { lines ->
        for (line in lines) {
            if (line.isBlank()) continue
            val command = parser.parse(line)
            writer.appendLine(service.execute(command))
            writer.flush()
        }
    }
    writer.flush()
}

class CatalogService(
    private val catalog: Catalog,
    private val recommendations: Recommendations,
    private val output: CatalogOutput,
) {
    fun execute(command: CatalogCommand): String = when (command) {
        is CatalogCommand.Put -> register(command)
        is CatalogCommand.Score -> changeScore(command)
        is CatalogCommand.Stock -> changeStock(command)
        is CatalogCommand.Remove -> output.mutation(catalog.remove(command.id))
        is CatalogCommand.Get -> output.product(catalog.find(command.id))
        is CatalogCommand.Show -> show(command)
        is CatalogCommand.Policy -> configure(command)
    }

    private fun register(command: CatalogCommand.Put): String {
        catalog.put(command.product)
        return "OK"
    }

    private fun changeScore(command: CatalogCommand.Score): String {
        val changed = catalog.updateScore(command.id, command.score)
        return output.mutation(changed)
    }

    private fun changeStock(command: CatalogCommand.Stock): String {
        val changed = catalog.updateStock(command.id, command.stock)
        return output.mutation(changed)
    }

    private fun show(command: CatalogCommand.Show): String {
        val products = recommendations.show(command.category, command.limit)
        return output.recommendations(products)
    }

    private fun configure(command: CatalogCommand.Policy): String {
        recommendations.configure(command.category, command.policy)
        return "OK"
    }
}
