package lab

import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom

fun <T> retry(maxAttempts: Int, trace: RetryTrace, transaction: () -> T): T {
    require(maxAttempts in 1..5)
    for (attempt in 1..maxAttempts) {
        try {
            return transaction()
        } catch (failure: SQLException) {
            if (failure.sqlState != "40001" || attempt == maxAttempts) throw failure
            trace.pause(ThreadLocalRandom.current().nextLong(20, 81))
        }
    }
    error("Unreachable attempt limit")
}
