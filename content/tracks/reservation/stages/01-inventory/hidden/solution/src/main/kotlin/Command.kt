import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Stay(val from: LocalDate, val to: LocalDate) {
    val length: Int get() = ChronoUnit.DAYS.between(from, to).toInt()
    fun dates(): List<LocalDate> = List(length) { from.plusDays(it.toLong()) }
    fun contains(other: Stay): Boolean = from <= other.from && other.to <= to
}

data class Booking(val id: String, val type: String, val stay: Stay, val quantity: Long)
data class Cancellation(val id: String, val stay: Stay, val quantity: Long)

sealed interface Command {
    data class Type(val type: String, val percent: Long) : Command
    data class Stock(val type: String, val stay: Stay, val base: Long) : Command
    data class Avail(val type: String, val date: LocalDate) : Command
    data class Book(val booking: Booking) : Command
    data class Cancel(val cancellation: Cancellation) : Command
    data class Status(val id: String) : Command
}

sealed interface ParsedLine {
    data object Empty : ParsedLine
    data class Rejected(val response: String) : ParsedLine
    data class Accepted(val command: Command) : ParsedLine
}
