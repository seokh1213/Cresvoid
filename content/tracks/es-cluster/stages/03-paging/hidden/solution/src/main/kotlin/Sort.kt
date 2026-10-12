private const val SHARD_DOC_MULTIPLIER = 4_294_967_296L

enum class SortDirection(val parameterValue: String) {
    ASC("asc"),
    DESC("desc"),
    ;

    fun orient(comparison: Int): Int = if (this == ASC) comparison else -comparison

    companion object {
        fun find(parameterValue: String): SortDirection? = entries.firstOrNull { it.parameterValue == parameterValue }
    }
}

enum class SortField(val parameterValue: String, val defaultDirection: SortDirection) {
    SCORE("_score", SortDirection.DESC),
    PRICE("price", SortDirection.ASC),
    ID("id", SortDirection.ASC),
    SHARD_DOC("_shard_doc", SortDirection.ASC),
    ;

    fun sortValue(hit: Hit): Long =
        when (this) {
            SCORE -> hit.score.tenThousandths
            PRICE -> hit.document.price.toLong()
            ID -> hit.document.id.toLong()
            SHARD_DOC -> hit.shardNumber * SHARD_DOC_MULTIPLIER + hit.document.internalNumber
        }

    fun format(hit: Hit): String = if (this == SCORE) hit.score.toString() else sortValue(hit).toString()

    companion object {
        fun find(parameterValue: String): SortField? = entries.firstOrNull { it.parameterValue == parameterValue }
    }
}

data class SortKey(val field: SortField, val direction: SortDirection)

class SortOrder(private val keys: List<SortKey>) : Comparator<Hit> {

    override fun compare(left: Hit, right: Hit): Int {
        for (key in keys) {
            val comparison = key.field.sortValue(left).compareTo(key.field.sortValue(right))
            if (comparison != 0) return key.direction.orient(comparison)
        }
        if (left.shardNumber != right.shardNumber) return left.shardNumber.compareTo(right.shardNumber)
        return left.document.internalNumber.compareTo(right.document.internalNumber)
    }

    fun isAfter(hit: Hit, searchAfter: List<Long>): Boolean {
        for ((position, key) in keys.withIndex()) {
            val comparison = key.field.sortValue(hit).compareTo(searchAfter[position])
            if (comparison != 0) return key.direction.orient(comparison) > 0
        }
        return false
    }
}
