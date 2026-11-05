package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.apache.spark.sql.SparkSession
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors

private fun HttpExchange.respond(status: Int, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}

private fun parameters(query: String?): RunInput {
    val values = requireNotNull(query).split('&').associate {
        val pair = it.split('=')
        require(pair.size == 2)
        pair[0] to pair[1].toInt()
    }
    require(values.keys == setOf("size", "cut"))
    val size = values.getValue("size")
    val cut = values.getValue("cut")
    require(size in 2..100 && cut in 1 until size)
    return RunInput(size, cut)
}

fun main() {
    val spark = SparkSession.builder().appName("retries-and-side-effects")
        .master("local[2]").config("spark.ui.enabled", "false")
        .config("spark.driver.host", "127.0.0.1")
        .config("spark.driver.bindAddress", "127.0.0.1")
        .config("spark.speculation", "true")
        .config("spark.speculation.interval", "100ms")
        .config("spark.speculation.quantile", "0.5")
        .config("spark.speculation.multiplier", "1.5")
        .config("spark.speculation.minTaskRuntime", "100ms")
        .config("spark.speculation.efficiency.enabled", "false")
        .getOrCreate()
    spark.sparkContext().setLogLevel("ERROR")
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress(System.getenv("PORT")?.toInt() ?: 8080), 0)
    val lock = Any()
    server.executor = executor
    server.createContext("/health") { exchange -> exchange.respond(200, "ready\n") }
    server.createContext("/run") { exchange ->
        if (exchange.requestMethod != "POST") {
            exchange.respond(405, "POST required\n")
        } else {
            val input = runCatching { parameters(exchange.requestURI.rawQuery) }.getOrNull()
            if (input == null) exchange.respond(400, "invalid parameters\n")
            else synchronized(lock) {
                val directory = Files.createTempDirectory("spark-effects-").toFile()
                try {
                    exchange.respond(200, observe(spark, input, directory.toPath()))
                } catch (error: Exception) {
                    error.printStackTrace(System.err)
                    exchange.respond(500, "experiment failed\n")
                } finally {
                    directory.deleteRecursively()
                }
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.close()
        spark.stop()
    })
    server.start()
}
