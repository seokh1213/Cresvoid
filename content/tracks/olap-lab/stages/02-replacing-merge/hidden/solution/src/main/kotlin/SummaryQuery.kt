fun summaryQuery(tenant: Long, state: String): String {
    val stateFilter = if (state == "all") "" else "WHERE latest.1 = '$state'"
    return """
        SELECT count(), coalesce(sum(toInt128(latest.2)), 0)
        FROM (
            SELECT id, argMax(tuple(state, amount), version) AS latest
            FROM lab.events
            WHERE tenant = $tenant
            GROUP BY id
        )
        $stateFilter
        FORMAT TabSeparated
    """.trimIndent()
}
