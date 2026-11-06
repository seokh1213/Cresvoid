package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.hibernate.exception.ConstraintViolationException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.Executors

fun main() {
    val database = Database()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", System.getenv("PORT").toInt()), 32)
    val executor = Executors.newSingleThreadExecutor()
    server.executor = executor
    server.createContext("/") { exchange ->
        try {
            route(database, exchange)
        } catch (error: Exception) {
            error.printStackTrace(System.err)
            respond(exchange, 500, mapOf("error" to 1))
        } finally {
            exchange.close()
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
        database.close()
    })
    server.start()
}

fun route(database: Database, exchange: HttpExchange) {
    val params = exchange.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }.associate {
        val (key, value) = it.split('=', limit = 2)
        key to URLDecoder.decode(value, UTF_8)
    }
    val path = exchange.requestURI.path
    if (path == "/health" && exchange.requestMethod == "GET") {
        respond(exchange, 200, mapOf("ready" to 1))
        return
    }
    val run = params["run"].orEmpty()
    val count = params["count"]?.toIntOrNull()
    if (!run.matches(Regex("[a-z0-9-]{1,40}")) || count == null || count !in 1..10_000) {
        respond(exchange, 400, mapOf("error" to 1))
        return
    }
    when {
        path == "/rows" && exchange.requestMethod == "GET" ->
            respond(exchange, 200, database.rows("import_row", run, count))
        path in listOf("/observe", "/insert") && exchange.requestMethod == "POST" ->
            insert(database, exchange, run, count)
        else -> respond(exchange, 404, mapOf("error" to 1))
    }
}

fun insert(database: Database, exchange: HttpExchange, run: String, count: Int) {
    val observe = exchange.requestURI.path == "/observe"
    val duplicate = exchange.requestURI.rawQuery.orEmpty().split('&').contains("duplicate=true")
    val rows = (1..count).map { InputRow(run, it, "value-$it") }.let {
        if (duplicate && !observe) it + it.first() else it
    }
    val meter = InsertMeter()
    var maxManaged = 0
    var status = 200
    val began = System.nanoTime()
    InsertMeter.current.set(meter)
    try {
        database.factory.openSession().use { session ->
            val transaction = session.beginTransaction()
            val work = ImportSession(session)
            try {
                if (observe) {
                    rows.forEach { row -> session.persist(ObservedRow(row.runKey, row.ordinal, row.payload)) }
                    maxManaged = session.statistics.entityCount
                } else {
                    Importer().insert(work, rows)
                }
                transaction.commit()
            } catch (error: Exception) {
                if (transaction.isActive) transaction.rollback()
                if (error is ConstraintViolationException) status = 409 else throw error
            } finally {
                if (!observe) maxManaged = work.maxManaged
            }
        }
    } finally {
        InsertMeter.current.remove()
    }
    val result = database.rows(if (observe) "observed_row" else "import_row", run, count) + mapOf(
        "singles" to meter.singles, "batches" to meter.batches, "batch_rows" to meter.batchRows,
        "max_managed" to maxManaged, "elapsed_ms" to (System.nanoTime() - began) / 1e6,
    )
    respond(exchange, status, result)
}

fun respond(exchange: HttpExchange, status: Int, values: Map<String, Number>) {
    val body = values.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" }.toByteArray(UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, body.size.toLong())
    exchange.responseBody.use { it.write(body) }
}
