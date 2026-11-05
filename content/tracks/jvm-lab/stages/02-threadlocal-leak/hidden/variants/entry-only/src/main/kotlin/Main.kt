import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class UserRequest(val id: Long, val lane: Int, val user: String?, val fail: Boolean, val bytes: Int)
data class Reply(val status: Int, val body: String)

class RequestWorkers {
    private val scope = RequestScope()
    private val lanes = List(4) { index ->
        Executors.newSingleThreadExecutor { task -> Thread(task, "request-$index") }
    }

    init {
        snapshot()
    }

    fun execute(request: UserRequest): Reply = lanes[request.lane].submit(Callable {
        val worker = Thread.currentThread().threadId()
        try {
            Reply(200, "${request.id}|${request.lane}|$worker|${scope.handle(request)}")
        } catch (failure: BusinessFailure) {
            Reply(503, "${request.id}|${request.lane}|$worker|${failure.principal}")
        }
    }).get()

    fun snapshot(): String = lanes.mapIndexed { index, executor ->
        executor.submit(Callable {
            "$index|${Thread.currentThread().threadId()}|${scope.retainedBytes()}"
        })
    }.joinToString("\n") { it.get() }

    fun close() = lanes.forEach { it.shutdownNow() }
}

fun parseRequest(exchange: HttpExchange): UserRequest {
    val pairs = exchange.requestURI.rawQuery.orEmpty().split('&').map { entry ->
        val parts = entry.split('=', limit = 2)
        require(parts.size == 2)
        parts[0] to parts[1]
    }
    require(pairs.map { it.first }.distinct().size == pairs.size)
    val query = pairs.toMap()
    require(query.keys.all { it in setOf("id", "lane", "user", "fail", "bytes") })
    val id = requireNotNull(query["id"]?.toLongOrNull()).also { require(it >= 0) }
    val lane = requireNotNull(query["lane"]?.toIntOrNull()).also { require(it in 0..3) }
    val bytes = requireNotNull(query["bytes"]?.toIntOrNull()).also { require(it in 1..1_048_576) }
    val user = query["user"]?.also { require(it.matches(Regex("[A-Za-z0-9-]{1,40}"))) }
    val fail = when (query["fail"]) {
        "0" -> false
        "1" -> true
        else -> throw IllegalArgumentException("fail")
    }
    return UserRequest(id, lane, user, fail, bytes)
}

fun respond(exchange: HttpExchange, reply: Reply) {
    val bytes = (reply.body + "\n").toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
    exchange.close()
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")?.toIntOrNull())
    require(port in 1..65535)
    val workers = RequestWorkers()
    val transport = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 1024)
    server.executor = transport
    server.createContext("/") { exchange ->
        val reply = try {
            if (exchange.requestMethod != "GET") Reply(405, "method")
            else when (exchange.requestURI.path) {
                "/health" -> Reply(200, "ready")
                "/snapshot" -> Reply(200, workers.snapshot())
                "/request" -> workers.execute(parseRequest(exchange))
                else -> Reply(404, "missing")
            }
        } catch (_: IllegalArgumentException) {
            Reply(400, "invalid")
        }
        respond(exchange, reply)
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        workers.close()
        transport.shutdownNow()
    })
    server.start()
}
