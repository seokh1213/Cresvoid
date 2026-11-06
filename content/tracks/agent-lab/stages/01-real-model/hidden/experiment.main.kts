@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("oracle.main.kts")

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess


data class Conversation(val prompt: String, val description: String, val cache: String) {
    val requests = mutableListOf<Map<*, *>>()
    val responses = mutableListOf<Map<*, *>>()
    val usages = mutableListOf<Map<*, *>>()
}
val violations = AtomicInteger()
var currentPhase = "initialization"
fun verify(condition: Boolean) { if (!condition) violations.incrementAndGet() }
val conversations = ConcurrentHashMap<String, Conversation>()
val toolSchemas = mutableListOf<Any?>()
val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
val liveCount = AtomicInteger()
var liveTokens = 0L
var liveAllowance = BigDecimal.ZERO
val costLog = System.getenv("LAB_COST_LOG")?.let(::File)
fun auditTranscript(messages: List<*>) {
    var pending = listOf<Any?>()
    for (item in messages.drop(2)) {
        val message = obj(item)
        if (message["role"] == "assistant") {
            verify(pending.isEmpty())
            pending = (message["tool_calls"] as? List<*>).orEmpty()
        } else {
            val call = pending.firstOrNull()
            verify(message["role"] == "tool" && call != null && obj(call)["id"] == message["tool_call_id"])
            if (call != null) verify(validToolResult(call, message["content"]))
            pending = pending.drop(1)
        }
    }
    verify(pending.isEmpty())
}
fun auditRecovery(messages: List<*>, turn: Int) {
    val results = messages.map(::obj).filter { it["role"] == "tool" }
    if (turn >= 1) {
        val first = obj(Json(results[0]["content"].toString()).read())
        verify(first["ok"] == false && first["error"] == "unknown_tool" && (first["hint"] as? String)?.isNotBlank() == true)
    }
    if (turn >= 2) {
        verify(results.size == 10)
        results.drop(1).dropLast(1).forEach {
            val result = obj(Json(it["content"].toString()).read())
            verify(result["ok"] == false && result["error"] == "invalid_arguments" && (result["hint"] as? String)?.isNotBlank() == true)
        }
        verify(obj(Json(results.last()["content"].toString()).read())["quantity"].toString() == "7")
    }
}
fun forward(body: String, headers: com.sun.net.httpserver.Headers): String {
    val reservation = body.toByteArray().size + 512L
    require(liveCount.get() < 12 && liveTokens + reservation <= 60_000 && liveAllowance + BigDecimal("0.01") <= BigDecimal("0.10"))
    liveCount.incrementAndGet()
    val request = HttpRequest.newBuilder(URI.create("https://opencode.ai/inference/go/openai/v1/chat/completions"))
        .timeout(Duration.ofSeconds(50)).header("Content-Type", "application/json")
        .header("Authorization", headers.getFirst("Authorization"))
        .header("User-Agent", headers.getFirst("User-Agent"))
        .header("x-opencode-session", headers.getFirst("x-opencode-session"))
        .POST(HttpRequest.BodyPublishers.ofString(body)).build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() != 200) System.err.println("Provider HTTP ${response.statusCode()}")
    require(response.statusCode() == 200) { "model HTTP ${response.statusCode()}" }
    val used = obj(obj(Json(response.body()).read())["usage"])
    val input = decimal(used["prompt_tokens"]).longValueExact()
    val output = decimal(used["completion_tokens"]).longValueExact()
    val cached = cachedTokens(used).longValueExact()
    require(input >= 0 && output >= 0 && cached in 0..input)
    val cost = decimal(obj(used["opencode"])["allowance"])
    require(cost.signum() >= 0)
    liveTokens += input + output; liveAllowance += cost
    costLog?.appendText(encode(mapOf("requests" to 1, "input_tokens" to input, "output_tokens" to output,
        "cache_read_tokens" to cached, "allowance_usd" to cost)) + "\n")
    require(input + output <= reservation && cost <= BigDecimal("0.01") && liveTokens <= 60_000 && liveAllowance <= BigDecimal("0.10"))
    return response.body()
}
val gateway = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
gateway.createContext("/chat/completions") { exchange ->
    try {
        val raw = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
        val request = obj(Json(raw).read())
        val messages = list(request["messages"])
        val prompt = obj(messages[1])["content"].toString()
        val headers = exchange.requestHeaders
        val session = headers.getFirst("x-opencode-session") ?: ""
        verify(session.isNotBlank() && headers.getFirst("User-Agent") == "cresvoid-agent-lab/1.0")
        verify(headers.getFirst("Authorization") == "Bearer ${System.getenv("OPENCODE_API_KEY")}")
        verify(request["model"] == "deepseek-v4.1-flash" && decimal(request["max_tokens"]) == BigDecimal(512))
        verify(request["stream"] == false && !raw.contains("cache_control"))
        verify(messages.size >= 2 && obj(messages[0])["role"] == "system" && obj(messages[1])["role"] == "user")
        val description = obj(obj(list(request["tools"])[0])["function"])["description"].toString()
        val definition = obj(list(request["tools"])[0])
        val function = obj(definition["function"])
        verify(list(request["tools"]).size == 1 && definition["type"] == "function" && function["name"] == "stock")
        verify(function["parameters"] is Map<*, *>)
        if (toolSchemas.isNotEmpty()) verify(function["parameters"] == toolSchemas.first())
        toolSchemas += function["parameters"]
        val conversation = conversations.computeIfAbsent(session) { Conversation(prompt, description, obj(messages[0])["content"].toString()) }
        verify(conversation.prompt == prompt && conversation.description == description && conversation.requests.size < 8)
        if (conversation.requests.isNotEmpty()) {
            val previous = list(conversation.requests.last()["messages"])
            val assistant = obj(list(conversation.responses.last()["choices"])[0])["message"]
            verify(messages.take(previous.size) == previous && messages.getOrNull(previous.size) == assistant)
        }
        auditTranscript(messages)
        if (prompt == "probe recovery") auditRecovery(messages, conversation.requests.size)
        val response = if (prompt.startsWith("probe ")) encode(scripted(prompt, conversation.requests.size)) else forward(raw, headers)
        conversation.requests += request
        conversation.responses += obj(Json(response).read())
        conversation.usages += obj(obj(Json(response).read())["usage"])
        val bytes = response.toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
    } catch (error: Exception) {
        System.err.println("Gateway error: ${error.javaClass.simpleName}")
        violations.incrementAndGet()
        exchange.sendResponseHeaders(502, 2); exchange.responseBody.use { it.write("{}".toByteArray()) }
    } finally { exchange.close() }
}

