package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.apache.spark.sql.SparkSession

fun main() {
    val spark = SparkSession.builder().appName("daily-partition-writes").master("local[2]")
        .config("spark.ui.enabled", "false")
        .config("spark.sql.adaptive.enabled", "false")
        .config("spark.sql.shuffle.partitions", "4")
        .config("spark.sql.sources.useV1SourceList", "parquet")
        .config("spark.sql.sources.partitionColumnTypeInference.enabled", "false")
        .config("spark.sql.warehouse.dir", "/tmp/daily-warehouse")
        .getOrCreate()
    spark.sparkContext().setLogLevel("ERROR")
    val storage = Storage(spark, WritePolicy())
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
    val executor = Executors.newSingleThreadExecutor()
    server.executor = executor
    server.createContext("/health") { exchange -> exchange.reply(200, "ready\n") }
    server.createContext("/overwrite") { exchange ->
        handle(exchange) {
            val mode = exchange.parameters()["mode"]
            require(mode in setOf("STATIC", "DYNAMIC"))
            storage.overwrite(requireNotNull(mode))
        }
    }
    server.createContext("/files") { exchange ->
        handle(exchange) {
            val parameters = exchange.parameters()
            val tasks = parameters["tasks"]?.toIntOrNull()
            val dates = parameters["dates"]?.toIntOrNull()
            val rows = parameters["rows"]?.toIntOrNull()
            val strategy = parameters["strategy"]
            require(tasks in setOf(2, 4) && dates in setOf(2, 3) && rows in setOf(6, 8))
            require(strategy in setOf("plain", "coalesce", "repartition", "capped"))
            storage.files(FileRequest(requireNotNull(tasks), requireNotNull(dates), requireNotNull(rows), requireNotNull(strategy)))
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
        spark.stop()
    })
    server.start()
}

private fun handle(exchange: HttpExchange, operation: () -> String) {
    if (exchange.requestMethod != "POST") {
        exchange.reply(405, "POST required\n")
        return
    }
    try {
        exchange.reply(200, operation())
    } catch (_: IllegalArgumentException) {
        exchange.reply(400, "invalid parameters\n")
    } catch (failure: Exception) {
        failure.printStackTrace(System.err)
        exchange.reply(500, "write failed\n")
    }
}

private fun HttpExchange.parameters(): Map<String, String> =
    requestURI.rawQuery.orEmpty().split('&').filter { it.contains('=') }
        .associate { it.substringBefore('=') to it.substringAfter('=') }

private fun HttpExchange.reply(status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}
