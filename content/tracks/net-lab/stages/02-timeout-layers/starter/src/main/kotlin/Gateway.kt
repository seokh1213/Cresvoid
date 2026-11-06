import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class Gateway(private val upstream: String, private val connectUrl: String) {
    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    fun call(kind: String, token: String): Reply {
        val url = if (kind == "connect") connectUrl else "$upstream/$kind?token=$token"
        val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
        val pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        return try {
            val response = pending.get(8, TimeUnit.SECONDS)
            Reply(response.statusCode(), response.body())
        } catch (_: TimeoutException) {
            pending.cancel(true)
            Reply(504, "TOTAL_TIMEOUT")
        } catch (failure: ExecutionException) {
            if (failure.cause is IOException) Reply(502, "IO_ERROR") else throw failure
        } catch (interrupted: InterruptedException) {
            pending.cancel(true)
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }
}
