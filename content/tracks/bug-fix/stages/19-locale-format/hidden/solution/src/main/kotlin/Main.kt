import java.util.Locale

fun main() {
    Locale.setDefault(Locale.forLanguageTag("en-US"))
    val terminal = CatalogTerminal()
    System.`in`.bufferedReader().useLines { lines ->
        for (line in lines) {
            if (line.isBlank()) continue
            println(terminal.execute(line.trim().split(Regex("\\s+"))))
        }
    }
}

private class CatalogTerminal {
    private val catalog = PricingCatalog()
    private val documents = PriceDocuments()
    private val archive = QuoteArchive(catalog, documents)
    private val localeTags = setOf("en-US", "ko-KR", "de-DE", "fr-FR", "ar-EG")

    fun execute(words: List<String>): String = try {
        when (words.first()) {
            "LOCALE" -> configureLocale(words)
            "STATUS" -> {
                require(words.size == 1)
                "STATUS ${Locale.getDefault().toLanguageTag()} ${catalog.size}"
            }
            "REGISTER" -> registerLayout(words)
            "SELECT" -> selectLayout(words)
            "PUT" -> putProduct(words)
            "SHOW" -> showProduct(words)
            "FREEZE" -> freezeQuote(words)
            "DRAFT" -> showQuote(words)
            "NOTICE", "EXPORT" -> printPrice(words)
            else -> "ERROR invalid-command"
        }
    } catch (_: IllegalArgumentException) {
        "ERROR invalid-command"
    }

    private fun configureLocale(words: List<String>): String {
        require(words.size == 2 && words[1] in localeTags)
        Locale.setDefault(Locale.forLanguageTag(words[1]))
        return "OK"
    }

    private fun putProduct(words: List<String>): String {
        require(words.size == 4)
        val productId = productId(words[1])
        val unitPrice = unsignedInteger(words[2], 1_000_000_000)
        val discountBasisPoints = unsignedInteger(words[3], 10_000).toInt()
        catalog.put(ProductPrice(productId, unitPrice, discountBasisPoints))
        return "OK"
    }

    private fun showProduct(words: List<String>): String {
        require(words.size == 2)
        val product = catalog.find(productId(words[1])) ?: return "ERROR unknown-product"
        return "PRODUCT ${product.id} ${product.unitPrice} ${product.discountBasisPoints}"
    }

    private fun printPrice(words: List<String>): String {
        require(words.size in 2..3)
        val channel = DocumentChannel.valueOf(words[0])
        if (words.size == 2) {
            val document = archive.find(productId(words[1]))?.documents ?: return "ERROR unknown-quote"
            return document.render(channel)
        }
        val productId = productId(words[1])
        val quantity = quantity(words[2])
        val quotation = catalog.quote(productId, quantity) ?: return "ERROR unknown-product"
        return documents.render(quotation, channel)
    }

    private fun registerLayout(words: List<String>): String {
        require(words.size == 4)
        val layoutId = productId(words[1])
        val noticePrefix = productId(words[2])
        val rowPrefix = productId(words[3])
        documents.register(layoutId, noticePrefix, rowPrefix)
        return "OK"
    }

    private fun selectLayout(words: List<String>): String {
        require(words.size == 2)
        return if (documents.select(productId(words[1]))) "OK" else "ERROR unknown-layout"
    }

    private fun freezeQuote(words: List<String>): String {
        require(words.size == 4)
        val quoteId = productId(words[1])
        val productId = productId(words[2])
        val quantity = quantity(words[3])
        return if (archive.freeze(quoteId, productId, quantity)) "OK" else "ERROR unknown-product"
    }

    private fun showQuote(words: List<String>): String {
        require(words.size == 2)
        val quoteId = productId(words[1])
        val quote = archive.find(quoteId)?.quotation ?: return "ERROR unknown-quote"
        return listOf(
            "QUOTE", quoteId, quote.productId, quote.quantity,
            quote.originalAmount, quote.discountAmount, quote.payableAmount, quote.discountBasisPoints,
        ).joinToString(" ")
    }

    private fun quantity(token: String): Int {
        val quantity = unsignedInteger(token, 10_000).toInt()
        require(quantity >= 1)
        return quantity
    }

    private fun productId(token: String): String {
        require(token.matches(Regex("[A-Za-z0-9_-]{1,30}")))
        return token
    }

    private fun unsignedInteger(token: String, maximum: Long): Long {
        require(token.isNotEmpty() && token.all { it in '0'..'9' })
        val amount = token.trimStart('0').ifEmpty { "0" }.toLongOrNull()
        require(amount != null && amount <= maximum)
        return amount
    }
}
