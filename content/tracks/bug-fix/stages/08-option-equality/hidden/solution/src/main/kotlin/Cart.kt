data class CartKey(
    val productId: String,
    val options: List<OptionSelection>,
)

class CartLine(
    val id: Int,
    val key: CartKey,
    quantity: Int,
) {
    var quantity: Int = quantity
        private set

    fun addQuantity(amount: Int) {
        quantity += amount
    }

    fun replaceQuantity(amount: Int) {
        quantity = amount
    }
}

class Cart {
    private val linesByKey = mutableMapOf<CartKey, CartLine>()
    private val linesById = sortedMapOf<Int, CartLine>()
    private var nextLineId = 1

    fun add(command: CartCommand.Add): CartLine {
        val existing = linesByKey[command.key]
        if (existing != null) {
            existing.addQuantity(command.quantity)
            return existing
        }

        val created = CartLine(nextLineId++, command.key, command.quantity)
        linesByKey[command.key] = created
        linesById[created.id] = created
        return created
    }

    fun quantity(key: CartKey): Int = linesByKey[key]?.quantity ?: 0

    fun setQuantity(command: CartCommand.SetQuantity): CartLine? {
        val line = linesById[command.lineId] ?: return null
        line.replaceQuantity(command.quantity)
        return line
    }

    fun remove(lineId: Int): Boolean {
        val line = linesById.remove(lineId) ?: return false
        linesByKey.remove(line.key)
        return true
    }

    fun lines(): List<CartLine> = linesById.values.toList()
}
