import java.math.BigDecimal

data class Product(val sku: String, val price: BigDecimal, val bundleGroup: String?)

data class CartLine(val sku: String, val quantity: Int)

data class Cart(
    val grade: MemberGrade = MemberGrade.BASIC,
    val coupon: Coupon? = null,
    val lines: MutableList<CartLine> = mutableListOf(),
) {
    fun add(sku: String, quantity: Int) {
        require(quantity > 0) { "quantity must be positive: $quantity" }
        val index = lines.indexOfFirst { it.sku == sku }
        if (index == -1) {
            lines.add(CartLine(sku, quantity))
        } else {
            val existing = lines[index]
            lines[index] = existing.copy(quantity = existing.quantity + quantity)
        }
    }
}

fun main() {
    val catalog = mutableMapOf<String, Product>()
    val bundleDiscounts = linkedMapOf<String, DiscountRule.BundleDiscount>()
    val checkout = Checkout(catalog, bundleDiscounts)
    var cart = Cart()

    for (command in generateSequence(::readlnOrNull).map(String::trim).filter(String::isNotEmpty)) {
        val words = command.split(Regex("\\s+"))
        try {
            when (words[0]) {
                "PRODUCT" -> catalog[words[1]] = Product(words[1], parseWon(words[2]), words.getOrNull(3))
                "BUNDLE" -> bundleDiscounts[words[1]] =
                    DiscountRule.BundleDiscount(words[1], words[2].toInt(), words[3].toBigDecimal().percentToRate())
                "GRADE" -> cart = cart.copy(grade = MemberGrade.valueOf(words[1]))
                "COUPON" -> cart = cart.copy(coupon = parseCoupon(words.drop(1)))
                "ADD" -> {
                    require(words[1] in catalog) { "unknown product ${words[1]}" }
                    cart.add(words[1], words[2].toInt())
                }
                "LINE" -> {
                    val line = checkout.price(cart).find { it.product.sku == words[1] }
                    println(if (line == null) "ERROR not in cart ${words[1]}" else formatLine(line))
                }
                "TOTAL" -> println(formatTotal(checkout.price(cart)))
                else -> println("ERROR unknown command ${words[0]}")
            }
        } catch (error: IllegalArgumentException) {
            println("ERROR ${error.message}")
        } catch (error: IndexOutOfBoundsException) {
            println("ERROR missing argument: $command")
        }
    }
}

private fun parseCoupon(args: List<String>): Coupon = when (args[0]) {
    "RATE" -> Coupon.Rate(
        rate = args[1].toBigDecimal().percentToRate(),
        minimumOrder = parseWon(args[2]),
        maximumDiscount = parseWon(args[3]),
    )
    "FIXED" -> Coupon.Fixed(amount = parseWon(args[1]), minimumOrder = parseWon(args[2]))
    else -> throw IllegalArgumentException("unknown coupon type ${args[0]}")
}

private fun formatLine(line: PricedLine): String = buildList {
    add(line.product.sku)
    add("list=${line.listAmount.toPlainString()}")
    line.discounts.forEach { add("${it.label}=${it.amount.toPlainString()}") }
    add("pay=${line.payable.toPlainString()}")
}.joinToString(" ")

private fun formatTotal(lines: List<PricedLine>): String {
    val listTotal = lines.sumOf { it.listAmount }
    val payTotal = lines.sumOf { it.payable }
    val discountTotal = listTotal - payTotal
    return "list=${listTotal.toPlainString()} discount=${discountTotal.toPlainString()} pay=${payTotal.toPlainString()}"
}
