data class DeliveryBatch(
    val round: Int,
    val products: List<MergedProduct>,
) {
    fun render(): List<String> = buildList {
        add("BATCH $round ${products.size}")
        for (product in products) {
            add(product.render())
        }
        add("END $round")
    }
}

private fun MergedProduct.render(): String {
    val origin = "$source ${position.round} ${position.sequence}"
    return when (val selected = revision) {
        is CatalogRevision.Content -> "PUT $key ${selected.value} $origin"
        CatalogRevision.Deleted -> "DELETE $key $origin"
    }
}

class DeliveryPlanner(
    private val history: ChangeHistory,
    private val merger: CatalogMerger,
) {
    fun plan(round: Int): DeliveryBatch {
        val keys = history.changedKeys(round)
        val products = keys.map { key -> merger.mergeAt(key, round) }
        return DeliveryBatch(round, products)
    }
}
