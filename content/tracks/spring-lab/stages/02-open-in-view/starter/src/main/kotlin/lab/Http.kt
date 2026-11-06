package lab

import com.zaxxer.hikari.HikariDataSource
import org.springframework.stereotype.Component
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

@Component
class SlowExternal {
    val calls = AtomicInteger()
    val heldCalls = AtomicInteger()

    fun call(id: Long): String {
        calls.incrementAndGet()
        if (BorrowedConnections.held() > 0) heldCalls.incrementAndGet()
        Thread.sleep(300)
        return "checked-$id"
    }
}

@RestController
class HttpApi(
    private val service: PurchaseService,
    private val external: SlowExternal,
    private val pool: HikariDataSource,
) {
    private val ready = AtomicBoolean()

    @EventListener(ApplicationReadyEvent::class)
    fun applicationReady() {
        ready.set(true)
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<String> =
        if (ready.get()) ResponseEntity.ok("ready") else ResponseEntity.status(503).body("starting")

    @GetMapping("/purchases/{id}")
    fun purchase(@PathVariable id: Long): PurchaseReply {
        require(id in 1..40)
        return service.details(id)
    }

    @PostMapping("/metrics/reset")
    fun reset(): String {
        external.calls.set(0)
        external.heldCalls.set(0)
        return "reset"
    }

    @GetMapping("/metrics")
    fun metrics(): String = listOf(
        "external_calls=${external.calls.get()}",
        "held_calls=${external.heldCalls.get()}",
        "pool_size=${pool.maximumPoolSize}",
        "active=${pool.hikariPoolMXBean.activeConnections}",
        "pending=${pool.hikariPoolMXBean.threadsAwaitingConnection}",
    ).joinToString("\n")
}
