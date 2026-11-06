package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.zaxxer.hikari.HikariDataSource
import org.h2.tools.Server
import java.net.InetSocketAddress
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class CatalogService(private val pool: HikariDataSource, private val proxy: IdleProxy) {
    private val successes = AtomicLong()
    private val failures = AtomicLong()

    fun query(exchange: HttpExchange) {
        try {
            val value = pool.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT 42").use { rows ->
                        check(rows.next())
                        rows.getInt(1)
                    }
                }
            }
            successes.incrementAndGet()
            exchange.reply(200, value.toString())
        } catch (_: SQLException) {
            failures.incrementAndGet()
            exchange.reply(503, "database unavailable")
        }
    }

    fun stats(exchange: HttpExchange) {
        val bean = pool.hikariPoolMXBean
        val values = linkedMapOf(
            "successes" to successes.get(),
            "failures" to failures.get(),
            "created" to proxy.created.get(),
            "idle_drops" to proxy.idleDrops.get(),
            "age_drops" to proxy.ageDrops.get(),
            "idle" to bean.idleConnections.toLong(),
            "active" to bean.activeConnections.toLong(),
            "total" to bean.totalConnections.toLong(),
        )
        exchange.reply(200, values.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
}

fun HttpExchange.reply(status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val database = Server.createTcpServer("-tcpPort", "0", "-tcpDaemon", "-ifNotExists").start()
    val proxy = IdleProxy(database.port)
    val jdbcUrl = "jdbc:h2:tcp://127.0.0.1:${proxy.port}/mem:catalog;DB_CLOSE_DELAY=-1"
    val pool = HikariDataSource(poolSettings(jdbcUrl))
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
    while (pool.hikariPoolMXBean.idleConnections < 4 && System.nanoTime() < deadline) Thread.sleep(25)
    check(pool.hikariPoolMXBean.idleConnections == 4)
    val service = CatalogService(pool, proxy)
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 32)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/health") { it.reply(200, "ready") }
    server.createContext("/query", service::query)
    server.createContext("/stats", service::stats)
    val stopped = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
        pool.close()
        proxy.close()
        database.stop()
        stopped.countDown()
    })
    server.start()
    stopped.await()
}
