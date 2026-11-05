fun summaryQuery(tenant: Long, state: String): String {
    val stateFilter = if (state == "all") "" else "AND state = '$state'"
    return """
        SELECT count(), coalesce(sum(latest), 0)
        FROM (
            SELECT id, argMax(amount, version) AS latest
            FROM lab.events
            WHERE tenant = $tenant $stateFilter
            GROUP BY id
        )
        FORMAT TabSeparated
    """.trimIndent()
}
