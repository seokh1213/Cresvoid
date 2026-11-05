import java.time.LocalDate
import java.time.ZoneId

class ReservationReports(private val service: ReservationService) {
    private val businessZone = ZoneId.of("Asia/Seoul")

    fun stock(sku: String): String {
        val stock = service.stock(sku)
        val available = stock?.availableQuantity() ?: 0
        val held = stock?.heldQuantity() ?: 0
        val tracked = stock?.trackedCount() ?: 0
        return "$sku available=$available held=$held tracked=$tracked"
    }

    fun daily(date: LocalDate): String {
        val confirmed = service.history().filter { reservation ->
            val confirmedAt = reservation.confirmedAt
            confirmedAt != null && confirmedAt.atZone(businessZone).toLocalDate() == date
        }
        val bySku = confirmed.groupBy { it.request.sku }
        return bySku.toSortedMap().entries.joinToString(
            separator = ",",
        ) { (sku, reservations) ->
            val quantity = reservations.sumOf { it.request.quantity }
            "$sku:${reservations.size}/$quantity"
        }.ifEmpty { "(none)" }
    }

    fun latest(): String {
        val bySku = service.history().associateBy { it.request.sku }
        return bySku.toSortedMap().entries.joinToString(
            separator = ",",
        ) { (sku, reservation) ->
            "$sku:${reservation.request.id}/${reservation.status}"
        }.ifEmpty { "(none)" }
    }

    fun expired(): String = service.drainExpired()
        .joinToString(",")
        .ifEmpty { "(none)" }
}
