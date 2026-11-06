package lab

import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

class RetryTrace {
    var attempts: Int = 0
        private set
    private val delays = mutableListOf<Long>()
    private var shortSleeps = 0

    fun begin() { attempts++ }

    fun pause(millis: Long) {
        require(millis in 0..1000)
        val start = System.nanoTime()
        Thread.sleep(millis)
        if (System.nanoTime() - start < millis * 900_000) shortSleeps++
        delays += millis
    }

    fun fields(): String = "attempts=$attempts;delays=${delays.joinToString(",")};shortSleeps=$shortSleeps"
}

data class LeaveRequest(val round: Int, val doctor: Int, val limit: Int, val faults: Int, val state: String) {
    val id: String get() = "$round-$doctor"
}

class Database {
    private val barriers = ConcurrentHashMap<Int, CyclicBarrier>()

    fun connect(): Connection = DriverManager.getConnection(
        System.getenv("JDBC_URL") ?: "jdbc:postgresql://postgres:5432/lab",
        "lab", "lab",
    )

    fun prepare(rounds: Int) {
        barriers.clear()
        connect().use { connection ->
            connection.createStatement().use { sql ->
                sql.execute("DROP TABLE IF EXISTS decisions, attempt_log, duty")
                sql.execute("CREATE TABLE duty(round_id int, doctor int, active boolean NOT NULL, PRIMARY KEY(round_id, doctor))")
                sql.execute("CREATE TABLE decisions(request_id text PRIMARY KEY, outcome text NOT NULL)")
                sql.execute("CREATE TABLE attempt_log(request_id text, attempt_no int, PRIMARY KEY(request_id, attempt_no))")
                sql.execute("INSERT INTO duty SELECT r, d, true FROM generate_series(1,$rounds) r CROSS JOIN generate_series(1,2) d")
            }
        }
    }

    fun leave(request: LeaveRequest, trace: RetryTrace, paired: Boolean): String {
        trace.begin()
        recordAttempt(request, trace.attempts)
        return connect().use { connection ->
            connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            connection.autoCommit = false
            try {
                val result = decide(connection, request, paired && trace.attempts == 1)
                if (trace.attempts <= request.faults) injectFailure(connection, request.state)
                connection.commit()
                result
            } catch (failure: Exception) {
                connection.rollback()
                throw failure
            }
        }
    }

    private fun recordAttempt(request: LeaveRequest, attempt: Int) {
        connect().use { connection ->
            connection.prepareStatement("INSERT INTO attempt_log VALUES (?, ?)").use { sql ->
                sql.setString(1, request.id)
                sql.setInt(2, attempt)
                sql.executeUpdate()
            }
        }
    }

    private fun decide(connection: Connection, request: LeaveRequest, paired: Boolean): String {
        val active = connection.prepareStatement("SELECT count(*) FROM duty WHERE round_id=? AND active").use { sql ->
            sql.setInt(1, request.round)
            sql.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
        }
        // Both first attempts must read the same state before either writes.
        if (paired) barriers.computeIfAbsent(request.round) { CyclicBarrier(2) }.await(20, TimeUnit.SECONDS)
        val outcome = if (active > 1) "left" else "stayed"
        if (outcome == "left") {
            connection.prepareStatement("UPDATE duty SET active=false WHERE round_id=? AND doctor=?").use { sql ->
                sql.setInt(1, request.round)
                sql.setInt(2, request.doctor)
                sql.executeUpdate()
            }
        }
        connection.prepareStatement("INSERT INTO decisions VALUES (?, ?)").use { sql ->
            sql.setString(1, request.id)
            sql.setString(2, outcome)
            sql.executeUpdate()
        }
        return outcome
    }

    private fun injectFailure(connection: Connection, state: String) {
        require(state in setOf("40001", "23514"))
        connection.createStatement().use { sql ->
            sql.execute("DO $$ BEGIN RAISE EXCEPTION 'lab fault' USING ERRCODE = '$state'; END $$")
        }
    }
}
