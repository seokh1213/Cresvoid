package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.net.InetSocketAddress
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun HttpExchange.reply(status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

fun main() {
    val bootstrap = System.getenv("KAFKA_BOOTSTRAP") ?: "kafka:9092"
    Admin.create(mapOf("bootstrap.servers" to bootstrap)).use { admin ->
        admin.createTopics(listOf(NewTopic("work-queue", 1, 1.toShort())))
            .all().get(30, TimeUnit.SECONDS)
    }
    val producer = KafkaProducer<String, String>(Properties().apply {
        put("bootstrap.servers", bootstrap)
        put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("acks", "all")
        put("enable.idempotence", "true")
        put("max.block.ms", "10000")
        put("delivery.timeout.ms", "15000")
        put("request.timeout.ms", "5000")
    })
    val work = Work()
    val loop = ConsumerLoop(bootstrap, work)
    val accepted = ConcurrentHashMap.newKeySet<Int>()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", System.getenv("PORT").toInt()), 0)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/") { exchange ->
        try {
            when (exchange.requestMethod to exchange.requestURI.path) {
                "GET" to "/health" -> exchange.reply(200, "ready")
                "POST" to "/messages" -> enqueue(exchange, producer, accepted)
                "POST" to "/start" -> { loop.start(); exchange.reply(200, "started") }
                "POST" to "/restart" -> { loop.close(); loop.start(); exchange.reply(200, "restarted") }
                "POST" to "/stop" -> { loop.close(); exchange.reply(200, "stopped") }
                "GET" to "/stats" -> exchange.reply(200, "accepted=${accepted.size}\n" + work.snapshot())
                else -> exchange.reply(404, "unknown route")
            }
        } catch (error: Exception) {
            error.printStackTrace(System.err)
            exchange.reply(500, "request failed")
        } finally {
            exchange.close()
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        loop.close()
        producer.close()
        executor.close()
    })
    server.start()
}

private fun enqueue(exchange: HttpExchange, producer: KafkaProducer<String, String>, accepted: MutableSet<Int>) {
    val id = exchange.requestBody.bufferedReader().readText().toIntOrNull()
    if (id == null || id !in 0..11) {
        exchange.reply(400, "expected id 0..11")
        return
    }
    if (!accepted.add(id)) {
        exchange.reply(409, "duplicate id")
        return
    }
    producer.send(ProducerRecord("work-queue", 0, id.toString(), id.toString())).get(20, TimeUnit.SECONDS)
    exchange.reply(202, id.toString())
}