fun runOne(base: String, prompt: String, profile: Pair<String, String>, limit: Int = 3): Map<*, *> {
    currentPhase = "conversation-${conversations.size + 1}"
    val before = conversations.keys.toSet()
    val body = encode(mapOf("prompt" to prompt, "description" to profile.first, "cache" to profile.second, "turn_limit" to limit))
    val outcome = runLoad(client, listOf(Arrival(0, post(base, "/run", body, Duration.ofSeconds(200))))).single()
    currentPhase += "-http-${outcome.status}"
    check(outcome.status == 200) { "run HTTP ${outcome.status}" }
    val result = obj(Json(outcome.body).read())
    val fresh = conversations.keys.toSet() - before
    verify(fresh.size == 1)
    val conversation = conversations[fresh.single()] ?: error("conversation")
    verify(decimal(result["turns"]).toInt() == conversation.requests.size)
    val used = obj(result["usage"])
    val fields = mapOf("input_tokens" to "prompt_tokens", "output_tokens" to "completion_tokens")
    fields.forEach { (reported, raw) -> verify(decimal(used[reported]) == conversation.usages.sumOf { decimal(it[raw]) }) }
    verify(decimal(used["cache_read_tokens"]) == conversation.usages.sumOf(::cachedTokens))
    verify(decimal(used["allowance_usd"]).compareTo(conversation.usages.sumOf { decimal(obj(it["opencode"])["allowance"]) }) == 0)
    val attempted = conversation.responses.sumOf { response ->
        val message = obj(obj(list(response["choices"])[0])["message"])
        (message["tool_calls"] as? List<*>)?.size ?: 0
    }
    verify(decimal(result["tool_calls"]).toInt() == attempted)
    val calls = conversation.responses.flatMap { response ->
        (obj(obj(list(response["choices"])[0])["message"])["tool_calls"] as? List<*>).orEmpty()
    }
    val errors = calls.count { toolOutcome(it).first != null }
    verify(decimal(result["tool_errors"]) == errors.toBigDecimal())
    verify(decimal(result["tool_executions"]) == (attempted - errors).toBigDecimal())
    verify(conversation.requests.size in 1..limit)
    verify(result["status"] == expectedStatus(conversation.responses.last()))
    val lastMessage = obj(obj(list(conversation.responses.last()["choices"])[0])["message"])
    val lastCalls = lastMessage["tool_calls"] as? List<*>
    val expectedAnswer = if (lastCalls.isNullOrEmpty()) (lastMessage["content"] as? String).orEmpty().trim() else ""
    verify(result["answer"] == expectedAnswer)
    verify(!result.containsKey("diagnostics"))
    return result
}
fun preflight(base: String) {
    val profile = "detailed" to "stable"
    val recovery = runOne(base, "probe recovery", profile)
    verify(recovery["status"] == "completed" && recovery["answer"] == "7")
    verify(decimal(recovery["tool_executions"]).toInt() == 1 && decimal(recovery["tool_errors"]).toInt() == 9)
    val loop = runOne(base, "probe loop", profile, 8)
    verify(loop["status"] == "turn_limit" && decimal(loop["turns"]).toInt() == 8)
    verify(decimal(loop["tool_executions"]).toInt() == 8 && decimal(loop["tool_errors"]).toInt() == 0)
    val last = runOne(base, "probe loop", profile, 1)
    verify(last["status"] == "turn_limit" && last["answer"] == "")
    val limited = runOne(base, "probe limited", profile, 1)
    verify(limited["status"] == "model_limit" && limited["answer"] == "partial")
    val used = runOne(base, "probe usage", profile)
    verify(used["status"] == "completed" && used["answer"] == "11")
    val done = runOne(base, "probe done", profile)
    verify(decimal(done["tool_calls"]).toInt() == 0 && done["status"] == "completed")
    for (suffix in listOf("a".repeat(1994), "😀".repeat(997))) {
        runOne(base, "probe $suffix", profile, 1)
    }
    val before = conversations.size
    val invalid = listOf(0, 9).mapIndexed { index, limit -> Arrival(index * 20L, post(base, "/run",
        encode(mapOf("prompt" to "probe done", "description" to "brief", "cache" to "cold", "turn_limit" to limit)))) }
    verify(runLoad(client, invalid).all { it.status == 400 } && conversations.size == before)
    val valid = mapOf("prompt" to "probe done", "description" to "brief", "cache" to "cold", "turn_limit" to 1)
    val malformed = listOf("{", "[]", encode(valid - "prompt"), encode(valid + ("prompt" to "")),
        encode(valid + ("prompt" to "a".repeat(2001))), encode(valid + ("description" to "other")),
        encode(valid + ("cache" to "other")), encode(valid + ("turn_limit" to "1")),
        encode(valid + ("turn_limit" to 1.5)), encode(valid + ("prompt" to "가".repeat(3000))),
        encode(valid + ("prompt" to "probe " + "😀".repeat(997) + "a")))
    malformed.forEach { body ->
        verify(runLoad(client, listOf(Arrival(0, post(base, "/run", body)))).single().status == 400)
    }
    verify(runLoad(client, listOf(Arrival(0, get(base, "/run")))).single().status == 405)
    verify(runLoad(client, listOf(Arrival(0, post(base, "/health", "{}")))).single().status == 405)
    verify(conversations.size == before)
}
fun observe(base: String, profile: Pair<String, String>): List<Map<*, *>> {
    val results = (0..3).map { index ->
        val sku = if (index % 2 == 0) "tea" else "coffee"
        val result = runOne(base, "What is the stock quantity of $sku?", profile)
        val expected = if (sku == "tea") "7" else "11"
        result + mapOf("success" to if (result["status"] == "completed" && result["answer"] == expected) 1 else 0)
    }
    val live = conversations.values.filter { !it.prompt.startsWith("probe ") }
    verify(live.size == 4)
    val prefixes = live.map { it.cache }
    verify(if (profile.second == "stable") prefixes.distinct().size == 1 else prefixes.distinct().size == 4)
    val detailed = conversations.values.first { it.prompt == "probe recovery" }.description
    verify(live.all { it.description.isNotBlank() && (it.description == detailed) == (profile.first == "detailed") })
    return results
}
fun report(results: List<Map<*, *>>) {
    val probabilities = (0..1).map { task -> results.filterIndexed { index, _ -> index % 2 == task }.map { decimal(it["success"]).toDouble() }.let { if (it.isEmpty()) 0.0 else it.average() } }
    metric("tea_success_rate", probabilities[0]); metric("coffee_success_rate", probabilities[1])
    metric("success_variance", probabilities.map { it * (1 - it) }.average())
    metric("mean_turns", if (results.isEmpty()) 0.0 else results.map { decimal(it["turns"]).toDouble() }.average())
    metric("tool_calls", results.sumOf { decimal(it["tool_calls"]) })
    for (field in listOf("input_tokens", "output_tokens", "cache_read_tokens", "allowance_usd")) {
        val total = results.sumOf { decimal(obj(it["usage"])[field]) }
        // Shared metric rounds to three decimals; preserve the measured cost instead.
        if (field == "allowance_usd") println("METRIC $field ${total.toPlainString()}") else metric(field, total)
    }
    metric("invariant_failures", violations.get())
}
if (System.getenv("OPENCODE_API_KEY").isNullOrBlank()) {
    System.err.println("OPENCODE_API_KEY is required"); exitProcess(2)
}
require(args.size == 2 && File(args[0]).isAbsolute)
val profile = when (args[1]) {
    "morning-work" -> "brief" to "cold"
    "midday-work" -> "detailed" to "cold"
    "evening-work" -> "detailed" to "stable"
    else -> error("unknown scenario")
}
var exit = 2
gateway.start()
try {
    auditBudgets(File(args[0]), client, ::verify)
    currentPhase = "diagnostics"
    auditDiagnostics(File(args[0]))
    currentPhase = "main-start"
    val port = freePort()
    val base = "http://127.0.0.1:$port"
    Program(File(args[0]), mapOf("PORT" to "$port", "LAB_MODEL_URL" to "http://127.0.0.1:${gateway.address.port}/chat/completions", "LAB_DIAGNOSTICS" to "0"), base).use {
        preflight(base)
        val results = if (violations.get() == 0) observe(base, profile) else emptyList()
        report(results)
        exit = if (violations.get() == 0) 0 else 1
    }
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.javaClass.simpleName} during $currentPhase; inspect connectivity and the public HTTP contract.")
} finally {
    gateway.stop(0)
    client.close()
}
exitProcess(exit)

