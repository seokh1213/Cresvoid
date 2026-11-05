enum class Side {
    D, C;

    fun signed(amount: Long): Long = if (this == D) amount else -amount
}

enum class BalancePolicy { NONNEG, ANY }

data class AccountTerms(val currency: String, val normal: Side, val policy: BalancePolicy)

class Account(val terms: AccountTerms) {
    var rawBalance: Long = 0
        private set

    val balance: Long get() = terms.normal.signed(rawBalance)

    fun apply(change: Long) {
        rawBalance += change
    }
}

data class Entry(val account: String, val side: Side, val amount: Long)
data class Journal(val currency: String, val entries: List<Entry>)
