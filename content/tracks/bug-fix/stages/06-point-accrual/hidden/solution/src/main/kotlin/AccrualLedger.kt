import java.time.LocalDate

internal data class Accrual(
    val payment: Payment,
    val rate: PointRate,
    val points: Long,
) {
    fun receiptLine(): String = listOf(
        "RECEIPT",
        payment.id,
        payment.date,
        payment.tier,
        payment.amount,
        rate.format(),
        points,
    ).joinToString(" ")
}

internal data class DailySettlement(
    val date: LocalDate,
    val count: Int,
    val amount: Long,
    val points: Long,
) {
    fun outputLine(): String = "DAY $date $count $amount $points"
}

internal class AccrualLedger(private val policy: PointPolicy) {
    private val accruals = mutableMapOf<String, Accrual>()

    fun accrue(payment: Payment): String {
        if (payment.id in accruals) return "ERROR duplicate"
        val rate = policy.rateFor(payment)
        val points = rate.pointsFor(payment.amount)
        val accrual = Accrual(payment, rate, points)
        accruals[payment.id] = accrual
        return "ACCRUED ${payment.id} $points"
    }

    fun receipt(paymentId: String): String =
        accruals[paymentId]?.receiptLine() ?: "NONE"

    fun settlement(date: LocalDate): DailySettlement {
        val dailyAccruals = accruals.values.filter { it.payment.date == date }
        return DailySettlement(
            date = date,
            count = dailyAccruals.size,
            amount = dailyAccruals.sumOf { it.payment.amount },
            points = dailyAccruals.sumOf { it.points },
        )
    }
}

internal class PointService {
    private val policy = PointPolicy()
    private val ledger = AccrualLedger(policy)

    fun execute(command: Command): String = when (command) {
        is Command.SetRate -> {
            policy.setRate(command.tier, command.rate)
            "OK"
        }
        is Command.SetEvent -> {
            policy.setEvent(command.promotion)
            "OK"
        }
        is Command.Accrue -> ledger.accrue(command.payment)
        is Command.Receipt -> ledger.receipt(command.paymentId)
        is Command.Day -> ledger.settlement(command.date).outputLine()
    }
}