data class BudgetCase(
    val name: String,
    val statuses: List<Int>,
    val requests: Int,
    val response: (String) -> Pair<Int, String>
)

fun budgetResponse(cost: String = "0", input: Long = 1, output: Long = 0): String = encode(mapOf(
    "choices" to listOf(mapOf("message" to assistant(), "finish_reason" to "stop")),
    "usage" to mapOf("prompt_tokens" to input, "completion_tokens" to output,
        "opencode" to mapOf("allowance" to cost.toBigDecimal()))
))

fun auditBudgets(program: File, client: HttpClient, verify: (Boolean) -> Unit) {
    val cases = listOf(
        BudgetCase("exact", List(10) { 200 } + 429, 10) { 200 to budgetResponse("0.01") },
        BudgetCase("residual", List(10) { 200 } + 429, 10) { 200 to budgetResponse("0.0095") },
        BudgetCase("release", List(11) { 200 } + 429, 11) { 200 to budgetResponse("0.009") },
        BudgetCase("http-failure", List(10) { 502 } + 429, 10) { 503 to "{}" },
        BudgetCase("usage-failure", List(10) { 502 } + 429, 10) { 200 to "{}" },
        BudgetCase("allowance-overrun", listOf(502, 502), 1) { 200 to budgetResponse("0.01001") },
        BudgetCase("token-overrun", listOf(502, 502), 1) { 200 to budgetResponse(input = 20_000) },
        BudgetCase("overflow", listOf(502, 502), 1) { 200 to budgetResponse(input = Long.MAX_VALUE, output = 1) },
        BudgetCase("request-limit", List(60) { 200 } + 429, 60) { 200 to budgetResponse(input = 0) }
    )
    cases.forEach { case -> auditBudgetCase(program, case, client, verify) }
}

