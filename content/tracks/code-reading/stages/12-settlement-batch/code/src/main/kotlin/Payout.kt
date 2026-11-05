import java.time.LocalDate

data class ScheduleRequest(
    val anchor: LocalDate,
    val monthCount: Int,
    val skipMonths: Int,
    val banks: List<String>,
) {
    init {
        require(monthCount in 0..24)
        require(skipMonths in 0..24)
        require(banks.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_-]*")) })
    }
}

data class PaymentWindow(val date: LocalDate, val bank: String)

data class PlannedPayment(
    val key: SettlementKey,
    val amount: Long,
    val window: PaymentWindow,
)

data class PayoutPreview(
    val candidates: Int,
    val windows: Int,
    val payments: List<PlannedPayment>,
)

class PayoutPlanner {
    fun preview(
        settlements: Map<SettlementKey, SettlementTotals>,
        request: ScheduleRequest,
    ): PayoutPreview {
        val windows = paymentWindows(request)
        val candidates = settlements.entries.filter { it.value.payable > 0 }
        val payments = candidates.zip(windows) { settlement, window ->
            PlannedPayment(settlement.key, settlement.value.payable, window)
        }
        return PayoutPreview(candidates.size, windows.size, payments)
    }

    private fun paymentWindows(request: ScheduleRequest): List<PaymentWindow> {
        val dates = monthlyDates(request)
        return dates.zip(request.banks) { date, bank -> PaymentWindow(date, bank) }
    }

    private fun monthlyDates(request: ScheduleRequest): List<LocalDate> {
        var scheduledOn = request.anchor
        val dates = mutableListOf<LocalDate>()
        repeat(request.skipMonths + request.monthCount) { index ->
            scheduledOn = scheduledOn.plusMonths(1)
            if (index >= request.skipMonths) dates.add(scheduledOn)
        }
        return dates
    }

    fun highestPayment(
        settlements: Map<SettlementKey, SettlementTotals>,
    ): Map.Entry<SettlementKey, SettlementTotals>? =
        settlements.entries.maxByOrNull { it.value.payable }
}
