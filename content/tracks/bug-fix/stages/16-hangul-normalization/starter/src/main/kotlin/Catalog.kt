data class CatalogProduct(
    val id: Int,
    val title: String,
    val terms: Set<String>,
)

class ProductCatalog {
    private val products = mutableMapOf<Int, CatalogProduct>()
    private val postings = mutableMapOf<String, MutableSet<Int>>()

    fun put(id: Int, title: String) {
        val terms = SearchText.terms(title)
        val replacement = CatalogProduct(id, title, terms)
        products[id]?.let { removePostings(it) }
        products[id] = replacement
        addPostings(replacement)
    }

    fun remove(id: Int): Boolean {
        val product = products.remove(id) ?: return false
        removePostings(product)
        return true
    }

    fun list(): List<CatalogProduct> =
        products.values.sortedBy { it.id }

    fun search(query: SearchQuery): List<Int> {
        val matchingPostings = query.terms.map { term ->
            postings[term] ?: return emptyList()
        }
        val smallestPosting = matchingPostings.minByOrNull { it.size }
            ?: return emptyList()
        return smallestPosting
            .filter { id -> matchingPostings.all { id in it } }
            .sorted()
    }

    private fun addPostings(product: CatalogProduct) {
        for (term in product.terms) {
            postings.getOrPut(term) { mutableSetOf() }.add(product.id)
        }
    }

    private fun removePostings(product: CatalogProduct) {
        for (term in product.terms) {
            val members = postings[term] ?: continue
            members.remove(product.id)
            if (members.isEmpty()) {
                postings.remove(term)
            }
        }
    }
}
