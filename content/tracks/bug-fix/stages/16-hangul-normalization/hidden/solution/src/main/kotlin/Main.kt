fun main() {
    val catalog = ProductCatalog()
    val input = System.`in`.bufferedReader(Charsets.UTF_8)
    val output = System.out.bufferedWriter(Charsets.UTF_8)
    input.useLines { lines ->
        for (line in lines) {
            val command = CommandParser.parse(line)
            val response = execute(command, catalog)
            for (resultLine in response) {
                output.appendLine(resultLine)
            }
        }
    }
    output.flush()
}

private fun execute(
    command: CatalogCommand,
    catalog: ProductCatalog,
): List<String> = when (command) {
    is CatalogCommand.Put -> {
        catalog.put(command.id, command.title)
        listOf("SAVED ${command.id}")
    }
    is CatalogCommand.Remove -> {
        val result = if (catalog.remove(command.id)) "REMOVED" else "MISSING"
        listOf("$result ${command.id}")
    }
    is CatalogCommand.Search -> {
        val query = SearchQuery.from(command.text)
        val matches = catalog.search(query)
        val ids = if (matches.isEmpty()) "-" else matches.joinToString(",")
        listOf("FOUND $ids")
    }
    CatalogCommand.ListProducts -> {
        val products = catalog.list()
        buildList {
            add("LIST ${products.size}")
            for (product in products) {
                add("${product.id}|${product.title}")
            }
        }
    }
    is CatalogCommand.Rejected -> listOf("ERR ${command.reason}")
}
