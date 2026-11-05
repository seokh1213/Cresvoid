data class SourceSelection(
    val live: CatalogChange?,
    val trial: CatalogChange?,
) {
    fun preferred(): CatalogChange {
        return checkNotNull(trial ?: live) { "A changed key must have a source record" }
    }
}

data class MergedProduct(
    val key: String,
    val source: CatalogSource,
    val position: RecordPosition,
    val revision: CatalogRevision,
)

class CatalogMerger(private val history: ChangeHistory) {
    fun mergeAt(key: String, round: Int): MergedProduct {
        val sources = SourceSelection(
            live = history.latestAt(key, CatalogSource.LIVE, round),
            trial = history.latestAt(key, CatalogSource.TRIAL, round),
        )
        val selected = sources.preferred()
        return MergedProduct(
            key = key,
            source = selected.source,
            position = selected.position,
            revision = selected.revision,
        )
    }
}
