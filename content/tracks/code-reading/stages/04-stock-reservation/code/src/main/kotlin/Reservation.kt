import java.time.Instant

enum class ReservationStatus {
    HELD,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
}

data class ReservationRequest(
    val id: String,
    val sku: String,
    val quantity: Int,
    val lifetimeSeconds: Long,
)

class Reservation(
    val request: ReservationRequest,
    val expiresAt: Instant,
) {
    var status: ReservationStatus = ReservationStatus.HELD
        private set
    var confirmedAt: Instant? = null
        private set

    fun confirm(at: Instant) {
        check(status == ReservationStatus.HELD)
        status = ReservationStatus.CONFIRMED
        confirmedAt = at
    }

    fun cancel() {
        check(status == ReservationStatus.HELD)
        status = ReservationStatus.CANCELLED
    }

    fun expire() {
        check(status == ReservationStatus.HELD)
        status = ReservationStatus.EXPIRED
    }
}

class ProductStock(val sku: String) {
    var onHand: Int = 0
        private set
    private val tracked = arrayListOf<Reservation>()

    fun replenish(quantity: Int) {
        onHand += quantity
    }

    fun track(reservation: Reservation) {
        tracked.add(reservation)
    }

    fun ship(quantity: Int) {
        onHand -= quantity
    }

    fun heldQuantity(): Int = tracked
        .filter { it.status == ReservationStatus.HELD }
        .sumOf { it.request.quantity }

    fun availableQuantity(): Int = onHand - heldQuantity()

    fun compact() {
        tracked.removeIf { it.status != ReservationStatus.HELD }
    }

    fun trackedCount(): Int = tracked.size
}
