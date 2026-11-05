import com.sun.net.httpserver.HttpServer
import java.io.BufferedWriter
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val errorLog = File("build/export-errors.log").apply { writeText("") }
    val elastic = Elastic(requireNotNull(System.getenv("ES_URL")))
    val index = requireNotNull(System.getenv("ES_INDEX"))
    val server = HttpServer.create(
        InetSocketAddress(InetAddress.getLoopbackAddress(), requireNotNull(System.getenv("PORT")).toInt()), 0,
    )
    server.executor = Executors.newVirtualThreadPerTaskExecutor()
    server.createContext("/health") { exchange ->
        exchange.sendResponseHeaders(200, 2)
        exchange.responseBody.use { it.write("OK".toByteArray()) }
    }
    server.createContext("/export") { exchange ->
        exchange.responseHeaders.set("Content-Type", "text/tab-separated-values; charset=utf-8")
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.bufferedWriter().use { output ->
            try {
                export(elastic, index, output)
            } catch (error: Exception) {
                val httpError = Regex("Elasticsearch HTTP [0-9]{3}").matchEntire(error.message.orEmpty())?.value
                val message = "Export failed: ${httpError ?: error.javaClass.simpleName}"
                System.err.println(message)
                errorLog.appendText("$message\n")
                output.write("ERROR\n")
            }
        }
    }
    server.start()
}

fun writeProduct(hit: JsonValue, output: BufferedWriter) {
    val source = hit.obj().getValue("_source").obj()
    val id = (source.getValue("id") as JsonNumber).literal
    val revision = (source.getValue("revision") as JsonNumber).literal
    output.write("$id\t$revision\t${source.getValue("payload").text()}\n")
}
