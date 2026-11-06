package lab

import com.zaxxer.hikari.HikariDataSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class Resources(val pool: HikariDataSource) {
    val queries = AtomicInteger()

    fun price(id: Int): Int = pool.connection.use { connection ->
        Thread.sleep(150)
        connection.prepareStatement("SELECT 100 + ?").use { statement ->
            statement.setInt(1, id)
            statement.executeQuery().use { rows ->
                check(rows.next())
                queries.incrementAndGet()
                rows.getInt(1)
            }
        }
    }
}

class Downstream {
    @Volatile private var release = CountDownLatch(0)
    val active = AtomicInteger()
    val peak = AtomicInteger()
    val calls = AtomicInteger()

    fun hold() {
        check(active.get() == 0)
        release = CountDownLatch(1)
    }

    fun release() = release.countDown()

    fun quote(price: Int): Int {
        val current = active.incrementAndGet()
        peak.accumulateAndGet(current, ::maxOf)
        calls.incrementAndGet()
        try {
            release.await()
            return price + 7
        } finally {
            active.decrementAndGet()
        }
    }
}
