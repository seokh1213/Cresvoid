import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ModelUnavailable : RuntimeException()
class BudgetExceeded : RuntimeException()

class ModelClient(private val key: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val endpoint = URI.create(System.getenv("LAB_MODEL_URL") ?: ENDPOINT).also { uri ->
        require(uri.toString() == ENDPOINT || (uri.scheme == "http" && uri.host == "127.0.0.1" && uri.userInfo == null))
    }
    private var requests = 0
    private var tokens = 0L
    private var allowance = BigDecimal.ZERO
    private var overrun = false

    @Synchronized
    fun complete(messages: List<JsonValue>, settings: RunSettings, session: String): JsonObject {
        if (overrun) throw ModelUnavailable()
        val body = json(
            "model" to text("deepseek-v4.1-flash"), "messages" to JsonArray(messages.toList()),
            "tools" to toolDefinitions(settings.description), "max_tokens" to number(512), "stream" to JsonBoolean(false)
        ).toJson()
        // UTF-8 bytes conservatively bound input tokens before spending the next request.
        val reservation = body.toByteArray(Charsets.UTF_8).size + 512L
        if (requests >= 60 || tokens + reservation > 60_000 || allowance + CALL_ALLOWANCE > BigDecimal("0.10")) throw BudgetExceeded()
        requests++
        tokens += reservation
        allowance += CALL_ALLOWANCE
        val request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $key")
            .header("User-Agent", "cresvoid-agent-lab/1.0")
            .header("x-opencode-session", session)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: Exception) {
            throw ModelUnavailable()
        }
        if (response.statusCode() != 200) throw ModelUnavailable()
        val parsed = try { parseJson(response.body()).obj() } catch (_: Exception) { throw ModelUnavailable() }
        val used = try { Usage.read(parsed) } catch (_: Exception) { throw ModelUnavailable() }
        val actualTokens = try { Math.addExact(used.input, used.output) }
        catch (_: ArithmeticException) { overrun = true; throw ModelUnavailable() }
        if (actualTokens > reservation || used.allowance > CALL_ALLOWANCE) {
            overrun = true
            throw ModelUnavailable()
        }
        tokens = tokens - reservation + actualTokens
        allowance += used.allowance - CALL_ALLOWANCE
        return parsed
    }

    companion object {
        const val ENDPOINT = "https://opencode.ai/inference/go/openai/v1/chat/completions"
        private val CALL_ALLOWANCE = BigDecimal("0.01")
    }
}
