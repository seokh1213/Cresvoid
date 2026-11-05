fun summaryQuery(tenant: Long, state: String): String {
    val stateFilter = if (state == "all") "" else "AND state = '$state'"
    return """
        SELECT count(), coalesce(sum(amount), 0)
        FROM lab.events
        WHERE tenant = $tenant $stateFilter
        FORMAT TabSeparated
    """.trimIndent()
}
