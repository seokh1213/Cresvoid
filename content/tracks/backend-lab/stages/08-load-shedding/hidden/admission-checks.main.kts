import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun finalCounterFlags(snapshot: Map<String, Long>, responses: Long): Int =
    (if (snapshot.getValue("active") != 0L) 1 else 0) +
        (if (snapshot.getValue("waiting") != 0L) 2 else 0) +
        (if (snapshot.getValue("started") != snapshot.getValue("completed")) 4 else 0) +
        (if (snapshot.getValue("completed") != responses) 8 else 0)

class AdmissionApi(programDir: File) : AutoCloseable {
    private val loader = URLClassLoader(
        arrayOf(File(programDir, "build/app.jar").toURI().toURL()),
        Thread.currentThread().contextClassLoader,
    )
    private val serviceClass = loader.loadClass("WorkService")
    private val service = serviceClass.getConstructor().newInstance()
    private val controllerClass = loader.loadClass("AdmissionController")
    private val controller = controllerClass.getConstructor(serviceClass).newInstance(service)
    private val handle = controllerClass.getMethod("handle", Long::class.javaPrimitiveType, Function0::class.java)

    fun call(deadline: Long, work: () -> Unit): Boolean = try {
        handle.invoke(controller, deadline, work) as Boolean
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }

    fun waiting(): Int = (serviceClass.getMethod("snapshot").invoke(service) as String)
        .lineSequence().first { it.startsWith("waiting=") }.substringAfter('=').toInt()

    override fun close() = loader.close()
}

fun controllerFailures(programDir: File): Int = AdmissionApi(programDir).use { api ->
    var failures = 0
    val calls = AtomicInteger()
    if (api.call(System.nanoTime() - 1) { calls.incrementAndGet() } || calls.get() != 0) failures++
    val fault = IllegalStateException("work failure")
    repeat(20) {
        val before = calls.get()
        val outcome = runCatching {
            api.call(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)) {
                calls.incrementAndGet()
                throw fault
            }
        }
        if (calls.get() != before + 1 || outcome.getOrNull() == true) failures++
    }
    if (!api.call(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)) { Thread.sleep(150) }) failures++
    failures + saturatedChecks(api)
}

fun saturatedChecks(api: AdmissionApi): Int {
    val failures = AtomicInteger()
    val entered = CountDownLatch(16)
    val release = CountDownLatch(1)
    val threads = mutableListOf<Thread>()
    fun launch(action: () -> Unit): Thread = Thread.ofVirtual().start {
        try {
            action()
        } catch (_: InterruptedException) {
            // A blocked admission may propagate interruption or return false.
        } catch (_: Throwable) {
            failures.incrementAndGet()
        }
    }.also { threads += it }
    try {
        repeat(16) {
            launch {
                val accepted = api.call(System.nanoTime() + TimeUnit.SECONDS.toNanos(10)) {
                    entered.countDown()
                    if (!release.await(8, TimeUnit.SECONDS)) failures.incrementAndGet()
                }
                if (!accepted) failures.incrementAndGet()
            }
        }
        if (!entered.await(3, TimeUnit.SECONDS)) {
            failures.incrementAndGet()
        } else {
            val expired = launch {
                if (api.call(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)) {
                    failures.incrementAndGet()
                }) failures.incrementAndGet()
            }
            expired.join(1500)
            if (expired.isAlive) failures.incrementAndGet()
            expired.interrupt()
            expired.join(1000)
            repeat(2) {
                val attempted = CountDownLatch(8)
                val waiters = List(8) {
                    launch {
                        attempted.countDown()
                        if (api.call(System.nanoTime() + TimeUnit.SECONDS.toNanos(5)) {
                            failures.incrementAndGet()
                        }) failures.incrementAndGet()
                    }
                }
                if (!attempted.await(1, TimeUnit.SECONDS)) failures.incrementAndGet()
                val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                while (api.waiting() < 8 && waiters.any { it.isAlive } && System.nanoTime() < until) {
                    Thread.sleep(1)
                }
                waiters.forEach { it.interrupt() }
                waiters.forEach { it.join(1000) }
                if (waiters.any { it.isAlive } || api.waiting() != 0) failures.incrementAndGet()
            }
        }
    } finally {
        release.countDown()
        threads.forEach { it.join(1000) }
        threads.filter { it.isAlive }.forEach { it.interrupt() }
        threads.forEach { it.join(1000) }
    }
    if (threads.any { it.isAlive } || api.waiting() != 0) failures.incrementAndGet()
    repeat(16) {
        if (!api.call(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)) {}) failures.incrementAndGet()
    }
    return failures.get()
}

fun httpContractFailures(client: HttpClient, base: String): Int {
    val validId = "boundary"
    val future = (System.currentTimeMillis() + 60_000).toString()
    data class Case(val method: String, val path: String, val id: String?, val deadline: String?, val status: Int, val body: String)
    val cases = listOf(
        Case("POST", "/work", null, future, 400, "invalid"),
        Case("POST", "/work", validId, null, 400, "invalid"),
        Case("POST", "/work", "", future, 400, "invalid"),
        Case("POST", "/work", "x".repeat(65), future, 400, "invalid"),
        Case("POST", "/work", "bad_id", future, 400, "invalid"),
        Case("POST", "/work", validId, "no-time", 400, "invalid"),
        Case("POST", "/work", validId, "9223372036854775808", 400, "invalid"),
        Case("GET", "/work", validId, future, 404, "missing"),
        Case("PUT", "/work", validId, future, 404, "missing"),
        Case("POST", "/missing", validId, future, 404, "missing"),
        Case("POST", "/health", null, null, 404, "missing"),
        Case("GET", "/health", null, null, 200, "ready"),
        Case("POST", "/work", validId, Long.MIN_VALUE.toString(), 503, "busy"),
    )
    return cases.count { case ->
        val request = HttpRequest.newBuilder(URI.create(base + case.path)).timeout(Duration.ofSeconds(3))
            .method(case.method, HttpRequest.BodyPublishers.noBody())
            .apply {
                case.id?.let { header("X-Request-Id", it) }
                case.deadline?.let { header("X-Deadline-Epoch-Ms", it) }
            }.build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        response.statusCode() != case.status || response.body() != case.body ||
            (case.status == 503 && response.headers().firstValue("Retry-After").orElse("") != "1")
    }
}
