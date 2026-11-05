@file:Import("../../../../../lab-env/load.main.kts")

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RejectingStorage : AutoCloseable {
    val reject = AtomicBoolean(false)
    val disconnect = AtomicBoolean(false)
    val httpStatus = AtomicInteger(500)
    val databaseCode = AtomicInteger(252)
    private val stored = mutableListOf<String>()
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 16)
    val origin: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            exchange.use {
                val rows = exchange.requestBody.bufferedReader().readText().trimEnd().lines()
                if (disconnect.get()) return@use
                val rejected = reject.get()
                val body = if (rejected) "Code: 252. private database details" else ""
                if (rejected) exchange.responseHeaders.set("X-ClickHouse-Exception-Code", databaseCode.get().toString())
                else synchronized(stored) { stored.addAll(rows) }
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(if (rejected) httpStatus.get() else 200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
    }

    fun rows(): List<String> = synchronized(stored) { stored.toList() }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}

fun checkRejected(response: HttpResponse<String>, httpStatus: Int = 500, databaseCode: Int = 252) {
    check(response.statusCode() == 503 && response.body() == "storage unavailable")
    check(response.headers().firstValue("X-Storage-Http-Status").orElse("") == httpStatus.toString())
    check(response.headers().firstValue("X-Storage-Error-Code").orElse("") == databaseCode.toString())
}

fun checkTransportFailure(client: HttpClient, base: String, storage: RejectingStorage) {
    val next = steady(40, 31 / 40.0) { index ->
        val id = index + 33
        post(base, "/events", "$id\t${id * 17 + 3}\n")
    }
    check(runLoad(client, next).all { it.status == 202 && it.body == "accepted" })
    storage.disconnect.set(true)
    val unknown = client.send(post(base, "/events", "64\t1091\n"), HttpResponse.BodyHandlers.ofString())
    val failed = if (unknown.statusCode() == 202) {
        check(unknown.body() == "accepted")
        client.send(post(base, "/flush", ""), HttpResponse.BodyHandlers.ofString())
    } else unknown
    check(failed.statusCode() == 503 && failed.body() == "storage unavailable")
    check(failed.headers().firstValue("X-Storage-Http-Status").isEmpty)
    check(failed.headers().firstValue("X-Storage-Error-Code").isEmpty)
    storage.disconnect.set(false)
    val recovered = client.send(post(base, "/flush", ""), HttpResponse.BodyHandlers.ofString())
    check(recovered.statusCode() == 200 && recovered.body() == "flushed")
    check(storage.rows().sorted() == (1..64).map { "$it\t${it * 17 + 3}" }.sorted())
    println("PASS transport failure retained unconfirmed rows; diagnostic headers absent")
}

fun recovery(programDir: File) {
    RejectingStorage().use { storage ->
        HttpClient.newHttpClient().use { client ->
            val port = freePort()
            val base = "http://127.0.0.1:$port"
            val environment = mapOf(
                "PORT" to port.toString(), "CLICKHOUSE_URL" to storage.origin,
                "CLICKHOUSE_TABLE" to "default.events",
            )
            Program(programDir, environment, base).use {
                val initial = steady(40, 31 / 40.0) { index ->
                    post(base, "/events", "${index + 1}\t${(index + 1) * 17 + 3}\n")
                }
                check(runLoad(client, initial).all { it.status == 202 && it.body == "accepted" })
                storage.reject.set(true)
                val accepted = client.send(post(base, "/events", "32\t547\n"), HttpResponse.BodyHandlers.ofString())
                if (accepted.statusCode() == 503) checkRejected(accepted)
                else check(accepted.statusCode() == 202 && accepted.body() == "accepted")
                storage.httpStatus.set(403)
                storage.databaseCode.set(60)
                checkRejected(client.send(post(base, "/flush", ""), HttpResponse.BodyHandlers.ofString()), 403, 60)
                storage.reject.set(false)
                val flushed = client.send(post(base, "/flush", ""), HttpResponse.BodyHandlers.ofString())
                check(flushed.statusCode() == 200 && flushed.body() == "flushed")
                val expected = (1..32).map { "$it\t${it * 17 + 3}" }.sorted()
                check(storage.rows().sorted() == expected) { "Rows from 202 or 503 were lost or duplicated" }
                val invalid = client.send(post(base, "/events", "33\t564\ninvalid\n"), HttpResponse.BodyHandlers.ofString())
                check(invalid.statusCode() == 400)
                val repeated = client.send(post(base, "/flush", ""), HttpResponse.BodyHandlers.ofString())
                check(repeated.statusCode() == 200 && repeated.body() == "flushed")
                check(storage.rows().sorted() == expected) { "Invalid input or repeated flush changed rows" }
                println("PASS recovery: 202/503 rows=32; failed flush retained; invalid batch rejected; repeat unchanged")
                checkTransportFailure(client, base, storage)
            }
            val stopped = runLoad(client, listOf(Arrival(0, get(base, "/health")))).single()
            check(stopped.status == 0) { "Program still serves after close" }
            println("PASS server shutdown")
        }
    }
}

require(args.size == 1) { "Expected built program directory" }
recovery(File(args.single()).absoluteFile)
