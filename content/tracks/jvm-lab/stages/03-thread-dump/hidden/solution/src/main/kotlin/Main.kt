import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FirstLockWindows {
    private val pairs = ConcurrentHashMap<String, CountDownLatch>()

    fun enter(pair: String?) {
        if (pair == null) return
        val arrived = pairs.computeIfAbsent(pair) { CountDownLatch(2) }
        arrived.countDown()
        // The bounded fixture widens overlap without requiring two threads to hold different locks.
        arrived.await(2, TimeUnit.SECONDS)
    }
}

fun HttpExchange.reply(status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}

fun transfer(exchange: HttpExchange, store: AccountStore, windows: FirstLockWindows) {
    val query = exchange.requestURI.rawQuery.orEmpty().split('&').associate { field ->
        val parts = field.split('=', limit = 2)
        require(parts.size == 2)
        parts[0] to parts[1]
    }
    require(query.keys.all { it in setOf("from", "to", "amount", "pair") })
    val from = query["from"]
    val to = query["to"]
    val amount = query["amount"]?.toIntOrNull()
    val pair = query["pair"]
    require(from in setOf("A", "B") && to in setOf("A", "B") && from != to)
    require(amount != null && amount in 1..100)
    require(pair == null || pair.matches(Regex("[a-z0-9-]{1,32}")))
    store.transfer(requireNotNull(from), requireNotNull(to), amount) { windows.enter(pair) }
    exchange.reply(200, "OK")
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val store = AccountStore()
    val windows = FirstLockWindows()
    val workerIds = AtomicInteger()
    val workers = Executors.newCachedThreadPool { task ->
        Thread(task, "transfer-worker-${workerIds.incrementAndGet()}").apply { isDaemon = true }
    }
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = workers
    server.createContext("/") { exchange ->
        try {
            when {
                exchange.requestURI.path == "/health" && exchange.requestMethod == "GET" ->
                    exchange.reply(200, "READY ${ProcessHandle.current().pid()}")
                exchange.requestURI.path == "/balances" && exchange.requestMethod == "GET" ->
                    exchange.reply(200, store.snapshot())
                exchange.requestURI.path == "/transfer" && exchange.requestMethod == "POST" ->
                    transfer(exchange, store, windows)
                exchange.requestURI.path in setOf("/health", "/balances", "/transfer") ->
                    exchange.reply(405, "METHOD")
                else -> exchange.reply(404, "NOT_FOUND")
            }
        } catch (_: IllegalArgumentException) {
            exchange.reply(400, "INVALID")
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        workers.shutdownNow()
    })
    server.start()
}
