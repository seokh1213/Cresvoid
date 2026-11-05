import java.time.Instant

sealed interface LimitCommand {
    val occurredAt: Instant
    val userId: String
}

data class RegisterAccount(
    override val occurredAt: Instant,
    override val userId: String,
    val dailyLimit: Long,
) : LimitCommand

data class UseAllowance(
    override val occurredAt: Instant,
    override val userId: String,
    val usageId: String,
    val amount: Long,
) : LimitCommand

data class CancelUsage(
    override val occurredAt: Instant,
    override val userId: String,
    val usageId: String,
) : LimitCommand

data class InspectAllowance(
    override val occurredAt: Instant,
    override val userId: String,
) : LimitCommand

class CommandReader {
    fun read(line: String): LimitCommand {
        val fields = line.trim().split(Regex("\\s+"))
        val occurredAt = Instant.parse(fields[1])
        val userId = fields[2]
        return when (fields[0]) {
            "REGISTER" -> RegisterAccount(
                occurredAt = occurredAt,
                userId = userId,
                dailyLimit = fields[3].toLong(),
            )
            "USE" -> UseAllowance(
                occurredAt = occurredAt,
                userId = userId,
                usageId = fields[3],
                amount = fields[4].toLong(),
            )
            "CANCEL" -> CancelUsage(
                occurredAt = occurredAt,
                userId = userId,
                usageId = fields[3],
            )
            "LIMIT" -> InspectAllowance(
                occurredAt = occurredAt,
                userId = userId,
            )
            else -> error("Unsupported command")
        }
    }
}

sealed interface LimitReply {
    fun render(): String

    data class Registered(val account: LimitAccount) : LimitReply {
        override fun render(): String =
            "REGISTERED ${account.userId} ${account.dailyLimit}"
    }

    data class Used(val usageId: String, val remaining: Long) : LimitReply {
        override fun render(): String = "USED $usageId $remaining"
    }

    data class Canceled(val usageId: String, val remaining: Long) : LimitReply {
        override fun render(): String = "CANCELED $usageId $remaining"
    }

    data class Inspected(
        val userId: String,
        val balance: AllowanceBalance,
    ) : LimitReply {
        override fun render(): String =
            "LIMIT $userId ${balance.used} ${balance.remaining}"
    }
}

enum class LimitRejection : LimitReply {
    USER_EXISTS,
    UNKNOWN_USER,
    DUPLICATE_USE,
    LIMIT_EXCEEDED,
    UNKNOWN_USE,
    ALREADY_CANCELED;

    override fun render(): String = "ERROR $name"
}
