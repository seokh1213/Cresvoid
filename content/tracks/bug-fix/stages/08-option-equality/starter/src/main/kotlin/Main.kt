fun main() {
    val cart = Cart()
    System.`in`.bufferedReader().use { input ->
        System.out.bufferedWriter().use { output ->
            input.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val command = CommandParser.parse(line)
                val response = execute(cart, command)
                output.write(response)
                output.newLine()
            }
        }
    }
}

private fun execute(cart: Cart, command: CartCommand): String = when (command) {
    is CartCommand.Add -> {
        val line = cart.add(command)
        "ADDED ${line.id} ${line.quantity}"
    }
    is CartCommand.Quantity -> "QUANTITY ${cart.quantity(command.key)}"
    is CartCommand.SetQuantity -> {
        val line = cart.setQuantity(command)
        if (line == null) "NOT_FOUND ${command.lineId}"
        else "SET ${line.id} ${line.quantity}"
    }
    is CartCommand.Remove -> {
        if (cart.remove(command.lineId)) "REMOVED ${command.lineId}"
        else "NOT_FOUND ${command.lineId}"
    }
    CartCommand.Show -> show(cart.lines())
}

private fun show(lines: List<CartLine>): String = buildString {
    append("CART ${lines.size} ${lines.sumOf { it.quantity.toLong() }}")
    for (line in lines) {
        append('\n')
        append("LINE ${line.id} ${line.key.productId} ${line.quantity} ")
        append(OptionText.format(line.key.options))
    }
}
