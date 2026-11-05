object BatchOutput {
    fun report(settlements: Map<SettlementKey, SettlementTotals>): String =
        settlements.entries.joinToString(";") { (key, totals) ->
            "${label(key)}=${totals.rowCount},${totals.gross},${totals.fee},${totals.payable}"
        }.ifEmpty { "(none)" }

    fun audit(audit: LegacyAudit): String =
        "${audit.seller} exact=${audit.exactGross}" +
            " legacy=${audit.legacyGross} wire=${audit.wireCheck}"

    fun preview(preview: PayoutPreview): String {
        val payments = preview.payments.joinToString(";") { payment ->
            "${label(payment.key)}=${payment.amount}" +
                "@${payment.window.date}#${payment.window.bank}"
        }.ifEmpty { "(none)" }
        return "candidates=${preview.candidates} windows=${preview.windows} $payments"
    }

    fun top(settlement: Map.Entry<SettlementKey, SettlementTotals>?): String {
        if (settlement == null) return "(none)"
        return "${label(settlement.key)}=${settlement.value.payable}"
    }

    private fun label(key: SettlementKey): String = "${key.seller}/${key.month}"
}
