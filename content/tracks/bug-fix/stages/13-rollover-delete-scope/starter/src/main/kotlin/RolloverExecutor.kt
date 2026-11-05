class RolloverExecutor(private val versions: CatalogVersions) {
    fun apply(plan: RolloverPlan): String {
        if (plan.applied) return "ALREADY_APPLIED"
        if (plan.sourceRevision != versions.revision) return "STALE"

        val confirmed = plan.confirmedSnapshots()
        versions.install(plan.cutoff, confirmed)
        val deletionKeys = versions.keys()
        versions.deleteBefore(deletionKeys, plan.cutoff)
        versions.finishRollover()
        plan.markApplied()
        return "APPLIED ${confirmed.size}"
    }
}
