import java.time.LocalDate
import java.time.format.DateTimeParseException

class CommandParser {
    private val identifier = Regex("[A-Za-z][A-Za-z0-9_-]{0,31}")
    private val integer = Regex("-?[0-9]+")
    private val dateShape = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val firstDate = LocalDate.of(2000, 1, 1)
    private val lastDate = LocalDate.of(2099, 12, 31)
    private class InputError : RuntimeException()

    fun parse(line: String): ParsedLine {
        val trimmed = line.trim(' ', '\t')
        if (trimmed.isEmpty()) return ParsedLine.Empty
        val command = try {
            syntax(trimmed.split(Regex("[ \\t]+")))
        } catch (_: InputError) {
            return ParsedLine.Rejected("ERROR INPUT")
        }
        return if (inRange(command)) ParsedLine.Accepted(command)
        else ParsedLine.Rejected("ERROR RANGE")
    }

    private fun syntax(tokens: List<String>): Command {
        fun count(expected: Int) {
            if (tokens.size != expected) throw InputError()
        }
        return when (tokens[0]) {
            "BEGIN" -> {
                count(2)
                Command.Begin(LockMode.entries.firstOrNull { it.name == tokens[1] } ?: throw InputError())
            }
            "TX" -> {
                count(7)
                Command.Tx(id(tokens[1]), Booking(id(tokens[2]), id(tokens[3]), stay(tokens[4], tokens[5]), number(tokens[6])))
            }
            "STEP" -> {
                count(2)
                Command.Step(id(tokens[1]))
            }
            "END" -> {
                count(1)
                Command.End
            }
            "REQUEST" -> {
                if (tokens.size < 3 || tokens[2] !in setOf("BOOK", "CANCEL")) throw InputError()
                val requestId = id(tokens[1])
                val operation = syntax(tokens.drop(2)) as? Command.Mutation ?: throw InputError()
                Command.Request(requestId, operation)
            }
            "TYPE" -> {
                count(3)
                Command.Type(id(tokens[1]), number(tokens[2]))
            }
            "STOCK" -> {
                count(5)
                Command.Stock(id(tokens[1]), stay(tokens[2], tokens[3]), number(tokens[4]))
            }
            "AVAIL" -> {
                count(3)
                Command.Avail(id(tokens[1]), date(tokens[2]))
            }
            "BOOK" -> {
                count(6)
                Command.Book(Booking(id(tokens[1]), id(tokens[2]), stay(tokens[3], tokens[4]), number(tokens[5])))
            }
            "CANCEL" -> {
                count(5)
                Command.Cancel(Cancellation(id(tokens[1]), stay(tokens[2], tokens[3]), number(tokens[4])))
            }
            "STATUS" -> {
                count(2)
                Command.Status(id(tokens[1]))
            }
            else -> throw InputError()
        }
    }

    private fun id(token: String): String = token.takeIf(identifier::matches) ?: throw InputError()
    private fun number(token: String): Long {
        if (!integer.matches(token)) throw InputError()
        return token.toLongOrNull() ?: throw InputError()
    }
    private fun date(token: String): LocalDate {
        if (!dateShape.matches(token)) throw InputError()
        return try {
            LocalDate.parse(token)
        } catch (_: DateTimeParseException) {
            throw InputError()
        }
    }
    private fun stay(from: String, to: String): Stay = Stay(date(from), date(to))
    private fun validDate(date: LocalDate): Boolean = date in firstDate..lastDate
    private fun validStay(stay: Stay): Boolean =
        validDate(stay.from) && validDate(stay.to) && stay.length in 1..366

    private fun inRange(command: Command): Boolean = when (command) {
        is Command.Type -> command.percent in 0L..100L
        is Command.Stock -> validStay(command.stay) && command.base in 0L..1_000_000_000L
        is Command.Avail -> validDate(command.date)
        is Command.Book -> validStay(command.booking.stay) && command.booking.quantity in 1L..1_000_000_000L
        is Command.Cancel -> validStay(command.cancellation.stay) && command.cancellation.quantity in 1L..1_000_000_000L
        is Command.Status -> true
        is Command.Request -> inRange(command.operation)
        is Command.Tx -> validStay(command.booking.stay) && command.booking.quantity in 1L..1_000_000_000L
        is Command.Begin, is Command.Step, Command.End -> true
    }
}
