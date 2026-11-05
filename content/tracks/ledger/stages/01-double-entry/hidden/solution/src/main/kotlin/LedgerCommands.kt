class LedgerCommands(private val ledger: Ledger) {
    fun execute(tokens: List<String>): List<String>? = when (tokens[0]) {
        "OPEN" -> listOf(ledger.open(tokens[1], AccountTerms(tokens[2], Side.valueOf(tokens[3]), BalancePolicy.valueOf(tokens[4]))))
        "POST" -> listOf(ledger.post(tokens[1], parseJournal(tokens)))
        "BALANCE" -> listOf(ledger.balance(tokens[1]))
        "JOURNAL" -> ledger.journal(tokens[1])
        "TRIAL" -> listOf(ledger.trial(tokens[1]))
        else -> null
    }

    private fun parseJournal(tokens: List<String>): Journal {
        val entries = List(tokens[3].toInt()) { index ->
            val offset = 4 + index * 3
            Entry(tokens[offset], Side.valueOf(tokens[offset + 1]), tokens[offset + 2].toLong())
        }
        return Journal(tokens[2], entries)
    }
}
