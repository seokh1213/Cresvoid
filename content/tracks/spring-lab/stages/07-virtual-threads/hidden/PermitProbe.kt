package lab

import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

class ProbeMetrics {
    var dbInterruptions = 0
    var dbRecovery = -3
    var downstreamInterruptions = 0
    var downstreamRecovery = -3
    var sqlExceptions = 0

    fun reportFailure() {
        println("METRIC permit_recovery 0")
        println("METRIC db_interruptions $dbInterruptions")
        println("METRIC db_recovery $dbRecovery")
        println("METRIC downstream_interruptions $downstreamInterruptions")
        println("METRIC downstream_recovery $downstreamRecovery")
        println("METRIC sql_exceptions $sqlExceptions")
    }
}

fun recoveryResult(service: QuoteService): Int = try {
    service.quote(3) ?: -1
} catch (_: Exception) {
    -2
}

fun interruptedDbCall(service: QuoteService, resources: Resources) {
    val failure = AtomicReference<Exception?>()
    val worker = Thread.ofVirtual().start {
        try {
            service.quote(3)
        } catch (error: Exception) {
            failure.set(error)
        }
    }
    try {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        var waitingInDb = false
        while (worker.isAlive && System.nanoTime() < deadline) {
            waitingInDb = resources.pool.hikariPoolMXBean.activeConnections == 1 &&
                worker.state == Thread.State.TIMED_WAITING &&
                worker.stackTrace.any { it.className == "lab.Resources" && it.methodName == "price" }
            if (waitingInDb) break
            Thread.sleep(1)
        }
        check(waitingInDb) { "DB request did not enter its blocking wait" }
    } finally {
        worker.interrupt()
        worker.join(5_000)
    }
    check(!worker.isAlive) { "interrupted DB request did not stop" }
    check(failure.get() is InterruptedException || failure.get() is SQLException) {
        "DB interruption was not propagated"
    }
    check(resources.pool.hikariPoolMXBean.activeConnections == 0) { "connection leaked after DB interruption" }
}

fun checkDbInterruption(metrics: ProbeMetrics) {
    Application().pool().use { pool ->
        val resources = Resources(pool)
        val service = QuoteService(resources, Downstream())
        repeat(4) {
            interruptedDbCall(service, resources)
            metrics.dbInterruptions++
        }
        metrics.dbRecovery = recoveryResult(service)
        check(metrics.dbRecovery == 110) { "permit leaked after DB interruption" }
    }
}

fun checkDownstreamInterruption(metrics: ProbeMetrics) {
    Application().pool().use { pool ->
        val downstream = Downstream()
        val service = QuoteService(Resources(pool), downstream)
        downstream.hold()
        try {
            repeat(4) {
                val failure = AtomicReference<Exception?>()
                val worker = Thread.ofVirtual().start {
                    try {
                        service.quote(3)
                    } catch (error: Exception) {
                        failure.set(error)
                    }
                }
                try {
                    val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
                    while (downstream.active.get() != 1 && worker.isAlive && System.nanoTime() < deadline) {
                        Thread.sleep(10)
                    }
                    check(downstream.active.get() == 1) { "downstream call did not enter" }
                } finally {
                    worker.interrupt()
                    worker.join(5_000)
                }
                check(!worker.isAlive && failure.get() is InterruptedException) {
                    "downstream interruption was not propagated"
                }
                check(downstream.active.get() == 0 && pool.hikariPoolMXBean.activeConnections == 0)
                metrics.downstreamInterruptions++
            }
        } finally {
            downstream.release()
        }
        metrics.downstreamRecovery = recoveryResult(service)
        check(metrics.downstreamRecovery == 110) { "permit leaked after downstream interruption" }
    }
}

fun checkSqlFailure(metrics: ProbeMetrics) {
    val pool = Application().pool()
    val service = QuoteService(Resources(pool), Downstream())
    pool.close()
    repeat(8) {
        val failure = try {
            service.quote(3)
            null
        } catch (error: Exception) {
            error
        }
        check(failure is SQLException) { "SQL failure was not propagated" }
        metrics.sqlExceptions++
    }
}

fun main() {
    val metrics = ProbeMetrics()
    val result = try {
        checkDbInterruption(metrics)
        checkDownstreamInterruption(metrics)
        checkSqlFailure(metrics)
        0
    } catch (failure: IllegalStateException) {
        System.err.println(failure.message)
        metrics.reportFailure()
        1
    } catch (failure: Exception) {
        System.err.println("probe error: $failure")
        2
    }
    exitProcess(result)
}
