@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OutboxDiagnostic(private val client: HttpClient, private val base: String) {
    fun request(request: HttpRequest): String {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return "${response.statusCode()} ${response.body()}"
    }

    fun show(request: HttpRequest) {
        println("${request.method()} ${request.uri().path}: ${request(request)}")
    }

    fun await(label: String, path: String, completed: (String) -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos()
        var response: String
        do {
            response = request(get(base, path))
            if (completed(response)) {
                println("$label: $response")
                return
            }
            Thread.sleep(100)
        } while (System.nanoTime() < deadline)
        println("$label: timeout, last=$response")
    }

    fun relay(event: String) {
        show(post(base, "/relay/start", event))
        await("relay", "/relay/status") { it.startsWith("200 done:") }
        await("progress", "/progress") { response ->
            val offsets = response.removePrefix("200 ").split('|').mapNotNull(String::toLongOrNull)
            response.startsWith("200 ") && offsets.size == 2 && offsets[0] == offsets[1]
        }
    }
}

fun snapshot(stack: ComposeStack, label: String) {
    println("\n$label")
    val queries = linkedMapOf(
        "orders" to "SELECT order_id,amount FROM orders ORDER BY order_id",
        "outbox" to "SELECT event_id,order_id,amount,sent FROM outbox ORDER BY event_id",
        "published" to "SELECT event_id,order_id,amount FROM published ORDER BY seq",
        "receipts" to "SELECT offset_no,event_id,order_id,amount FROM receipts ORDER BY offset_no",
        "seen" to "SELECT event_id FROM seen ORDER BY event_id",
        "effects" to "SELECT event_id,order_id,amount FROM effects ORDER BY seq",
    )
    for ((table, query) in queries) {
        val rows = stack.exec("postgres", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "lab", "-d", "lab",
            "-At", "-F", "|", "-c", query).trim()
        println("$table:\n${rows.ifEmpty { "(empty)" }}")
    }
}

require(args.size == 1) { "작업 공간의 절대 경로 하나를 넘겨라." }
val program = File(args[0])
require(program.isAbsolute && File(program, "build/app.jar").isFile) { "작업 공간을 먼저 빌드해라." }
val compose = File("content/tracks/backend-lab/stages/12-outbox/diagnostics/compose.yaml")
require(compose.isFile) { "cresvoid 프로젝트 루트에서 실행해라." }
requireDocker()
val port = freePort()
ComposeStack(compose, "outbox-diagnose", programEnvironment(program) + mapOf("APP_PORT" to "$port")).use { stack ->
    stack.up(150)
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
        val base = "http://127.0.0.1:$port"
        val diagnostic = OutboxDiagnostic(client, base)
        diagnostic.show(get(base, "/health"))
        for (body in listOf("sample|9|commit-gap", "cancelled|8|rollback", "anchor|17|normal")) {
            println("input: $body")
            diagnostic.show(post(base, "/orders", body))
        }
        diagnostic.show(get(base, "/state"))
        snapshot(stack, "before relay")
        diagnostic.relay("evt-anchor")
        snapshot(stack, "after crash")
        diagnostic.relay("")
        snapshot(stack, "after recovery")
        diagnostic.show(get(base, "/state"))
    }
}
