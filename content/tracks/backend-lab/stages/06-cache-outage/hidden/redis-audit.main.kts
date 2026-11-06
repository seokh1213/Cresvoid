import java.net.InetSocketAddress
import java.net.Socket

fun redisTtlViolations(port: Int): Int {
    return Socket().use { socket ->
        socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
        socket.soTimeout = 2_000
        val request = buildString {
            for (id in 1..41) {
                val key = "product:$id"
                append("*2\r\n\$4\r\nPTTL\r\n\$${key.length}\r\n$key\r\n")
            }
        }
        socket.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
        val input = socket.getInputStream().bufferedReader()
        (1..41).count {
            val reply = input.readLine()
            check(reply != null && reply.startsWith(':')) { "Redis TTL 조회 실패: $reply" }
            val ttl = reply.drop(1).toLong()
            check(ttl >= -2)
            ttl == -1L || ttl > 5_000
        }
    }
}
