package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.apache.spark.sql.SparkSession
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

fun main() {
    val spark = SparkSession.builder().master("local[2]").appName("skew-lab")
        .config("spark.ui.enabled", "false")
        .config("spark.driver.host", "127.0.0.1")
        .config("spark.driver.bindAddress", "127.0.0.1")
        .config("spark.shuffle.compress", "false")
        .getOrCreate()
    spark.sparkContext().setLogLevel("ERROR")
    val log = TaskLog()
    spark.sparkContext().addSparkListener(log)
    val jobs = Executors.newSingleThreadExecutor()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", System.getenv("PORT")?.toInt() ?: 8080), 32)
    server.executor = Executors.newVirtualThreadPerTaskExecutor()
    server.createContext("/health") {
        if (it.requestMethod == "GET") respond(it, 200, "ready\n")
        else respond(it, 400, "invalid request\n")
    }
    server.createContext("/run") { exchange ->
        val query = exchange.requestURI.rawQuery.orEmpty().split('&').associate {
            it.substringBefore('=') to it.substringAfter('=', "")
        }
        val profile = query["profile"] ?: "ledger"
        val mode = query["mode"] ?: "tuned"
        if (exchange.requestMethod != "POST" || profile !in setOf("ledger", "replay") ||
            mode !in setOf("off", "on", "factor", "bytes", "tuned")) {
            respond(exchange, 400, "invalid request\n")
        } else {
            jobs.submit {
                try {
                    configure(spark, mode)
                    log.reset()
                    val tables = tables(spark, profile)
                    val joined = if (mode == "tuned") Tuning.join(spark, tables.facts, tables.rates)
                    else tables.facts.hint("merge").join(tables.rates.hint("merge"), "key")
                        .select("id", "weight", "amount")
                    respond(exchange, 200, measure(joined, log))
                } catch (error: Exception) {
                    error.printStackTrace(System.err)
                    respond(exchange, 500, "job failed\n")
                }
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        jobs.shutdownNow()
        spark.stop()
    })
    server.start()
}