fun auditBudgetCase(program: File, case: BudgetCase, client: HttpClient, verify: (Boolean) -> Unit) {
    currentPhase = "budget-${case.name}"
    val count = java.util.concurrent.atomic.AtomicInteger()
    val gateway = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
    gateway.createContext("/") { exchange ->
        count.incrementAndGet()
        val (status, body) = case.response(exchange.requestBody.readAllBytes().toString(Charsets.UTF_8))
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }
    val base = "http://127.0.0.1:${freePort()}"
    gateway.start()
    try {
        Program(program, mapOf("PORT" to java.net.URI.create(base).port.toString(),
            "LAB_MODEL_URL" to "http://127.0.0.1:${gateway.address.port}/", "LAB_DIAGNOSTICS" to "1"), base).use {
            val request = encode(mapOf("prompt" to "budget check", "description" to "brief", "cache" to "stable", "turn_limit" to 1))
            val batches = if (case.name == "exact") case.statuses.take(9).map { listOf(it) } + listOf(case.statuses.drop(9))
                else case.statuses.map { listOf(it) }
            val outcomes = batches.flatMap { expectedBatch ->
                val arrivals = expectedBatch.indices.map { Arrival(it * 20L, post(base, "/run", request)) }
                runLoad(client, arrivals)
            }
            outcomes.zip(case.statuses).forEach { (outcome, expected) ->
                verify(outcome.status == expected)
                if (outcome.status == 200) {
                    val result = obj(Json(outcome.body).read())
                    verify(list(result["diagnostics"]) == listOf(mapOf("turn" to 1.toBigDecimal(),
                        "finish_reason" to "stop", "answer_format" to "canonical_integer", "calls" to emptyList<Any?>())))
                }
            }
            verify(count.get() == case.requests)
        }
        verify(runLoad(client, listOf(Arrival(0, get(base, "/health")))).single().status == 0)
    } finally {
        gateway.stop(0)
    }
}

