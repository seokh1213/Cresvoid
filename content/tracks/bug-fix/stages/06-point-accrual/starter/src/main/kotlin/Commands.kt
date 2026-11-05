import java.time.LocalDate
import java.time.format.DateTimeParseException

internal enum class Tier { BASIC, SILVER, GOLD }

internal sealed interface Command {
    data class SetRate(val tier: Tier, val rate: PointRate) : Command
    data class SetEvent(val promotion: Promotion) : Command
    data class Accrue(val payment: Payment) : Command
    data class Receipt(val paymentId: String) : Command
    data class Day(val date: LocalDate) : Command
}

internal data class Payment(
    val id: String,
    val date: LocalDate,
    val tier: Tier,
    val amount: Long,
)

internal object CommandParser {
    private val identifier = Regex("[A-Za-z0-9_-]{1,40}")
    private val dateFormat = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val amountFormat = Regex("[0-9]+")

    fun parse(line: String): Command? {
        val tokens = line.trim().split(Regex("\\s+"))
        return when (tokens.first()) {
            "RATE" -> parseRate(tokens)
            "EVENT" -> parseEvent(tokens)
            "ACCRUE" -> parsePayment(tokens)
            "RECEIPT" -> {
                if (tokens.size != 2 || !identifier.matches(tokens[1])) return null
                Command.Receipt(tokens[1])
            }
            "DAY" -> {
                if (tokens.size != 2) return null
                Command.Day(parseDate(tokens[1]) ?: return null)
            }
            else -> null
        }
    }

    private fun parseRate(tokens: List<String>): Command? {
        if (tokens.size != 3) return null
        val tier = parseTier(tokens[1]) ?: return null
        val rate = PointRate.parse(tokens[2]) ?: return null
        return Command.SetRate(tier, rate)
    }

    private fun parseEvent(tokens: List<String>): Command? {
        if (tokens.size != 5 || !identifier.matches(tokens[1])) return null
        val start = parseDate(tokens[2]) ?: return null
        val end = parseDate(tokens[3]) ?: return null
        val bonus = PointRate.parse(tokens[4]) ?: return null
        if (end < start) return null
        return Command.SetEvent(Promotion(tokens[1], start, end, bonus))
    }

    private fun parsePayment(tokens: List<String>): Command? {
        if (tokens.size != 5 || !identifier.matches(tokens[1])) return null
        val date = parseDate(tokens[2]) ?: return null
        val tier = parseTier(tokens[3]) ?: return null
        if (!amountFormat.matches(tokens[4])) return null
        val amount = tokens[4].toLongOrNull() ?: return null
        if (amount !in 0L..1_000_000_000_000L) return null
        return Command.Accrue(Payment(tokens[1], date, tier, amount))
    }

    private fun parseTier(token: String): Tier? =
        Tier.entries.firstOrNull { it.name == token }

    private fun parseDate(token: String): LocalDate? {
        if (!dateFormat.matches(token)) return null
        val date = try {
            LocalDate.parse(token)
        } catch (_: DateTimeParseException) {
            return null
        }
        return date.takeIf { it.year in 2000..2099 }
    }
}
