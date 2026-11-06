package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Worker : AutoCloseable {
    private var process: Process? = null

    @Synchronized
    fun start(crashEvent: String): String {
        if (process?.isAlive == true) return "running"
        process = ProcessBuilder(
            "java", "-Xmx128m", "-cp", System.getProperty("java.class.path"), "lab.MainKt", "consume",
        ).apply {
            environment()["CRASH_EVENT"] = crashEvent
            inheritIO()
        }.start()
        return "started"
    }

    @Synchronized
    fun status(): String = process?.let { if (it.isAlive) "running" else "dead:${it.exitValue()}" } ?: "idle"

    @Synchronized
    override fun close() {
        process?.let {
            it.destroy()
            if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroyForcibly().waitFor()
        }
    }
}

class Service(private val admin: AdminClient, private val producer: KafkaProducer<String, String>) : AutoCloseable {
    private val worker = Worker()

    fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val method = when (path) {
            "/health", "/status", "/progress", "/state" -> "GET"
            "/events", "/control/start" -> "POST"
            else -> { reply(exchange, 404, "missing"); return }
        }
        if (exchange.requestMethod != method) { reply(exchange, 405, "method"); return }
        try {
            route(exchange, path)
        } catch (_: IllegalArgumentException) {
            reply(exchange, 400, "invalid")
        } catch (failure: Exception) {
            failure.printStackTrace(System.err)
            reply(exchange, 503, "unavailable")
        }
    }

    private fun route(exchange: HttpExchange, path: String) {
        val body = when (path) {
            "/health" -> { Postgres().use { it.query("SELECT 1") }; "ready" }
            "/status" -> worker.status()
            "/events" -> {
                val event = Event.parse(readBody(exchange))
                producer.send(ProducerRecord("events", event.entity, event.encode())).get(15, TimeUnit.SECONDS)
                "queued:${event.id}"
            }
            "/control/start" -> {
                val crash = readBody(exchange)
                if (crash.isNotEmpty()) sqlId(crash)
                worker.start(crash)
            }
            "/progress" -> progress()
            "/state" -> Postgres().use { db ->
                db.query("SELECT entity_id,version,value,total FROM entities ORDER BY entity_id")
                    .joinToString("\n") { it.joinToString("|") }
            }
            else -> error("Unrouted path")
        }
        reply(exchange, if (path == "/events") 202 else 200, body)
    }

    private fun progress(): String {
        val partitions = (0..2).map { TopicPartition("events", it) }
        val end = admin.listOffsets(partitions.associateWith { OffsetSpec.latest() }).all().get(10, TimeUnit.SECONDS)
            .values.sumOf { it.offset() }
        val committed = admin.listConsumerGroupOffsets("projection").partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS)
            .values.sumOf { it.offset() }
        return "$committed|$end"
    }

    private fun readBody(exchange: HttpExchange): String {
        val bytes = exchange.requestBody.readNBytes(1025)
        require(bytes.size <= 1024)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun reply(exchange: HttpExchange, status: Int, text: String) {
        exchange.use {
            val bytes = text.toByteArray()
            it.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            it.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) it.responseBody.write(bytes)
        }
    }

    override fun close() = worker.close()
}

fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("consume"))) { consume(); return }
    initializeDatabase()
    val bootstrap = System.getenv("KAFKA_BOOTSTRAP") ?: "kafka:9092"
    val admin = AdminClient.create(mapOf("bootstrap.servers" to bootstrap))
    admin.createTopics(listOf(NewTopic("events", 3, 1))).all().get(20, TimeUnit.SECONDS)
    val producer = KafkaProducer<String, String>(mapOf(
        "bootstrap.servers" to bootstrap, "acks" to "all",
        "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
        "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
    ))
    val service = Service(admin, producer)
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", (System.getenv("PORT") ?: "8080").toInt()), 128)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/", service::handle)
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        service.close()
        executor.shutdownNow()
        producer.close()
        admin.close()
    })
    server.start()
}
