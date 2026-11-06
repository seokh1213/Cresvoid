package lab

data class Order(val id: String, val amount: Int) {
    val eventId: String get() = "evt-$id"
    fun body(): String = "$eventId|$id|$amount"
}

enum class Fault { NORMAL, ROLLBACK, COMMIT_GAP }
class RollbackRequested : RuntimeException()
class ReplyLost : RuntimeException()

object FailurePoint {
    fun beforeCommit(fault: Fault) {
        if (fault == Fault.ROLLBACK) throw RollbackRequested()
    }

    fun afterCommit(fault: Fault) {
        if (fault == Fault.COMMIT_GAP) throw ReplyLost()
    }
}

fun parseOrder(body: String): Pair<Order, Fault>? {
    val parts = body.split('|')
    if (parts.size != 3 || !parts[0].matches(Regex("[a-z][a-z0-9-]{0,29}"))) return null
    val amount = parts[1].toIntOrNull()?.takeIf { it in 1..100_000 } ?: return null
    val fault = when (parts[2]) {
        "normal" -> Fault.NORMAL
        "rollback" -> Fault.ROLLBACK
        "commit-gap" -> Fault.COMMIT_GAP
        else -> return null
    }
    return Order(parts[0], amount) to fault
}

fun parseEvent(body: String): Order {
    val parts = body.split('|')
    require(parts.size == 3)
    val order = parseOrder("${parts[1]}|${parts[2]}|normal")?.first ?: error("Invalid event")
    require(parts[0] == order.eventId)
    return order
}

fun initializeDatabase() = Postgres().use { db ->
    db.query("""
        CREATE TABLE orders (order_id text PRIMARY KEY, amount integer NOT NULL);
        CREATE TABLE outbox (event_id text PRIMARY KEY, order_id text NOT NULL,
          amount integer NOT NULL, sent boolean NOT NULL DEFAULT false);
        CREATE TABLE seen (event_id text PRIMARY KEY);
        CREATE TABLE effects (seq bigserial PRIMARY KEY, event_id text NOT NULL,
          order_id text NOT NULL, amount integer NOT NULL);
        CREATE TABLE receipts (offset_no bigint PRIMARY KEY, event_id text NOT NULL,
          order_id text NOT NULL, amount integer NOT NULL);
        CREATE TABLE published (seq bigserial PRIMARY KEY, event_id text NOT NULL,
          order_id text NOT NULL, amount integer NOT NULL);
    """.trimIndent())
}
