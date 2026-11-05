data class RecordPosition(
    val round: Int,
    val sequence: Long,
) : Comparable<RecordPosition> {
    override fun compareTo(other: RecordPosition): Int {
        val roundOrder = round.compareTo(other.round)
        return if (roundOrder != 0) roundOrder else sequence.compareTo(other.sequence)
    }
}

sealed interface CatalogRevision {
    data class Content(val value: String) : CatalogRevision
    data object Deleted : CatalogRevision
}

data class CatalogChange(
    val source: CatalogSource,
    val position: RecordPosition,
    val key: String,
    val revision: CatalogRevision,
)

class ChangeHistory {
    private val records = mutableListOf<CatalogChange>()

    fun append(record: CatalogChange) {
        records.add(record)
    }

    fun changedKeys(round: Int): List<String> {
        val changedKeys = records.asSequence()
            .filter { it.position.round == round }
            .map { it.key }
            .toSet()
        return changedKeys.sorted()
    }

    fun latestAt(key: String, source: CatalogSource, round: Int): CatalogChange? {
        return records.asSequence()
            .filter { it.key == key && it.source == source }
            .filter { it.position.round <= round }
            .maxByOrNull { it.position }
    }
}
