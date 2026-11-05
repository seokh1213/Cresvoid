import java.util.concurrent.ConcurrentLinkedQueue

data class SettlementBatch(val day: String, val sales: Long, val amount: Long)

data class DailySettlement(val runs: Long, val sales: Long, val amount: Long) {
    fun append(batch: SettlementBatch): DailySettlement = DailySettlement(
        runs = runs + 1,
        sales = sales + batch.sales,
        amount = amount + batch.amount,
    )
}

class SalesJournal {
    private val salesByDay = mutableMapOf<String, MutableList<Long>>()

    fun record(day: String, amount: Long) {
        salesByDay.getOrPut(day) { mutableListOf() }.add(amount)
    }

    fun snapshot(day: String): SettlementBatch {
        val sales = salesByDay[day].orEmpty()
        return SettlementBatch(day, sales.size.toLong(), sales.sum())
    }
}

class SettlementLedger {
    private val totals = mutableMapOf<String, DailySettlement>()

    fun post(batch: SettlementBatch) {
        totals[batch.day] = report(batch.day).append(batch)
    }

    fun report(day: String): DailySettlement =
        totals[day] ?: DailySettlement(0, 0, 0)
}

data class Completion(val batches: Int, val amount: Long)

class SettlementJob(
    private val state: RunStateStore,
    private val journal: SalesJournal,
    private val ledger: SettlementLedger,
) {
    private val pending = ConcurrentLinkedQueue<SettlementBatch>()

    fun start(day: String): Boolean {
        if (!state.begin()) return false
        pending.add(journal.snapshot(day))
        return true
    }

    fun finish(): Completion {
        val batches = drainPending()
        batches.forEach(ledger::post)
        state.end()
        return Completion(batches.size, batches.sumOf { it.amount })
    }

    fun abort(): Int {
        val discarded = drainPending().size
        state.end()
        return discarded
    }

    fun status(): String {
        val label = if (state.isRunning()) "RUNNING" else "IDLE"
        return "STATE $label pending=${pending.size}"
    }

    private fun drainPending(): List<SettlementBatch> = buildList {
        while (true) {
            val batch = pending.poll() ?: break
            add(batch)
        }
    }
}
