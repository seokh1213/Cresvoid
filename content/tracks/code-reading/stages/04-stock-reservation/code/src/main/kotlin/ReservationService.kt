import java.time.Instant
import java.util.PriorityQueue

class ReservationService {
    var now: Instant = Instant.parse("2026-01-01T00:00:00Z")
        private set
    private val stockBySku = linkedMapOf<String, ProductStock>()
    private val reservationsById = linkedMapOf<String, Reservation>()
    private val expirationQueue = PriorityQueue<Reservation>(compareBy { it.expiresAt })
    private val expiredNotices = arrayListOf<String>()

    fun replenish(sku: String, quantity: Int) {
        require(quantity > 0)
        val stock = stockBySku.getOrPut(sku) { ProductStock(sku) }
        stock.replenish(quantity)
    }

    fun reserve(request: ReservationRequest) {
        require(request.quantity > 0 && request.lifetimeSeconds > 0)
        require(request.id !in reservationsById)
        val stock = stockBySku.getOrPut(request.sku) { ProductStock(request.sku) }
        if (stock.availableQuantity() < request.quantity) return
        val reservation = Reservation(request, now.plusSeconds(request.lifetimeSeconds))
        reservationsById[request.id] = reservation
        stock.track(reservation)
        expirationQueue.add(reservation)
    }

    fun confirm(id: String) {
        val reservation = reservationsById[id] ?: return
        if (reservation.status != ReservationStatus.HELD) return
        if (now.isAfter(reservation.expiresAt)) return
        reservation.confirm(now)
        stockBySku.getValue(reservation.request.sku).ship(reservation.request.quantity)
    }

    fun cancel(id: String) {
        val reservation = reservationsById[id] ?: return
        if (reservation.status == ReservationStatus.HELD) reservation.cancel()
    }

    fun moveTo(next: Instant) {
        require(!next.isBefore(now))
        now = next
        expireReservations()
    }

    private fun expireReservations() {
        while (true) {
            val reservation = expirationQueue.peek() ?: return
            if (!reservation.expiresAt.isBefore(now)) return
            expirationQueue.poll()
            if (reservation.status != ReservationStatus.HELD) continue
            reservation.expire()
            expiredNotices.add(reservation.request.id)
        }
    }

    fun compact(sku: String) {
        stockBySku[sku]?.compact()
    }

    fun stock(sku: String): ProductStock? = stockBySku[sku]

    fun history(): List<Reservation> = reservationsById.values.toList()

    fun drainExpired(): List<String> {
        val delivered = mutableListOf<String>()
        val iterator = expiredNotices.iterator()
        while (iterator.hasNext()) {
            delivered.add(iterator.next())
            iterator.remove()
        }
        return delivered.sorted()
    }
}
