sealed interface CartCommand {
    data class Add(val key: CartKey, val quantity: Int) : CartCommand
    data class Quantity(val key: CartKey) : CartCommand
    data class SetQuantity(val lineId: Int, val quantity: Int) : CartCommand
    data class Remove(val lineId: Int) : CartCommand
    data object Show : CartCommand
}

object CommandParser {
    private val separator = Regex(" +")
    private val positiveInteger = Regex("[1-9][0-9]*")

    fun parse(line: String): CartCommand {
        val tokens = line.trim().split(separator)
        return when (tokens[0]) {
            "ADD" -> {
                require(tokens.size == 4)
                CartCommand.Add(key(tokens[1], tokens[3]), number(tokens[2]))
            }
            "QUANTITY" -> {
                require(tokens.size == 3)
                CartCommand.Quantity(key(tokens[1], tokens[2]))
            }
            "SET" -> {
                require(tokens.size == 3)
                CartCommand.SetQuantity(number(tokens[1]), number(tokens[2]))
            }
            "REMOVE" -> {
                require(tokens.size == 2)
                CartCommand.Remove(number(tokens[1]))
            }
            "SHOW" -> {
                require(tokens.size == 1)
                CartCommand.Show
            }
            else -> error("Unknown cart command: ${tokens[0]}")
        }
    }

    private fun key(productId: String, options: String): CartKey = CartKey(
        productId = OptionText.productId(productId),
        options = OptionText.parse(options),
    )

    private fun number(token: String): Int {
        require(positiveInteger.matches(token))
        val value = token.toInt()
        require(value in 1..1_000_000)
        return value
    }
}
