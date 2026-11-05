import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.system.exitProcess

val mib = 1_048_576L
data class Profile(val ram: Int, val percentage: Int?, val xmx: Int? = null, val directLimit: Int = 384) {
    val expectedHeap: Long get() = (xmx ?: (ram * (percentage ?: 25) / 100)) * 1_048_576L
}
data class Outcome(val heapOom: Int = 0, val directOom: Int = 0, val exceeded: Int = 0)
data class Sample(val profile: Profile, val heap: Int, val direct: Int = 0, val outcome: Outcome = Outcome())

val defaultProfile = Profile(1024, null)
val configuredProfile = Profile(1024, 50)
val smallerProfile = Profile(512, 50)
val explicitProfile = Profile(1024, 50, xmx = 128)
val nativeProfile = Profile(256, 50)
val samples = listOf(
    Sample(defaultProfile, 32),
    Sample(defaultProfile, 384, outcome = Outcome(heapOom = 1)),
    Sample(configuredProfile, 32),
    Sample(configuredProfile, 320),
    Sample(configuredProfile, 640, outcome = Outcome(heapOom = 1)),
    Sample(smallerProfile, 32),
    Sample(smallerProfile, 320, outcome = Outcome(heapOom = 1)),
    Sample(explicitProfile, 32),
    Sample(explicitProfile, 192, outcome = Outcome(heapOom = 1)),
    Sample(nativeProfile, 80, 192, Outcome(exceeded = 1)),
    Sample(nativeProfile, 16),
    Sample(Profile(256, 50, directLimit = 16), 16, 32, Outcome(directOom = 1)),
)
val fields = setOf(
    "max_heap_bytes", "heap_requested_bytes", "heap_retained_bytes",
    "direct_requested_bytes", "direct_retained_bytes", "heap_oom", "direct_oom", "budget_exceeded",
)
val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
val active = AtomicReference<Process?>()

fun stop(process: Process) {
    val children = process.toHandle().descendants().use { it.toList() }
    children.forEach { it.destroy() }
    process.destroy()
    process.waitFor(2, TimeUnit.SECONDS)
    children.filter { it.isAlive }.forEach { it.destroyForcibly() }
    if (process.isAlive) process.destroyForcibly()
    process.waitFor(2, TimeUnit.SECONDS)
}

Runtime.getRuntime().addShutdownHook(Thread { active.getAndSet(null)?.let(::stop) })

fun launch(program: File, profile: Profile, port: Int): Process {
    val options = buildList {
        addAll(listOf("-XX:+UseG1GC", "-XX:G1HeapRegionSize=1m", "-XX:ActiveProcessorCount=2", "-Xms16m"))
        add("-XX:MaxRAM=${profile.ram}m")
        add("-XX:MaxDirectMemorySize=${profile.directLimit}m")
        profile.percentage?.let { add("-XX:MaxRAMPercentage=$it") }
        profile.xmx?.let { add("-Xmx${it}m") }
    }
    val builder = ProcessBuilder(File(program, "run.sh").absolutePath).directory(program)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
    builder.environment().apply {
        remove("JDK_JAVA_OPTIONS")
        remove("_JAVA_OPTIONS")
        put("JAVA_HOME", System.getProperty("java.home"))
        put("PATH", "${System.getProperty("java.home")}/bin:${get("PATH").orEmpty()}")
        put("JAVA_TOOL_OPTIONS", options.joinToString(" "))
        put("PORT", port.toString())
    }
    return builder.start()
}

fun get(uri: String, timeout: Duration): HttpResponse<String> = client.send(
    HttpRequest.newBuilder(URI.create(uri)).timeout(timeout).GET().build(),
    HttpResponse.BodyHandlers.ofString(),
)

fun awaitReady(process: Process, base: String) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
    while (System.nanoTime() < deadline && process.isAlive) {
        val response = try {
            get("$base/health", Duration.ofMillis(500))
        } catch (_: IOException) {
            null
        }
        if (response?.statusCode() == 200 && response.body() == "ready\n") return
        Thread.sleep(50)
    }
    error("Service did not become ready")
}

