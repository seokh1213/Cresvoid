import java.time.LocalDate

data class LimitAccount(
    val userId: String,
    val dailyLimit: Long,
)

data class UsageKey(
    val userId: String,
    val usageId: String,
)

data class UsageDay(
    val userId: String,
    val date: LocalDate,
)

data class UsageReceipt(
    val key: UsageKey,
    val bookedOn: LocalDate,
    val amount: Long,
    val canceled: Boolean = false,
)

data class AllowanceBalance(
    val dailyLimit: Long,
    val used: Long,
) {
    val remaining: Long
        get() = dailyLimit - used
}

class UsageLedger {
    private val accounts = mutableMapOf<String, LimitAccount>()
    private val receipts = mutableMapOf<UsageKey, UsageReceipt>()
    private val dailyTotals = mutableMapOf<UsageDay, Long>()

    fun findAccount(userId: String): LimitAccount? = accounts[userId]

    fun register(account: LimitAccount) {
        check(account.userId !in accounts)
        accounts[account.userId] = account
    }

    fun findReceipt(key: UsageKey): UsageReceipt? = receipts[key]

    fun balance(account: LimitAccount, date: LocalDate): AllowanceBalance {
        val day = UsageDay(account.userId, date)
        return AllowanceBalance(
            dailyLimit = account.dailyLimit,
            used = dailyTotals[day] ?: 0L,
        )
    }

    fun recordUsage(receipt: UsageReceipt) {
        check(receipt.key !in receipts)
        check(!receipt.canceled)
        val day = UsageDay(receipt.key.userId, receipt.bookedOn)
        val previousTotal = dailyTotals[day] ?: 0L
        dailyTotals[day] = previousTotal + receipt.amount
        receipts[receipt.key] = receipt
    }

    fun cancelUsage(receipt: UsageReceipt) {
        check(receipts[receipt.key] == receipt)
        check(!receipt.canceled)
        val day = UsageDay(receipt.key.userId, receipt.bookedOn)
        val previousTotal = dailyTotals.getValue(day)
        check(previousTotal >= receipt.amount)
        dailyTotals[day] = previousTotal - receipt.amount
        receipts[receipt.key] = receipt.copy(canceled = true)
    }
}
