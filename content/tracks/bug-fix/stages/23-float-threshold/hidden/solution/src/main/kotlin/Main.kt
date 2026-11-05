import java.util.Locale

fun main() {
    val carts = CartService()
    val commands = CartCommands(carts, ShippingPolicy())
    System.`in`.bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() }.forEach { line ->
            val tokens = line.trim().split(Regex("\\s+"))
            val response = try {
                commands.execute(tokens)
            } catch (failure: CartFailure) {
                "ERROR ${failure.reason}"
            }
            println(response)
        }
    }
}

private class CartCommands(
    private val carts: CartService,
    private val shipping: ShippingPolicy,
) {
    fun execute(tokens: List<String>): String = when (tokens.first()) {
        "PRICE" -> registerPrice(tokens[1], tokens[2])
        "PUT" -> putLine(tokens[1], tokens[2], tokens[3])
        "DROP" -> dropLine(tokens[1], tokens[2])
        "ITEMS" -> renderItems(tokens[1])
        "QUOTE" -> renderQuote(tokens[1])
        else -> "ERROR command"
    }

    private fun registerPrice(productId: String, amount: String): String {
        carts.registerPrice(productId, amount)
        return "OK"
    }

    private fun putLine(cartId: String, lineId: String, productId: String): String {
        carts.putLine(cartId, lineId, productId)
        return "OK"
    }

    private fun dropLine(cartId: String, lineId: String): String {
        carts.dropLine(cartId, lineId)
        return "OK"
    }

    private fun renderItems(cartId: String): String {
        val lines = carts.lines(cartId)
        val listing = if (lines.isEmpty()) {
            "(empty)"
        } else {
            lines.sortedBy { it.lineId }.joinToString(" ") { line ->
                "${line.lineId}:${line.productId}:${line.price}"
            }
        }
        return "ITEMS $cartId $listing"
    }

    private fun renderQuote(cartId: String): String {
        val quote = shipping.quote(carts.lines(cartId))
        return String.format(
            Locale.ROOT,
            "QUOTE %s subtotal=%.2f shipping=%.2f total=%.2f",
            cartId,
            quote.subtotal,
            quote.shippingFee,
            quote.total,
        )
    }
}
