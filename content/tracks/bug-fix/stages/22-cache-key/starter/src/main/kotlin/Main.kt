sealed interface CatalogCommand

data class SetProduct(val product: Product) : CatalogCommand

data class ReadPrice(val request: PriceRequest) : CatalogCommand

data class ListPrices(
    val tier: MemberTier,
    val coupon: Coupon,
    val productIds: List<String>,
) : CatalogCommand

data class AdvanceTime(val seconds: Long) : CatalogCommand

data class ClearPrices(val productId: String) : CatalogCommand

fun parseCommand(line: String): CatalogCommand {
    val words = line.trim().split(Regex("\\s+"))
    return when (words[0]) {
        "SET" -> SetProduct(
            Product(words[1], words[2].toLong(), words[3].toLong()),
        )
        "PRICE" -> ReadPrice(
            PriceRequest(
                productId = words[1],
                tier = MemberTier.valueOf(words[2]),
                coupon = Coupon.valueOf(words[3]),
            ),
        )
        "LIST" -> ListPrices(
            tier = MemberTier.valueOf(words[1]),
            coupon = Coupon.valueOf(words[2]),
            productIds = words.drop(3),
        )
        "ADVANCE" -> AdvanceTime(words[1].toLong())
        "CLEAR" -> ClearPrices(words[1])
        else -> error("Unknown command")
    }
}

class CatalogCommands(
    private val service: CatalogService,
    private val clock: CatalogClock,
) {
    fun execute(command: CatalogCommand): String = when (command) {
        is SetProduct -> {
            service.register(command.product)
            "OK ${command.product.id}"
        }
        is ReadPrice -> service.price(command.request).singleLine()
        is ListPrices -> service.list(command)
        is AdvanceTime -> "TIME ${clock.advance(command.seconds)}"
        is ClearPrices -> {
            service.clear(command.productId)
            "OK ${command.productId}"
        }
    }
}

fun main() {
    val clock = CatalogClock()
    val service = CatalogService(ProductCatalog(), PriceCache(clock), PriceCalculator())
    val commands = CatalogCommands(service, clock)
    System.`in`.bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() }.forEach { line ->
            println(commands.execute(parseCommand(line)))
        }
    }
}
