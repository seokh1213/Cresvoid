package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.apache.spark.sql.SparkSession

private val cases = setOf("sample", "alternate")

fun startService() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    val spark = SparkSession.builder().appName("null-semantics").master("local[2]")
        .config("spark.ui.enabled", "false")
        .config("spark.sql.shuffle.partitions", "2")
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.sql.ansi.enabled", "true")
        .getOrCreate()
    spark.sparkContext().setLogLevel("OFF")
    installFixtures(spark)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 32)
    server.executor = executor
    server.createContext("/") { exchange ->
        try {
            route(exchange, spark)
        } catch (error: Exception) {
            System.err.println("Request failed: ${error.javaClass.simpleName}")
            exchange.respond(500, "request failed\n")
        } finally {
            exchange.close()
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.close()
        spark.stop()
    })
    server.start()
}

private fun route(exchange: HttpExchange, spark: SparkSession) {
    if (exchange.requestMethod != "GET") {
        exchange.respond(405, "GET required\n")
        return
    }
    val path = exchange.requestURI.path
    if (path == "/health") {
        exchange.respond(200, "ready\n")
        return
    }
    if (path != "/observe" && path != "/repair") {
        exchange.respond(404, "unknown path\n")
        return
    }
    val case = exchange.requestURI.rawQuery?.takeIf { it.startsWith("case=") }?.removePrefix("case=")
    if (case == null || case !in cases) {
        exchange.respond(400, "unknown case\n")
        return
    }
    val body = synchronized(spark) {
        val queries = if (path == "/observe") observations(spark, case) else repairedQueries(spark, case)
        renderBundle(queries)
    }
    exchange.respond(200, body)
}

private fun HttpExchange.respond(status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.write(bytes)
}
