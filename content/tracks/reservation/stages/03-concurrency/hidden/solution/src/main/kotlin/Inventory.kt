import java.time.LocalDate

data class StockSnapshot(val base: Long, val limit: Long, val reserved: Long) {
    val remaining: Long get() = limit - reserved
}

class RoomInventory(private val percent: Long) {
    private data class DayStock(var base: Long = 0, var reserved: Long = 0)
    private val days = mutableMapOf<LocalDate, DayStock>()

    private fun limit(base: Long): Long = base * (100 + percent) / 100

    fun snapshot(date: LocalDate): StockSnapshot {
        val stock = days[date] ?: return StockSnapshot(0, 0, 0)
        return StockSnapshot(stock.base, limit(stock.base), stock.reserved)
    }

    fun setStock(stay: Stay, base: Long): Boolean {
        val dates = stay.dates()
        val newLimit = limit(base)
        if (dates.any { snapshot(it).reserved > newLimit }) return false
        dates.forEach { days.getOrPut(it) { DayStock() }.base = base }
        return true
    }

    fun canBook(booking: Booking): Boolean =
        booking.stay.dates().all { snapshot(it).remaining >= booking.quantity }

    fun adjust(stay: Stay, quantity: Long) {
        stay.dates().forEach { days.getOrPut(it) { DayStock() }.reserved += quantity }
    }
}

class Reservation(val booking: Booking) {
    private val quantities = LongArray(booking.stay.length) { booking.quantity }

    fun canCancel(cancellation: Cancellation): Boolean =
        cancellation.stay.dates().all { quantities[index(it)] >= cancellation.quantity }

    fun cancel(cancellation: Cancellation) {
        cancellation.stay.dates().forEach { quantities[index(it)] -= cancellation.quantity }
    }

    fun status(): String = with(booking) {
        "STATUS $id $type ${stay.from} ${stay.to} ${quantities.joinToString(",")}"
    }

    private fun index(date: LocalDate): Int = (date.toEpochDay() - booking.stay.from.toEpochDay()).toInt()
}
