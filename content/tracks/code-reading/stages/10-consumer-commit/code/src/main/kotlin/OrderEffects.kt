data class OrderEvent(
    val eventId: String,
    val sku: String,
    val member: String,
    val units: Int,
    val points: Int,
)

class Inventory {
    private val available = mutableMapOf<String, Int>()
    private val reservations = mutableSetOf<String>()

    fun seed(sku: String, units: Int) {
        require(units >= 0)
        available[sku] = units
    }

    fun reserve(order: OrderEvent) {
        if (order.eventId in reservations) return
        val remaining = quantity(order.sku) - order.units
        check(remaining >= 0) { "insufficient stock" }
        available[order.sku] = remaining
        reservations.add(order.eventId)
    }

    fun quantity(sku: String): Int = available[sku] ?: 0
}

class LoyaltyAccounts {
    private val balances = mutableMapOf<String, Int>()

    // 기존 포인트 API는 요청 키를 받지 않는 계약이다.
    fun credit(order: OrderEvent) {
        balances[order.member] = balance(order.member) + order.points
    }

    fun balance(member: String): Int = balances[member] ?: 0
}

enum class ProcessingStep { BEFORE, STOCK, POINTS }

class WorkerInterrupted(val eventId: String, val step: ProcessingStep) : RuntimeException()

class FailurePlan {
    private val scheduled = mutableMapOf<String, ProcessingStep>()

    fun schedule(eventId: String, step: ProcessingStep) {
        scheduled[eventId] = step
    }

    fun reach(eventId: String, step: ProcessingStep) {
        if (scheduled[eventId] != step) return
        scheduled.remove(eventId)
        throw WorkerInterrupted(eventId, step)
    }
}
