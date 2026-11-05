import java.time.Instant

fun main() {
    val catalog = SaleCatalog()
    val input = System.`in`.bufferedReader()
    System.out.bufferedWriter().use { output ->
        input.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val result = execute(line.trim().split(Regex("\\s+")), catalog)
            output.appendLine(result)
        }
    }
}

private fun execute(tokens: List<String>, catalog: SaleCatalog): String = try {
    when (tokens[0]) {
        "PRODUCT" -> {
            catalog.registerProduct(tokens[1], tokens[2].toLong())
            "OK PRODUCT ${tokens[1]}"
        }
        "SALE" -> {
            val window = SaleWindow(
                startsAt = Instant.parse(tokens[3]),
                endsAt = Instant.parse(tokens[4]),
            )
            val offer = SaleOffer(tokens[1], window, tokens[5].toLong())
            catalog.scheduleSale(tokens[2], offer)
            "OK SALE ${offer.id}"
        }
        "ORDER" -> {
            val request = OrderRequest(
                id = tokens[1],
                sku = tokens[2],
                quantity = tokens[3].toInt(),
                placedAt = Instant.parse(tokens[4]),
            )
            catalog.placeOrder(request).render()
        }
        "RECEIPT" -> catalog.receipt(tokens[1]).render()
        else -> error("Unknown command")
    }
} catch (rejection: CatalogRejection) {
    "ERROR ${rejection.reason}"
}
