package lab

import org.springframework.http.ResponseEntity
import java.io.IOException

class Gateway(private val pool: HttpPool) {
    fun fetch(id: String, mode: String): ResponseEntity<String> = try {
        val response = pool.get("/reply?id=$id&mode=$mode")
        when {
            response.status != 200 -> ResponseEntity.status(502).body(id)
            mode == "peek" -> {
                response.readByte()
                ResponseEntity.ok(id)
            }
            else -> {
                val received = response.readText().substringBefore('|')
                response.close()
                ResponseEntity.ok(received)
            }
        }
    } catch (_: PoolWaitTimeout) {
        ResponseEntity.status(504).body("POOL_TIMEOUT:$id")
    } catch (_: IOException) {
        ResponseEntity.status(502).body("UPSTREAM_IO:$id")
    }
}
