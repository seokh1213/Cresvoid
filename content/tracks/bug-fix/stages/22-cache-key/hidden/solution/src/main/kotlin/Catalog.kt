class ProductCatalog {
    private val products = mutableMapOf<String, Product>()

    fun put(product: Product) {
        products[product.id] = product
    }

    fun find(productId: String): Product? = products[productId]
}

class CatalogService(
    private val catalog: ProductCatalog,
    private val cache: PriceCache,
    private val calculator: PriceCalculator,
) {
    fun register(product: Product) {
        catalog.put(product)
        cache.invalidate(product.id)
    }

    fun price(request: PriceRequest): ProductPrice {
        val product = catalog.find(request.productId)
            ?: return ProductPrice(request.productId, null)
        val cachedAmount = cache.find(request)
        if (cachedAmount != null) {
            return ProductPrice(product.id, cachedAmount)
        }
        val amount = calculator.calculate(product, request)
        cache.store(request, product, amount)
        return ProductPrice(product.id, amount)
    }

    fun list(command: ListPrices): String {
        val prices = command.productIds.map { productId ->
            price(PriceRequest(productId, command.tier, command.coupon))
        }
        return prices.joinToString(separator = " ", prefix = "PRICES ") { it.listItem() }
    }

    fun clear(productId: String) {
        cache.invalidate(productId)
    }
}
