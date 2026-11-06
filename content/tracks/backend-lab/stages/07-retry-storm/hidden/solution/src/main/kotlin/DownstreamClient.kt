import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RetryBudget {
    private var credits = 0

    @Synchronized
    fun recordInitial() {
        credits = (credits + 3).coerceAtMost(40)
    }

    @Synchronized
    fun takeRetry(): Boolean {
        if (credits < 20) return false
        credits -= 20
        return true
    }
}

class DownstreamClient(private val base: String) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(350)).build()
    private val budget = RetryBudget()

    fun fetch(id: String): Reply {
        budget.recordInitial()
        val deadline = System.nanoTime() + Duration.ofMillis(1100).toNanos()
        var backoff = 0L
        for (attempt in 0..2) {
            if (attempt > 0) {
                if (!budget.takeRetry()) break
                val ceiling = (80L shl (attempt - 1)).coerceAtMost(320)
                backoff = ThreadLocalRandom.current().nextLong(ceiling / 2, ceiling + 1)
                if (System.nanoTime() + Duration.ofMillis(backoff + 50).toNanos() >= deadline) break
                Thread.sleep(backoff)
            }
            val remaining = (deadline - System.nanoTime()) / 1_000_000
            if (remaining <= 0) break
            val response = call(id, attempt, backoff, remaining.coerceAtMost(350))
            if (response != null && response.statusCode() in setOf(200, 404)) {
                return Reply(response.statusCode(), response.body())
            }
            if (response != null && response.statusCode() != 503) break
        }
        return Reply(503, "{\"error\":\"unavailable\"}")
    }

    private fun call(id: String, attempt: Int, backoff: Long, timeout: Long): HttpResponse<String>? {
        val request = HttpRequest.newBuilder(URI.create("$base/quote/$id"))
            .timeout(Duration.ofMillis(timeout))
            .header("X-Attempt", attempt.toString())
            .header("X-Backoff-Ms", backoff.toString())
            .GET().build()
        val response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        return try {
            response.get(timeout, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            response.cancel(true)
            null
        } catch (error: ExecutionException) {
            if (error.cause !is IOException) throw error
            null
        } catch (error: InterruptedException) {
            response.cancel(true)
            Thread.currentThread().interrupt()
            throw error
        }
    }
}