fun parse(body: String): Map<String, Long>? {
    val lines = body.trimEnd('\n', '\r').lines()
    if (lines.size != fields.size) return null
    val result = mutableMapOf<String, Long>()
    for (line in lines) {
        val parts = line.split('=')
        if (parts.size != 2 || parts[0] !in fields || parts[0] in result) return null
        val value = parts[1].toLongOrNull() ?: return null
        if (value < 0 || (parts[0] in setOf("heap_oom", "direct_oom", "budget_exceeded") && value > 1)) return null
        result[parts[0]] = value
    }
    return result
}

fun observe(program: File, sample: Sample): Map<String, Long>? {
    val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    val process = launch(program, sample.profile, port)
    active.set(process)
    try {
        val base = "http://127.0.0.1:$port"
        awaitReady(process, base)
        val arrival = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)
        while (System.nanoTime() < arrival) {
            TimeUnit.NANOSECONDS.sleep((arrival - System.nanoTime()).coerceAtLeast(1))
        }
        val response = try {
            get("$base/probe?heapMiB=${sample.heap}&directMiB=${sample.direct}&budgetMiB=${sample.profile.ram}", Duration.ofSeconds(15))
        } catch (failure: HttpTimeoutException) {
            throw failure
        } catch (_: IOException) {
            return null
        }
        return if (response.statusCode() == 200) parse(response.body()) else null
    } finally {
        stop(process)
        active.compareAndSet(process, null)
    }
}

class Statistics {
    var responseFailures = 0
    var heapMismatches = 0
    var allocationMismatches = 0
    var budgetMismatches = 0
    var heapOom = 0L
    var directOom = 0L
    var exceeded = 0L
    var defaultHeap = 0.0
    var configuredHeap = 0.0

    fun record(index: Int, sample: Sample, response: Map<String, Long>?) {
        if (response == null) {
            responseFailures++
            return
        }
        val maxHeap = response.getValue("max_heap_bytes")
        if (abs(maxHeap - sample.profile.expectedHeap) > 8 * mib) heapMismatches++
        val expected = mapOf(
            "heap_requested_bytes" to sample.heap * mib,
            "direct_requested_bytes" to sample.direct * mib,
            "heap_retained_bytes" to if (sample.outcome.heapOom == 0) sample.heap * mib else 0L,
            "direct_retained_bytes" to if (sample.outcome.directOom == 0) sample.direct * mib else 0L,
            "heap_oom" to sample.outcome.heapOom.toLong(),
            "direct_oom" to sample.outcome.directOom.toLong(),
        )
        if (expected.any { (key, value) -> response.getValue(key) != value }) allocationMismatches++
        if (response.getValue("budget_exceeded") != sample.outcome.exceeded.toLong()) budgetMismatches++
        heapOom += response.getValue("heap_oom")
        directOom += response.getValue("direct_oom")
        exceeded += response.getValue("budget_exceeded")
        if (index == 0) defaultHeap = maxHeap.toDouble() / mib
        if (index == 2) configuredHeap = maxHeap.toDouble() / mib
    }

    fun report(): Int {
        val metrics = linkedMapOf<String, Number>(
            "requests" to samples.size,
            "response_failures" to responseFailures,
            "heap_mismatches" to heapMismatches,
            "allocation_mismatches" to allocationMismatches,
            "budget_mismatches" to budgetMismatches,
            "heap_oom_count" to heapOom,
            "direct_oom_count" to directOom,
            "budget_exceeded_count" to exceeded,
            "default_max_heap_mib" to defaultHeap,
            "configured_max_heap_mib" to configuredHeap,
        )
        metrics.forEach { (name, value) -> println("METRIC $name $value") }
        val matched = responseFailures == 0 && heapMismatches == 0 && allocationMismatches == 0 && budgetMismatches == 0
        return if (matched && heapOom == 4L && directOom == 1L && exceeded == 1L) 0 else 1
    }
}

val result = try {
    require(args.size == 2 && args[1] == "observation-cycle")
    require(Runtime.version().feature() == 21)
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute())
    val statistics = Statistics()
    samples.forEachIndexed { index, sample -> statistics.record(index, sample, observe(program, sample)) }
    statistics.report()
} catch (failure: Exception) {
    System.err.println("Experiment error: ${failure.javaClass.simpleName}")
    2
} finally {
    active.getAndSet(null)?.let(::stop)
}
exitProcess(result)
