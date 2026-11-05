class RolloverPlan(
    val sourceRevision: Int,
    val cutoff: Int,
    private val snapshots: Map<String, Map<String, String>>,
) {
    private val receipts = mutableMapOf<String, Boolean>()
    var applied: Boolean = false
        private set

    val size: Int get() = snapshots.size

    fun recordLoad(key: String, outcome: String): String {
        check(!applied)
        require(key in snapshots)
        require(outcome == "OK" || outcome == "FAIL")
        receipts[key] = outcome == "OK"
        return if (outcome == "OK") "LOADED" else "FAILED"
    }

    fun confirmedSnapshots(): Map<String, Map<String, String>> =
        snapshots.filterKeys { receipts[it] == true }

    fun markApplied() {
        applied = true
    }
}

class RolloverPlanner(private val versions: CatalogVersions) {
    private val plans = mutableMapOf<String, RolloverPlan>()

    fun create(id: String, from: Int, cutoff: Int): String {
        require(id !in plans)
        require(from >= versions.latestBaselineTime() && from < cutoff)
        val snapshots = versions.changedKeys(from, cutoff).associateWith { key ->
            versions.stateBefore(key, cutoff)
        }
        val plan = RolloverPlan(versions.revision, cutoff, snapshots)
        plans[id] = plan
        return "PLANNED ${plan.size}"
    }

    fun find(id: String): RolloverPlan = requireNotNull(plans[id])
}
