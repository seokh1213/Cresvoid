package lab

import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class PairRequest(val batch: Int, val left: Int, val right: Int)

data class PairResult(val a: String, val b: String, val aWait: Boolean) {
    fun encode(): String = "a=$a\nb=$b\na_wait=${if (aWait) 1 else 0}\nreads=2\n"
}

enum class RunMode { OBSERVE, IMPLEMENT }

fun runPair(request: PairRequest, mode: RunMode): PairResult = connect().use { a ->
    connect().use { b ->
        connect().use { monitor -> runSessions(request, mode, listOf(a, b, monitor)) }
    }
}

private fun runSessions(request: PairRequest, mode: RunMode, sessions: List<Connection>): PairResult {
    val (a, b, monitor) = sessions
    val isolation = if (mode == RunMode.OBSERVE) Connection.TRANSACTION_REPEATABLE_READ else Policy.isolation
    for (connection in listOf(a, b)) {
        connection.transactionIsolation = isolation
        connection.autoCommit = false
    }
    try {
        if (mode == RunMode.OBSERVE) {
            a.createStatement().use { it.executeUpdate("UPDATE ballast SET value = value + 1") }
        }
        readRange(a, request.batch)
        readRange(b, request.batch)
        val sessionA = connectionId(a)
        return Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val first = executor.submit<String> { insertAndFinish(a, request.batch, request.left, mode) }
            val waited = awaitWaitOrCompletion(monitor, sessionA, first)
            val second = insertAndFinish(b, request.batch, request.right, mode)
            PairResult(first.get(15, TimeUnit.SECONDS), second, waited)
        }
    } finally {
        a.rollback()
        b.rollback()
    }
}

private fun awaitWaitOrCompletion(monitor: Connection, session: Long, pending: Future<String>): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6)
    while (System.nanoTime() < deadline) {
        if (isWaiting(monitor, session)) return true
        if (pending.isDone) return false
        Thread.sleep(10)
    }
    error("Insertion neither completed nor entered a database lock wait")
}

private fun insertAndFinish(connection: Connection, batch: Int, id: Int, mode: RunMode): String {
    return try {
        val inserted = if (mode == RunMode.IMPLEMENT) Policy.insert(connection, batch, id) else {
            connection.prepareStatement("INSERT INTO slots(batch, id) VALUES (?, ?)").use { statement ->
                statement.setInt(1, batch)
                statement.setInt(2, id)
                statement.executeUpdate() == 1
            }
        }
        connection.commit()
        if (inserted) "inserted" else "duplicate"
    } catch (error: SQLException) {
        connection.rollback()
        if (error.errorCode == 1213) "deadlock" else "sql_${error.errorCode}"
    }
}
