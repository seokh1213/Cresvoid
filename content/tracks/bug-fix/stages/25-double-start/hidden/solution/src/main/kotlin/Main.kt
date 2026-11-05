fun main() {
    val journal = SalesJournal()
    val ledger = SettlementLedger()
    val state = MemoryRunStateStore()
    val job = SettlementJob(state, journal, ledger)
    val schedulers = SchedulerBatch(state, job)
    val commands = SettlementCommands(journal, ledger, job, schedulers)
    generateSequence(::readlnOrNull)
        .filter { it.isNotBlank() }
        .forEach { println(commands.execute(it.trim().split(Regex("\\s+")))) }
}

private class SettlementCommands(
    private val journal: SalesJournal,
    private val ledger: SettlementLedger,
    private val job: SettlementJob,
    private val schedulers: SchedulerBatch,
) {
    fun execute(tokens: List<String>): String = when (tokens[0]) {
        "SALE" -> recordSale(tokens)
        "START" -> schedulers.start(tokens[1], tokens[2].toInt()).toString()
        "FINISH" -> finish()
        "ABORT" -> "ABORT batches=${job.abort()}"
        "STATE" -> job.status()
        "REPORT" -> report(tokens[1])
        else -> error("Unknown command: ${tokens[0]}")
    }

    private fun recordSale(tokens: List<String>): String {
        val day = tokens[1]
        journal.record(day, tokens[2].toLong())
        return "SALE $day OK"
    }

    private fun finish(): String {
        val completion = job.finish()
        return "FINISH batches=${completion.batches} amount=${completion.amount}"
    }

    private fun report(day: String): String {
        val settled = ledger.report(day)
        return "REPORT $day runs=${settled.runs} sales=${settled.sales} amount=${settled.amount}"
    }
}
