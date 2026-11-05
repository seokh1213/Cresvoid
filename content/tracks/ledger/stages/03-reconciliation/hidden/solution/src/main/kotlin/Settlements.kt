class Settlements(private val payments: Payments) {
    private val files = mutableMapOf<String, List<SettlementRow>>()

    fun import(id: String, rows: List<SettlementRow>): String {
        val existing = files[id]
        if (existing != null) return if (existing == rows) "OK DUP $id" else "ERR FILE_CONFLICT"
        files[id] = rows
        return "OK IMPORT $id"
    }

    fun reconcile(id: String): List<String> {
        val rows = files[id] ?: return listOf("ERR NOT_FOUND")
        val local = payments.successful().mapValues { (_, details) ->
            val amount = when (details) {
                is RequestDetails.Pay -> details.amount
                is RequestDetails.Refund -> -details.amount
            }
            SettlementValue(details.currency, amount)
        }
        val groupedRows = rows.groupBy { it.key }
        val keys = (local.keys + groupedRows.keys).sorted()
        val discrepancies = keys.mapNotNull { key ->
            classify(local[key], groupedRows[key].orEmpty())?.render(key)
        }
        val localTotals = mutableMapOf<String, Long>()
        val remoteTotals = mutableMapOf<String, Long>()
        local.values.forEach { localTotals.add(it.currency, it.amount) }
        rows.forEach { remoteTotals.add(it.currency, it.amount) }
        val currencies = (localTotals.keys + remoteTotals.keys).sorted()
        return buildList {
            add("REPORT $id ${discrepancies.size} ${currencies.size}")
            addAll(discrepancies)
            for (currency in currencies) {
                val expected = localTotals.getOrDefault(currency, 0L)
                val actual = remoteTotals.getOrDefault(currency, 0L)
                add("TOTAL $currency $expected $actual ${actual - expected}")
            }
        }
    }

    private fun classify(expected: SettlementValue?, rows: List<SettlementRow>): Discrepancy? {
        if (rows.size >= 2) return Discrepancy.Duplicate(rows.size)
        val row = rows.firstOrNull()
        val actual = row?.let { SettlementValue(it.currency, it.amount) }
        return when {
            expected == null && actual != null -> Discrepancy.MissingLocal(actual)
            expected != null && actual == null -> Discrepancy.MissingRemote(expected)
            expected == null || actual == null -> null
            expected.currency != actual.currency -> Discrepancy.CurrencyMismatch(expected, actual)
            expected.amount != actual.amount -> Discrepancy.AmountMismatch(expected, actual)
            else -> null
        }
    }

    private fun MutableMap<String, Long>.add(currency: String, amount: Long) {
        this[currency] = getOrDefault(currency, 0L) + amount
    }
}
