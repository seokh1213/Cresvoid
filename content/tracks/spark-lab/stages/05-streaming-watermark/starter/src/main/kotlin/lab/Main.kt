package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.apache.spark.sql.SparkSession
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val spark = SparkSession.builder().appName("watermark-lab").master("local[2]")
        .config("spark.ui.enabled", "false")
        .config("spark.sql.shuffle.partitions", "2")
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.sql.streaming.noDataMicroBatches.enabled", "true")
        .getOrCreate()
    spark.sparkContext().setLogLevel("ERROR")
    val application = Application(spark)
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", System.getenv("PORT")?.toInt() ?: 8080), 0)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/") { exchange -> application.handle(exchange) }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        application.close()
        executor.close()
        spark.stop()
    })
    server.start()
}

class Application(private val spark: SparkSession) : AutoCloseable {
    private var session: StreamSession? = null

    @Synchronized
    fun handle(exchange: HttpExchange) {
        try {
            val method = exchange.requestMethod
            val path = exchange.requestURI.path
            val body = exchange.requestBody.bufferedReader().readText().trim()
            when {
                method == "GET" && path == "/health" -> reply(exchange, 200, "ready")
                method == "POST" && path == "/open" -> {
                    require(body in setOf("append", "update"))
                    session?.close()
                    session = StreamSession(spark, body)
                    reply(exchange, 200, "opened")
                }
                method == "POST" && path in setOf("/batch", "/fail") -> {
                    val minutes = body.split(',').map { it.trim().toInt() }
                    val passed = requireNotNull(session).accept(minutes, path == "/fail")
                    reply(exchange, if (passed) 200 else 503, requireNotNull(session).snapshot())
                }
                method == "POST" && path == "/restart" -> {
                    val passed = requireNotNull(session).drain()
                    reply(exchange, if (passed) 200 else 503, requireNotNull(session).snapshot())
                }
                method == "GET" && path == "/snapshot" -> reply(exchange, 200, requireNotNull(session).snapshot())
                else -> reply(exchange, 404, "unknown route")
            }
        } catch (_: IllegalArgumentException) {
            reply(exchange, 400, "invalid input")
        } catch (failure: Exception) {
            failure.printStackTrace(System.err)
            reply(exchange, 500, "stream failed")
        } finally {
            exchange.close()
        }
    }

    private fun reply(exchange: HttpExchange, status: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Synchronized
    override fun close() {
        session?.close()
    }
}
