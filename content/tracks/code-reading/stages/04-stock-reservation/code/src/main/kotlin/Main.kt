import java.time.Instant
import java.time.LocalDate

fun main() {
    val service = ReservationService()
    val reports = ReservationReports(service)
    val commands = ReservationCommands(service, reports)
    System.`in`.bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() }.forEach { line ->
            commands.execute(line)?.let(::println)
        }
    }
}

class ReservationCommands(
    private val service: ReservationService,
    private val reports: ReservationReports,
) {
    fun execute(line: String): String? {
        val fields = line.trim().split(Regex("\\s+"))
        return when (fields[0]) {
            "AT" -> {
                requireSize(fields, 2)
                service.moveTo(Instant.parse(fields[1]))
                null
            }
            "ADD" -> {
                requireSize(fields, 3)
                service.replenish(fields[1], fields[2].toInt())
                null
            }
            "HOLD" -> {
                requireSize(fields, 5)
                val request = ReservationRequest(
                    id = fields[1],
                    sku = fields[2],
                    quantity = fields[3].toInt(),
                    lifetimeSeconds = fields[4].toLong(),
                )
                service.reserve(request)
                null
            }
            "CONFIRM" -> {
                requireSize(fields, 2)
                service.confirm(fields[1])
                null
            }
            "CANCEL" -> {
                requireSize(fields, 2)
                service.cancel(fields[1])
                null
            }
            "COMPACT" -> {
                requireSize(fields, 2)
                service.compact(fields[1])
                null
            }
            "STOCK" -> {
                requireSize(fields, 2)
                reports.stock(fields[1])
            }
            "DAILY" -> {
                requireSize(fields, 2)
                reports.daily(LocalDate.parse(fields[1]))
            }
            "LATEST" -> {
                requireSize(fields, 1)
                reports.latest()
            }
            "EXPIRED" -> {
                requireSize(fields, 1)
                reports.expired()
            }
            else -> error("Unknown command: ${fields[0]}")
        }
    }

    private fun requireSize(fields: List<String>, expected: Int) {
        require(fields.size == expected) { "Invalid command arguments" }
    }
}
