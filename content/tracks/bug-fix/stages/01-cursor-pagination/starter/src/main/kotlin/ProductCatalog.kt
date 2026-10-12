@JvmInline
value class ProductId private constructor(private val value: Int) : Comparable<ProductId> {

    override fun compareTo(other: ProductId): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()

    companion object {
        private val FORMAT = Regex("[1-9][0-9]{0,8}")

        fun parseOrNull(text: String): ProductId? =
            if (FORMAT.matches(text)) ProductId(text.toInt()) else null
    }
}

data class Product(
    val id: ProductId,
    val category: String,
    val salesCount: Int,
)

data class PageCursor(val salesCount: Int) {

    fun precedes(product: Product): Boolean = product.salesCount < salesCount

    companion object {
        fun after(product: Product): PageCursor = PageCursor(product.salesCount)
    }
}

data class ProductPage(
    val products: List<Product>,
    val nextCursor: PageCursor?,
) {
    val hasNext: Boolean get() = nextCursor != null
}

private val bestSellingFirst: Comparator<Product> =
    compareByDescending<Product> { it.salesCount }
        .thenBy { it.id }

class ProductCatalog {
    private val productsById = HashMap<ProductId, Product>()
    private val productIdsByCategory = HashMap<String, MutableList<ProductId>>()

    fun register(product: Product): Boolean {
        if (product.id in productsById) return false
        productsById[product.id] = product
        productIdsByCategory.getOrPut(product.category) { mutableListOf() }.add(product.id)
        return true
    }

    fun firstPage(category: String, pageSize: Int): ProductPage =
        pageOf(rankedIn(category), pageSize)

    fun pageAfter(cursor: PageCursor, category: String, pageSize: Int): ProductPage =
        pageOf(rankedIn(category).filter(cursor::precedes), pageSize)

    private fun rankedIn(category: String): List<Product> =
        productIdsByCategory[category].orEmpty()
            .map(productsById::getValue)
            .sortedWith(bestSellingFirst)

    private fun pageOf(candidates: List<Product>, pageSize: Int): ProductPage {
        val products = candidates.take(pageSize)
        val nextCursor = if (candidates.size > pageSize) PageCursor.after(products.last()) else null
        return ProductPage(products, nextCursor)
    }
}
