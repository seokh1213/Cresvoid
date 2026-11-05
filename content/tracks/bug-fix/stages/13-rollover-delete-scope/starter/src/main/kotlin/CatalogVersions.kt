data class FieldChange(
    val time: Int,
    val sequence: Int,
    val field: String,
    val value: String?,
)

data class CatalogBaseline(val time: Int, val fields: Map<String, String>)

fun renderFields(fields: Map<String, String>): String =
    fields.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }.ifEmpty { "EMPTY" }

class CatalogVersions {
    private val changes = mutableMapOf<String, MutableList<FieldChange>>()
    private val baselines = mutableMapOf<String, CatalogBaseline>()
    private var sequence = 0
    var revision: Int = 0
        private set

    fun append(key: String, time: Int, field: String, value: String?) {
        require(time >= (baselines[key]?.time ?: 0))
        sequence += 1
        changes.getOrPut(key) { mutableListOf() }.add(FieldChange(time, sequence, field, value))
        revision += 1
    }

    fun keys(): Set<String> = baselines.keys + changes.keys

    fun latestBaselineTime(): Int = baselines.values.maxOfOrNull { it.time } ?: 0

    fun changedKeys(from: Int, until: Int): Set<String> = changes
        .filterValues { history -> history.any { it.time >= from && it.time < until } }
        .keys.toSet()

    fun stateBefore(key: String, cutoff: Int): Map<String, String> =
        foldFields(key, orderedChanges(key).filter { it.time < cutoff })

    fun read(key: String, time: Int): String {
        val baseline = baselines[key]
        if (baseline != null && time < baseline.time) return "EXPIRED"
        return renderFields(foldFields(key, orderedChanges(key).filter { it.time <= time }))
    }

    fun history(key: String): String {
        val records = mutableListOf<String>()
        baselines[key]?.let { baseline ->
            records.add("B@${baseline.time}{${renderFields(baseline.fields)}}")
        }
        for (change in orderedChanges(key)) {
            val position = "${change.time}#${change.sequence}"
            val record = if (change.value == null) {
                "D@$position{${change.field}}"
            } else {
                "P@$position{${change.field}=${change.value}}"
            }
            records.add(record)
        }
        return records.joinToString(";").ifEmpty { "NONE" }
    }

    fun install(cutoff: Int, snapshots: Map<String, Map<String, String>>) {
        for ((key, fields) in snapshots) {
            baselines[key] = CatalogBaseline(cutoff, fields.toMap())
        }
    }

    fun deleteBefore(keys: Set<String>, cutoff: Int) {
        for (key in keys) {
            changes[key]?.removeAll { it.time < cutoff }
            if (changes[key]?.isEmpty() == true) changes.remove(key)
            val baseline = baselines[key]
            if (baseline != null && baseline.time < cutoff) baselines.remove(key)
        }
    }

    fun finishRollover() {
        revision += 1
    }

    private fun orderedChanges(key: String): List<FieldChange> = changes[key].orEmpty()
        .sortedWith(compareBy<FieldChange> { it.time }.thenBy { it.sequence })

    private fun foldFields(key: String, history: List<FieldChange>): Map<String, String> {
        val fields = baselines[key]?.fields.orEmpty().toMutableMap()
        for (change in history) {
            if (change.value == null) fields.remove(change.field)
            else fields[change.field] = change.value
        }
        return fields.toMap()
    }
}
