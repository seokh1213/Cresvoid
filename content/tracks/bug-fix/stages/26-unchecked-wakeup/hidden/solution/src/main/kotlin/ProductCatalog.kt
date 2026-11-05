import java.util.concurrent.ConcurrentHashMap

data class Product(val code: String, val name: String, val version: Int) {
    fun label(): String = "$version:$name"
}

class ProductCatalog {
    private val products = linkedMapOf<String, Product>()
    private val searchIndex = ConcurrentHashMap<String, Product>()

    fun put(code: String, name: String): String {
        val version = (products[code]?.version ?: 0) + 1
        products[code] = Product(code, name, version)
        return "PUT $code $version"
    }

    fun show(code: String): String {
        val product = products[code] ?: return "MISSING $code"
        val indexed = searchIndex[code]?.label() ?: "-"
        return "PRODUCT $code source=${product.label()} index=$indexed"
    }

    fun snapshot(codes: List<String>): List<Product>? {
        if (codes.any { it !in products }) return null
        return codes.map { products.getValue(it) }
    }

    fun index(product: Product) {
        searchIndex[product.code] = product
    }
}

data class RefreshReport(val completed: Int, val alive: Int, val errors: List<String>) {
    fun render(accepted: Int): String {
        val errorNames = errors.joinToString(",").ifEmpty { "-" }
        return "REFRESH accepted=$accepted completed=$completed alive=$alive errors=$errorNames"
    }
}
