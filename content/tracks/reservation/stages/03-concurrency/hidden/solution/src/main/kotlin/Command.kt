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
    sealed interface HotelOperation : Command
    sealed interface Mutation : HotelOperation
    sealed interface BatchOperation : Command
    data class Type(val type: String, val percent: Long) : HotelOperation
    data class Stock(val type: String, val stay: Stay, val base: Long) : HotelOperation
    data class Avail(val type: String, val date: LocalDate) : HotelOperation
    data class Book(val booking: Booking) : Mutation
    data class Cancel(val cancellation: Cancellation) : Mutation
    data class Status(val id: String) : HotelOperation
    data class Request(val id: String, val operation: Mutation) : HotelOperation
    data class Begin(val mode: LockMode) : Command
    data class Tx(val id: String, val booking: Booking) : BatchOperation
    data class Step(val id: String) : BatchOperation
    data object End : BatchOperation
}

enum class LockMode { PESSIMISTIC, OPTIMISTIC, CONSTRAINT }

sealed interface ParsedLine {
    data object Empty : ParsedLine
    data class Rejected(val response: String) : ParsedLine
    data class Accepted(val command: Command) : ParsedLine
}
