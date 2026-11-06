package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.zaxxer.hikari.HikariDataSource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

fun main() {
    val context = AnnotationConfigApplicationContext(Configuration::class.java)
    val observation = Observation(context.getBean(HikariDataSource::class.java), context.getBean(JdbcTemplate::class.java))
    val pipeline = context.getBean(Pipeline::class.java)
    val batch = AtomicReference(Batch())
    val executor = Executors.newFixedThreadPool(12)
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
    server.executor = executor
    server.createContext("/") { exchange -> route(exchange, pipeline, observation, batch) }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
        observation.close()
        context.close()
    })
    server.start()
}

private fun route(exchange: HttpExchange, pipeline: Pipeline, observation: Observation, batch: AtomicReference<Batch>) {
    exchange.use {
        try {
            when (exchange.requestMethod to exchange.requestURI.path) {
                "GET" to "/health" -> exchange.reply(200, "ready")
                "GET" to "/gate" -> exchange.reply(200, batch.get().readyCount.get().toString())
                "POST" to "/batch" -> {
                    val name = exchange.requestBody.bufferedReader().readText()
                    require(name.matches(Regex("[a-zA-Z0-9-]{1,80}")))
                    batch.set(Batch())
                    exchange.reply(200, "created")
                }
                "POST" to "/release" -> {
                    require(batch.get().readyCount.get() == 4)
                    batch.get().release()
                    exchange.reply(200, "released")
                }
                "POST" to "/work" -> execute(exchange, pipeline, observation, batch.get())
                "GET" to "/state" -> exchange.reply(200, observation.state(batch.get()))
                "GET" to "/events" -> exchange.reply(200, observation.events.toSortedMap().values.joinToString("\n"))
                "GET" to "/records" -> exchange.reply(200, observation.records())
                else -> exchange.reply(404, "missing")
            }
        } catch (_: IllegalArgumentException) {
            exchange.reply(400, "invalid")
        } catch (_: RuntimeException) {
            exchange.reply(500, "error")
        }
    }
}

private fun execute(exchange: HttpExchange, pipeline: Pipeline, observation: Observation, batch: Batch) {
    val query = exchange.requestURI.rawQuery.orEmpty().split('&').associate { part ->
        val pieces = part.split('=', limit = 2)
        require(pieces.size == 2)
        pieces[0] to URLDecoder.decode(pieces[1], Charsets.UTF_8)
    }
    val id = requireNotNull(query["id"])
    require(id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
    val rollback = requireNotNull(query["rollback"]).toBooleanStrict()
    val job = Job(id, rollback, batch)
    val result = try {
        pipeline.process(job)
        200 to "committed"
    } catch (_: PlannedRollback) {
        200 to "rolled_back"
    } catch (_: RuntimeException) {
        503 to "failed"
    } finally {
        observation.events[id] = "$id|${Thread.currentThread().name}|${job.auditMillis}|${job.auditError}"
        batch.finishedCount.incrementAndGet()
    }
    exchange.reply(result.first, result.second)
}

private fun HttpExchange.reply(status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
