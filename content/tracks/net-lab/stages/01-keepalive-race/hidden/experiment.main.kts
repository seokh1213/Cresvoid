@file:Import("../../../../../lab-env/load.main.kts")

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

data class Receipt(val connection: Int, val method: String, val id: String)

class IdleServer : AutoCloseable {
    private val listener = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int = listener.localPort
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val nextConnection = AtomicInteger()
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    val receipts = ConcurrentLinkedQueue<Receipt>()
    val errors = ConcurrentLinkedQueue<Exception>()
    val idleCloses = AtomicInteger()

    init {
        executor.submit {
            try {
                while (!listener.isClosed) {
                    val socket = listener.accept()
                    sockets.add(socket)
                    val connection = nextConnection.incrementAndGet()
                    executor.submit { serve(socket, connection) }
                }
            } catch (error: SocketException) {
                if (!listener.isClosed) errors.add(error)
            }
        }
    }

    private fun serve(socket: Socket, connection: Int) {
        try {
            socket.use {
                socket.soTimeout = 5_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
                while (true) {
                    val line = reader.readLine() ?: return
                    val parts = line.split(' ')
                    require(parts.size == 3 && parts[2] == "HTTP/1.1")
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val header = reader.readLine() ?: return
                        if (header.isEmpty()) break
                        headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
                    }
                    require(headers["content-length"] == "0")
                    if (!respond(socket, connection, parts)) return
                }
            }
        } catch (_: SocketTimeoutException) {
            idleCloses.incrementAndGet()
        } catch (_: IOException) {
            // The client may retire an idle socket while the fixture is reading it.
        } catch (error: Exception) {
            errors.add(error)
        } finally {
            sockets.remove(socket)
        }
    }

    private fun respond(socket: Socket, connection: Int, parts: List<String>): Boolean {
        val uri = URI.create(parts[1])
        require(uri.path == "/resource")
        val query = uri.rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        val id = requireNotNull(query["id"])
        val fault = requireNotNull(query["fault"])
        val method = parts[0]
        require(method in setOf("GET", "POST") && fault in setOf("none", "once", "always"))
        val count = counts.computeIfAbsent("$method:$id") { AtomicInteger() }.incrementAndGet()
        receipts.add(Receipt(connection, method, id))
        if (fault == "always" || fault == "once" && count == 1) return false
        val body = if (method == "GET") "GET:$id" else "POST:$id:$count"
        val response = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n\r\n$body"
        socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().flush()
        return true
    }

    fun count(method: String, id: String): Int = counts["$method:$id"]?.get() ?: 0

    override fun close() {
        listener.close()
        sockets.forEach { runCatching { it.close() } }
        executor.shutdownNow()
        check(executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS))
    }
}

fun relayRequest(base: String, id: String, lane: String = "browse", fault: String = "none"): java.net.http.HttpRequest =
    get(base, "/relay?lane=$lane&id=$id&fault=$fault", Duration.ofSeconds(8))

fun valid(outcome: Outcome, status: Int, body: String): Boolean =
    outcome.error == null && outcome.status == status && outcome.body == body

fun experiment(program: File): Boolean {
    IdleServer().use { upstream ->
        val port = freePort()
        val base = "http://127.0.0.1:$port"
        Program(program, mapOf("PORT" to "$port", "UPSTREAM_PORT" to "${upstream.port}"), base).use {
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().use { client ->
                val schedule = listOf(0L, 300L, 600L, 5_100L, 10_600L, 15_500L, 20_600L)
                val browsing = runLoad(client, schedule.mapIndexed { index, at ->
                    Arrival(at, relayRequest(base, "page-$index"))
                })
                val faults = runLoad(client, listOf(
                    Arrival(0, relayRequest(base, "read-once", "lookup", "once")),
                    Arrival(400, relayRequest(base, "read-always", "offline", "always")),
                    Arrival(800, post(base, "/relay?lane=checkout&id=write-once&fault=once", "", Duration.ofSeconds(8))),
                    Arrival(1_200, post(base, "/relay?lane=checkout&id=write-next&fault=none", "", Duration.ofSeconds(8))),
                    Arrival(1_600, relayRequest(base, "read-next", "offline"))
                ))
                val idleWrite = runLoad(client, listOf(
                    Arrival(6_000, post(base, "/relay?lane=checkout&id=write-idle&fault=none", "", Duration.ofSeconds(8)))
                )).single()
                val outcomes = browsing + faults + idleWrite
                check(outcomes.none { it.error?.endsWith("TimeoutException") == true }) {
                    "Public request exceeded the 8-second limit"
                }
                check(upstream.errors.isEmpty()) { "Invalid fixture protocol: ${upstream.errors.firstOrNull()}" }
                val normalFailures = browsing.withIndex().count { (index, outcome) ->
                    !valid(outcome, 200, "GET:page-$index")
                }
                val readRecoveryFailures = if (valid(faults[0], 200, "GET:read-once") &&
                    upstream.count("GET", "read-once") == 2) 0 else 1
                val budgetFailures = if (valid(faults[1], 502, "upstream-unavailable") &&
                    upstream.count("GET", "read-always") == 2) 0 else 1
                val writeFailures = if (valid(faults[2], 502, "upstream-unavailable") &&
                    upstream.count("POST", "write-once") == 1) 0 else 1
                val recoveryFailures = listOf(
                    valid(faults[3], 200, "POST:write-next:1") && upstream.count("POST", "write-next") == 1,
                    valid(faults[4], 200, "GET:read-next") && upstream.count("GET", "read-next") == 1,
                    valid(idleWrite, 200, "POST:write-idle:1") && upstream.count("POST", "write-idle") == 1
                ).count { !it }
                val initialIds = setOf("page-0", "page-1", "page-2")
                val initial = upstream.receipts.filter { it.id in initialIds }
                val reuseFailures = if (initial.map { it.id }.toSet() == initialIds &&
                    initial.map { it.connection }.distinct().size == 1) 0 else 1
                val normalCountFailures = (0..6).count { upstream.count("GET", "page-$it") != 1 }
                metric("normal_failures", normalFailures)
                metric("read_recovery_failures", readRecoveryFailures)
                metric("retry_budget_failures", budgetFailures)
                metric("write_safety_failures", writeFailures)
                metric("recovery_failures", recoveryFailures)
                metric("reuse_failures", reuseFailures)
                metric("normal_count_failures", normalCountFailures)
                metric("write_applications", upstream.count("POST", "write-once"))
                metric("server_idle_closes", upstream.idleCloses.get())
                metric("p95_ms", percentile(outcomes.map { it.elapsedMillis }, 0.95))
                return normalFailures + readRecoveryFailures + budgetFailures + writeFailures +
                    recoveryFailures + reuseFailures + normalCountFailures == 0
            }
        }
    }
}

try {
    require(args.size == 2 && args[1] == "session-traffic")
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute())
    exitProcess(if (experiment(program)) 0 else 1)
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.message}")
    exitProcess(2)
}
