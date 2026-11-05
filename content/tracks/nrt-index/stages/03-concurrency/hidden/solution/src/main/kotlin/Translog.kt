class Translog {
    var mode = DurabilityMode.REQUEST
    private var generation = 1
    private val records = mutableListOf<VersionedMutation>()
    private var synced = 0

    fun append(record: VersionedMutation) {
        records.add(record)
        if (mode == DurabilityMode.REQUEST) sync()
    }

    fun sync() {
        synced = records.size
    }

    fun newGeneration() {
        records.clear()
        synced = 0
        generation++
    }

    fun recover(): List<VersionedMutation> {
        records.subList(synced, records.size).clear()
        return records.toList()
    }

    fun describe(): String = "generation=$generation total=${records.size} synced=$synced"
}
