import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ConcurrentLinkedQueue

class Gateway(private val upstream: String, private val connectUrl: String) {
    private val clients = ConcurrentLinkedQueue<HttpClient>()

    fun call(kind: String, token: String): Reply {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2500)
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .version(HttpClient.Version.HTTP_1_1)
            .build()
        clients.add(client)
        val url = if (kind == "connect") connectUrl else "$upstream/$kind?token=$token"
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(1500)).GET().build()
        val pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        return try {
            val remaining = (deadline - System.nanoTime()).coerceAtLeast(1)
            val response = pending.get(remaining, TimeUnit.NANOSECONDS)
            Reply(response.statusCode(), response.body())
        } catch (_: TimeoutException) {
            pending.cancel(true)
            Reply(504, "TOTAL_TIMEOUT")
        } catch (failure: ExecutionException) {
            when (val cause = failure.cause) {
                is HttpConnectTimeoutException -> Reply(504, "CONNECT_TIMEOUT")
                is HttpTimeoutException -> Reply(504, "RESPONSE_TIMEOUT")
                is IOException -> Reply(502, "IO_ERROR")
                else -> throw IllegalStateException("Unexpected downstream failure", cause)
            }
        } catch (interrupted: InterruptedException) {
            pending.cancel(true)
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }
}
