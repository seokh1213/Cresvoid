data class CartLine(
    val lineId: String,
    val productId: String,
    val price: String,
)

class CartFailure(val reason: String) : RuntimeException(reason)

class CartService {
    private val catalog = PriceCatalog()
    private val carts = mutableMapOf<String, Cart>()

    fun registerPrice(productId: String, amount: String) {
        catalog.register(productId, amount)
    }

    fun putLine(cartId: String, lineId: String, productId: String) {
        val price = catalog.price(productId)
        val line = CartLine(lineId, productId, price)
        carts.getOrPut(cartId) { Cart() }.put(line)
    }

    fun dropLine(cartId: String, lineId: String) {
        val cart = carts[cartId] ?: throw CartFailure("line")
        cart.drop(lineId)
    }

    fun lines(cartId: String): List<CartLine> = carts[cartId]?.lines().orEmpty()
}

private class PriceCatalog {
    private val prices = mutableMapOf<String, String>()
    private val amountPattern = Regex("(?:0|[1-9][0-9]{0,5})\\.[0-9]{2}")

    fun register(productId: String, amount: String) {
        if (!amountPattern.matches(amount)) {
            throw CartFailure("amount")
        }
        prices[productId] = amount
    }

    fun price(productId: String): String = prices[productId] ?: throw CartFailure("product")
}

private class Cart {
    private val linesById = mutableMapOf<String, CartLine>()

    fun put(line: CartLine) {
        linesById[line.lineId] = line
    }

    fun drop(lineId: String) {
        if (linesById.remove(lineId) == null) {
            throw CartFailure("line")
        }
    }

    fun lines(): List<CartLine> = linesById.values.toList()
}