fun diagnosticTurn(turn: Int, reason: String, format: String, calls: List<Any?> = emptyList()) = mapOf(
    "turn" to turn.toBigDecimal(), "finish_reason" to reason, "answer_format" to format, "calls" to calls)

fun diagnosticCall(name: String, sku: String, validation: String) = mapOf(
    "name" to name, "sku" to sku, "validation" to validation)

fun auditDiagnostics(program: File) {
    val fake = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    fake.createContext("/") { exchange ->
        val messages = list(obj(Json(exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)).read())["messages"])
        val prompt = obj(messages[1])["content"]
        val recovery = prompt == "diagnostic recovery"
        val message = if (recovery && messages.size == 2) assistant(listOf(
            stockCall("a", arguments = "{\"sku\":\"coffee\"}"),
            stockCall("b", "untrusted-marker", "{\"sku\":\"untrusted-marker\"}"),
            stockCall("c", arguments = "untrusted-marker")))
        else assistant(answer = when (prompt) { "empty" -> ""; "words" -> "7 items"; else -> " 007 " })
        val reason = if (message.containsKey("tool_calls")) "tool_calls" else if (recovery) "length" else "untrusted-marker"
        val body = encode(mapOf("choices" to listOf(mapOf("message" to message, "finish_reason" to reason)),
            "usage" to usage(1, 1, 0, "0"))).toByteArray()
        exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
        exchange.close()
    }
    val port = freePort()
    val base = "http://127.0.0.1:$port"
    fake.start()
    try {
        Program(program, mapOf("PORT" to "$port", "LAB_MODEL_URL" to "http://127.0.0.1:${fake.address.port}/",
            "LAB_DIAGNOSTICS" to "1"), base).use {
            val expected = mapOf(
                "diagnostic recovery" to listOf(diagnosticTurn(1, "tool_calls", "not_final", listOf(
                    diagnosticCall("stock", "coffee", "accepted"),
                    diagnosticCall("unknown", "invalid", "unknown_tool"),
                    diagnosticCall("stock", "invalid", "invalid_arguments"))), diagnosticTurn(2, "length", "other_integer")),
                "empty" to listOf(diagnosticTurn(1, "other", "empty")),
                "words" to listOf(diagnosticTurn(1, "other", "non_integer")))
            expected.forEach { (prompt, trace) ->
                val body = encode(mapOf("prompt" to prompt, "description" to "brief", "cache" to "stable", "turn_limit" to 3))
                val outcome = runLoad(client, listOf(Arrival(0, post(base, "/run", body)))).single()
                verify(outcome.status == 200)
                if (outcome.status == 200) verify(obj(Json(outcome.body).read())["diagnostics"] == trace)
            }
        }
        verify(runLoad(client, listOf(Arrival(0, get(base, "/health")))).single().status == 0)
    } finally { fake.stop(0) }
}
