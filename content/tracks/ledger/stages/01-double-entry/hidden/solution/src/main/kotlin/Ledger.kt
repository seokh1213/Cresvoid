class Ledger {
    private val accounts = mutableMapOf<String, Account>()
    private val journals = mutableMapOf<String, Journal>()

    fun account(id: String): Account? = accounts[id]

    fun open(id: String, terms: AccountTerms): String {
        if (id in accounts) return "ERR ACCOUNT_EXISTS"
        accounts[id] = Account(terms)
        return "OK OPEN $id"
    }

    fun post(id: String, journal: Journal): String {
        val existing = journals[id]
        if (existing != null) {
            return if (existing == journal) "OK DUP $id" else "ERR JOURNAL_CONFLICT"
        }
        val error = validate(journal)
        if (error != null) return "ERR $error"
        val changes = changes(journal)
        if (changes.any { (id, change) -> violatesBalance(accounts.getValue(id), change) }) {
            return "ERR INSUFFICIENT"
        }
        changes.forEach { (id, change) -> accounts.getValue(id).apply(change) }
        journals[id] = journal
        return "OK POST $id"
    }

    private fun validate(journal: Journal): String? {
        val entries = journal.entries
        return when {
            entries.any { it.amount <= 0 } -> "AMOUNT"
            entries.any { it.account !in accounts } -> "NOT_FOUND"
            entries.any { accounts.getValue(it.account).terms.currency != journal.currency } -> "CURRENCY"
            entries.sumOf { it.side.signed(it.amount) } != 0L -> "UNBALANCED"
            else -> null
        }
    }

    private fun changes(journal: Journal): Map<String, Long> {
        val changes = mutableMapOf<String, Long>()
        for (entry in journal.entries) {
            changes[entry.account] = changes.getOrDefault(entry.account, 0L) + entry.side.signed(entry.amount)
        }
        return changes
    }

    private fun violatesBalance(account: Account, change: Long): Boolean =
        account.terms.policy == BalancePolicy.NONNEG && account.terms.normal.signed(account.rawBalance + change) < 0

    fun balance(id: String): String {
        val account = accounts[id] ?: return "ERR NOT_FOUND"
        return "BALANCE $id ${account.terms.currency} ${account.balance}"
    }

    fun journal(id: String): List<String> {
        val journal = journals[id] ?: return listOf("ERR NOT_FOUND")
        return listOf("JOURNAL $id ${journal.currency} ${journal.entries.size}") + journal.entries.map {
            "ENTRY ${it.account} ${it.side} ${it.amount}"
        }
    }

    fun trial(currency: String): String {
        var debit = 0L
        var credit = 0L
        for (account in accounts.values) {
            if (account.terms.currency != currency) continue
            if (account.rawBalance > 0) debit += account.rawBalance else credit -= account.rawBalance
        }
        return "TRIAL $currency $debit $credit ${debit - credit}"
    }
}
