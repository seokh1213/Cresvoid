package lab

object OrderWorkflow {
    fun create(order: Order, fault: Fault) {
        Postgres().use { db ->
            db.query("BEGIN")
            try {
                db.query("INSERT INTO orders VALUES (${sqlId(order.id)},${order.amount})")
                db.query("INSERT INTO outbox(event_id,order_id,amount) VALUES " +
                    "(${sqlId(order.eventId)},${sqlId(order.id)},${order.amount})")
                FailurePoint.beforeCommit(fault)
                db.query("COMMIT")
            } catch (error: Exception) {
                db.query("ROLLBACK")
                throw error
            }
        }
        FailurePoint.afterCommit(fault)
    }

    fun relay(publisher: Publisher) {
        Postgres().use { db ->
            val pending = db.query("SELECT order_id,amount FROM outbox WHERE NOT sent ORDER BY event_id")
            for (row in pending) {
                val order = Order(row[0], row[1].toInt())
                publisher.send(order)
                db.query("UPDATE outbox SET sent=true WHERE event_id=${sqlId(order.eventId)}")
            }
        }
    }

    fun apply(order: Order) {
        Postgres().use { db ->
            db.query("BEGIN")
            try {
                val inserted = db.query("INSERT INTO seen VALUES (${sqlId(order.eventId)}) " +
                    "ON CONFLICT DO NOTHING RETURNING event_id")
                if (inserted.isNotEmpty()) {
                    db.query("INSERT INTO effects(event_id,order_id,amount) VALUES " +
                        "(${sqlId(order.eventId)},${sqlId(order.id)},${order.amount})")
                }
                db.query("COMMIT")
            } catch (error: Exception) {
                db.query("ROLLBACK")
                throw error
            }
        }
    }
}
