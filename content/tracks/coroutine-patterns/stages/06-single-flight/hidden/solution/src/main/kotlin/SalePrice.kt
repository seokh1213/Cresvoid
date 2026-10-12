import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.scan

data class PriceBook(val listPrice: Int?, val discountPercent: Int, val appliedVersion: Long) {
    val salePrice: Int?
        get() = listPrice?.let { price -> (price * (100 - discountPercent) / 100).floorToTens() }

    fun after(event: PriceEvent): PriceBook {
        if (event.version <= appliedVersion) return this
        return when (val change = event.change) {
            is PriceChange.ListPrice -> copy(listPrice = change.price, appliedVersion = event.version)
            is PriceChange.Sale -> copy(discountPercent = change.percent, appliedVersion = event.version)
        }
    }

    companion object {
        val EMPTY = PriceBook(listPrice = null, discountPercent = 0, appliedVersion = 0)
    }
}

fun Flow<PriceEvent>.salePriceChanges(product: String): Flow<Int> =
    filter { it.product == product }
        .scan(PriceBook.EMPTY) { book, event -> book.after(event) }
        .mapNotNull { it.salePrice }
        .distinctUntilChanged()

private fun Int.floorToTens(): Int = this / 10 * 10
