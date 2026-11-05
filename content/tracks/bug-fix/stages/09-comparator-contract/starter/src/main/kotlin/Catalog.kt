data class Product(
    val id: String,
    val category: String,
    val score: Double,
    val stock: Int,
)

class Catalog {
    private val products = linkedMapOf<String, Product>()

    fun put(product: Product) {
        products[product.id] = product
    }

    fun updateScore(id: String, score: Double): Boolean {
        val product = products[id] ?: return false
        products[id] = product.copy(score = score)
        return true
    }

    fun updateStock(id: String, stock: Int): Boolean {
        val product = products[id] ?: return false
        products[id] = product.copy(stock = stock)
        return true
    }

    fun remove(id: String): Boolean = products.remove(id) != null

    fun find(id: String): Product? = products[id]

    fun availableIn(category: String): List<Product> =
        products.values.filter { it.category == category && it.stock > 0 }
}

class CatalogOutput {
    fun mutation(changed: Boolean): String = if (changed) "OK" else "MISSING"

    fun product(product: Product?): String {
        if (product == null) return "MISSING"
        return listOf(
            product.id,
            product.category,
            product.score.toString(),
            product.stock.toString(),
        ).joinToString(" ")
    }

    fun recommendations(products: List<Product>): String {
        if (products.isEmpty()) return "EMPTY"
        return products.joinToString(" ") { it.id }
    }
}
