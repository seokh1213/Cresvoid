package lab

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicInteger

@RestController
class QuoteController(
    private val service: QuoteService,
    private val resources: Resources,
    private val downstream: Downstream,
) {
    private val received = AtomicInteger()
    private val virtual = AtomicInteger()
    private val finished = AtomicInteger()

    @GetMapping("/health", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun health(): String = "ready"

    @GetMapping("/quote/{id}", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun quote(@PathVariable("id") id: Int): ResponseEntity<String> {
        received.incrementAndGet()
        if (Thread.currentThread().isVirtual) virtual.incrementAndGet()
        return try {
            val price = service.quote(id)
            if (price == null) ResponseEntity.status(429).body("busy")
            else ResponseEntity.ok("$id:$price")
        } finally {
            finished.incrementAndGet()
        }
    }

    @PostMapping("/fixture/hold", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun hold(): String {
        downstream.hold()
        return "held"
    }

    @PostMapping("/fixture/release", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun release(): String {
        downstream.release()
        return "released"
    }

    @GetMapping("/metrics", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun metrics(): String = listOf(
        "received=" + received.get(),
        "virtual=" + virtual.get(),
        "finished=" + finished.get(),
        "queries=" + resources.queries.get(),
        "calls=" + downstream.calls.get(),
        "active=" + downstream.active.get(),
        "peak=" + downstream.peak.get(),
        "pool_active=" + resources.pool.hikariPoolMXBean.activeConnections,
        "pool_pending=" + resources.pool.hikariPoolMXBean.threadsAwaitingConnection,
    ).joinToString("\n")
}
