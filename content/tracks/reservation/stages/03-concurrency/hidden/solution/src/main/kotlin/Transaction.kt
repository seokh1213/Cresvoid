import java.time.LocalDate

data class InventoryRow(val type: String, val date: LocalDate)
data class Observation(val row: InventoryRow, val stock: StockSnapshot, val version: Long)

sealed interface TransactionState {
    data object Ready : TransactionState
    data class Prepared(val observations: List<Observation>, val acceptable: Boolean) : TransactionState
    data object Booked : TransactionState
    data object Full : TransactionState

    val label: String get() = when (this) {
        Ready -> "READY"
        is Prepared -> "PREPARED"
        Booked -> "BOOKED"
        Full -> "FULL"
    }
    val terminal: Boolean get() = this == Booked || this == Full
}

class Transaction(val id: String, val booking: Booking) {
    val rows = booking.stay.dates().map { InventoryRow(booking.type, it) }
    var state: TransactionState = TransactionState.Ready
    var attempts = 0
    var retries = 0
    var waits = 0

    fun response(prefix: String): String = "$prefix $id ${state.label} $attempts $retries $waits"

    fun retry() {
        retries++
        state = TransactionState.Ready
    }
}
