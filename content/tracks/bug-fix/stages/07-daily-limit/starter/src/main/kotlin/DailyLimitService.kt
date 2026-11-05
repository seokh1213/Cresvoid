class DailyLimitService(
    private val ledger: UsageLedger,
    private val accountingDay: AccountingDay,
) {
    fun execute(command: LimitCommand): LimitReply = when (command) {
        is RegisterAccount -> register(command)
        is UseAllowance -> use(command)
        is CancelUsage -> cancel(command)
        is InspectAllowance -> inspect(command)
    }

    private fun register(command: RegisterAccount): LimitReply {
        if (ledger.findAccount(command.userId) != null) {
            return LimitRejection.USER_EXISTS
        }
        val account = LimitAccount(command.userId, command.dailyLimit)
        ledger.register(account)
        return LimitReply.Registered(account)
    }

    private fun use(command: UseAllowance): LimitReply {
        val account = ledger.findAccount(command.userId)
            ?: return LimitRejection.UNKNOWN_USER
        val key = UsageKey(command.userId, command.usageId)
        if (ledger.findReceipt(key) != null) {
            return LimitRejection.DUPLICATE_USE
        }

        val today = accountingDay.current()
        val balance = ledger.balance(account, today)
        if (command.amount > balance.remaining) {
            return LimitRejection.LIMIT_EXCEEDED
        }

        val receipt = UsageReceipt(
            key = key,
            bookedOn = today,
            amount = command.amount,
        )
        ledger.recordUsage(receipt)
        val remaining = ledger.balance(account, today).remaining
        return LimitReply.Used(command.usageId, remaining)
    }

    private fun cancel(command: CancelUsage): LimitReply {
        val account = ledger.findAccount(command.userId)
            ?: return LimitRejection.UNKNOWN_USER
        val key = UsageKey(command.userId, command.usageId)
        val receipt = ledger.findReceipt(key)
            ?: return LimitRejection.UNKNOWN_USE
        if (receipt.canceled) {
            return LimitRejection.ALREADY_CANCELED
        }

        ledger.cancelUsage(receipt)
        val remaining = ledger.balance(account, accountingDay.current()).remaining
        return LimitReply.Canceled(command.usageId, remaining)
    }

    private fun inspect(command: InspectAllowance): LimitReply {
        val account = ledger.findAccount(command.userId)
            ?: return LimitRejection.UNKNOWN_USER
        val balance = ledger.balance(account, accountingDay.current())
        return LimitReply.Inspected(command.userId, balance)
    }
}
