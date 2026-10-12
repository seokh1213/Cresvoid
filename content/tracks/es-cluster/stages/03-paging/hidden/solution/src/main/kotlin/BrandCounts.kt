import java.util.TreeSet

data class BrandBucket(val brand: String, val count: Int)

val BUCKET_ORDER: Comparator<BrandBucket> = compareByDescending<BrandBucket> { it.count }.thenBy { it.brand }

class BrandCounts {
    private val countByBrand = HashMap<String, Int>()
    private val ranking = TreeSet(BUCKET_ORDER)

    fun count(brand: String): Int = countByBrand[brand] ?: 0

    fun top(limit: Int): List<BrandBucket> = ranking.asSequence().take(limit).toList()

    fun increment(brand: String) = change(brand, 1)

    fun decrement(brand: String) = change(brand, -1)

    private fun change(brand: String, difference: Int) {
        val before = count(brand)
        if (before > 0) ranking.remove(BrandBucket(brand, before))
        val after = before + difference
        if (after > 0) {
            countByBrand[brand] = after
            ranking.add(BrandBucket(brand, after))
        } else {
            countByBrand.remove(brand)
        }
    }
}
