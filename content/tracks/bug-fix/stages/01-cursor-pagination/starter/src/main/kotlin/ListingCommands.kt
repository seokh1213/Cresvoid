private const val UNKNOWN_COMMAND = "ERR UNKNOWN_COMMAND"
private const val BAD_ARGUMENT = "ERR BAD_ARGUMENT"
private const val DUPLICATE_PRODUCT = "ERR DUPLICATE_PRODUCT"
private const val UNKNOWN_SESSION = "ERR UNKNOWN_SESSION"
private const val NO_NEXT_PAGE = "ERR NO_NEXT_PAGE"
private const val MAX_PAGE_SIZE = 100

private val SALES_COUNT_FORMAT = Regex("0|[1-9][0-9]{0,8}")
private val PAGE_SIZE_FORMAT = Regex("[1-9][0-9]*")

private data class BrowsingSession(
    val category: String,
    val pageSize: Int,
    val nextCursor: PageCursor?,
)

class ListingCommands(private val catalog: ProductCatalog) {
    private val sessions = HashMap<String, BrowsingSession>()

    fun execute(line: String): String? {
        val words = line.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val command = words.first()
        val arguments = words.drop(1)
        return when (command) {
            "ADD" -> addProduct(arguments)
            "LIST" -> openSession(arguments)
            "NEXT" -> showNextPage(arguments)
            else -> UNKNOWN_COMMAND
        }
    }

    private fun addProduct(arguments: List<String>): String? {
        if (arguments.size != 3) return BAD_ARGUMENT
        val (idText, category, salesCountText) = arguments
        val id = ProductId.parseOrNull(idText) ?: return BAD_ARGUMENT
        val salesCount = parseSalesCount(salesCountText) ?: return BAD_ARGUMENT
        val registered = catalog.register(Product(id, category, salesCount))
        return if (registered) null else DUPLICATE_PRODUCT
    }

    private fun openSession(arguments: List<String>): String {
        if (arguments.size != 3) return BAD_ARGUMENT
        val (sessionName, category, pageSizeText) = arguments
        val pageSize = parsePageSize(pageSizeText) ?: return BAD_ARGUMENT
        val page = catalog.firstPage(category, pageSize)
        sessions[sessionName] = BrowsingSession(category, pageSize, page.nextCursor)
        return render(page)
    }

    private fun showNextPage(arguments: List<String>): String {
        val sessionName = arguments.singleOrNull() ?: return BAD_ARGUMENT
        val session = sessions[sessionName] ?: return UNKNOWN_SESSION
        val cursor = session.nextCursor ?: return NO_NEXT_PAGE
        val page = catalog.pageAfter(cursor, session.category, session.pageSize)
        sessions[sessionName] = session.copy(nextCursor = page.nextCursor)
        return render(page)
    }

    private fun parseSalesCount(text: String): Int? =
        if (SALES_COUNT_FORMAT.matches(text)) text.toInt() else null

    private fun parsePageSize(text: String): Int? {
        if (!PAGE_SIZE_FORMAT.matches(text)) return null
        return text.toIntOrNull()?.takeIf { it <= MAX_PAGE_SIZE }
    }

    private fun render(page: ProductPage): String {
        val productIds = page.products.map { it.id.toString() }
        val marker = if (page.hasNext) "MORE" else "END"
        return (productIds + marker).joinToString(" ")
    }
}
