package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.function.Supplier
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskRejectedException
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration(proxyBeanMethods = false)
@EnableAsync(proxyTargetClass = true)
open class AsyncConfiguration

class Lane(val name: String, val executor: ThreadPoolTaskExecutor) : AutoCloseable {
    val state = WorkState()
    private val context = AnnotationConfigApplicationContext()
    val worker: Worker

    init {
        executor.setThreadNamePrefix("async-$name-")
        executor.setAwaitTerminationSeconds(2)
        context.register(AsyncConfiguration::class.java)
        context.registerBean("taskExecutor", ThreadPoolTaskExecutor::class.java, Supplier { executor })
        context.registerBean(Worker::class.java, Supplier { Worker(state) })
        context.refresh()
        worker = context.getBean(Worker::class.java)
    }

    fun submit(id: Int): Int {
        val job = Job(id, state.millis())
        if (state.jobs.putIfAbsent(id, job) != null) return 409
        return try {
            worker.execute(job)
            202
        } catch (_: TaskRejectedException) {
            state.jobs.remove(id)
            503
        }
    }

    fun stats(): String = mapOf(
        "accepted" to state.jobs.size,
        "running" to state.running.get(),
        "completed" to state.completed.get(),
        "peak" to state.peak.get(),
        "queued" to executor.threadPoolExecutor.queue.size,
        "threads" to executor.threadPoolExecutor.largestPoolSize,
        "failed" to state.failed.get(),
    ).entries.joinToString("\n") { "${it.key}=${it.value}" }

    fun events(): String = state.jobs.values.sortedBy { it.id }.joinToString("\n") {
        "${it.id},${it.submitted},${it.started},${it.finished},${it.thread}"
    }

    override fun close() {
        state.gate.countDown()
        context.close()
    }
}

fun observationExecutor(capacity: Int): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
    corePoolSize = 4
    maxPoolSize = 8
    queueCapacity = capacity
    setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
}

fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

fun route(exchange: HttpExchange, lanes: Map<String, Lane>) {
    exchange.use {
        val path = exchange.requestURI.path
        if (path == "/health") {
            respond(exchange, if (exchange.requestMethod == "GET") 200 else 405, "ready")
            return
        }
        val parameters = exchange.requestURI.rawQuery.orEmpty().split('&')
            .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { it[0] to it[1] }
        val lane = lanes[parameters["lane"]]
        if (lane == null) {
            respond(exchange, 400, "unknown lane")
            return
        }
        val method = exchange.requestMethod
        when {
            path == "/jobs" && method == "POST" -> {
                val id = parameters["id"]?.toIntOrNull()
                val status = if (id == null || id < 0) 400 else lane.submit(id)
                respond(exchange, status, status.toString())
            }
            path == "/release" && method == "POST" -> {
                lane.state.gate.countDown()
                respond(exchange, 200, "released")
            }
            path == "/stats" && method == "GET" -> respond(exchange, 200, lane.stats())
            path == "/events" && method == "GET" -> respond(exchange, 200, lane.events())
            path in setOf("/jobs", "/release", "/stats", "/events") -> respond(exchange, 405, "method")
            else -> respond(exchange, 404, "path")
        }
    }
}

fun main() {
    val lanes = listOf(
        Lane("wide", observationExecutor(Int.MAX_VALUE)),
        Lane("direct", observationExecutor(0)),
        Lane("buffered", observationExecutor(8)),
        Lane("tuned-small", tunedExecutor()),
        Lane("tuned-mid", tunedExecutor()),

        Lane("tuned", tunedExecutor()),
    ).associateBy { it.name }
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", System.getenv("PORT").toInt()), 128)
    val requests = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = requests
    server.createContext("/") { exchange -> route(exchange, lanes) }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        lanes.values.forEach { it.close() }
        requests.shutdownNow()
    })
    server.start()
}
