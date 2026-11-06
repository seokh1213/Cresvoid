package lab

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicLong

class PagingWarnings : AppenderBase<ILoggingEvent>() {
    val count = AtomicLong()

    override fun append(event: ILoggingEvent) {
        val message = event.formattedMessage.lowercase()
        if ("hhh90003004" in message || "applying in memory" in message || "in-memory pagination" in message) {
            count.incrementAndGet()
        }
    }
}

@Component
class Probe(factory: EntityManagerFactory, private val catalog: Catalog, private val fixture: Fixture) {
    private val statistics = factory.unwrap(SessionFactory::class.java).statistics
    private val warnings = PagingWarnings().apply { start() }
    private var requests = 0L
    private var statements = 0L
    private var maxStatements = 0L
    private var minStatements = Long.MAX_VALUE
    private var overfetches = 0L
    private var maxEntities = 0L

    init {
        (LoggerFactory.getLogger("org.hibernate") as Logger).addAppender(warnings)
    }

    // A single measurement lane prevents global Hibernate counters from mixing concurrent requests.
    @Synchronized
    fun page(page: Int, size: Int): OrderPage {
        val sqlBefore = statistics.prepareStatementCount
        val entitiesBefore = statistics.entityLoadCount
        val result = catalog.page(page, size)
        val sql = statistics.prepareStatementCount - sqlBefore
        val entities = statistics.entityLoadCount - entitiesBefore
        requests++
        statements += sql
        maxStatements = maxOf(maxStatements, sql)
        minStatements = minOf(minStatements, sql)
        maxEntities = maxOf(maxEntities, entities)
        val returnedEntities = result.orders.size + result.orders.sumOf { it.itemIds.size }
        if (entities > returnedEntities) overfetches++
        return result
    }

    @Synchronized
    fun append(): Long = fixture.append()

    @Synchronized
    fun metrics(): String = "requests=$requests\nsql_total=$statements\nsql_max=$maxStatements\nsql_min=$minStatements\n" +
        "entities_max=$maxEntities\noverfetches=$overfetches\npaging_warnings=${warnings.count.get()}\n"
}

@RestController
class Endpoints(private val probe: Probe, private val fixture: Fixture) {
    @GetMapping("/health", produces = ["text/plain;charset=UTF-8"])
    fun health(): String {
        if (!fixture.ready) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        return "ready\n"
    }

    @GetMapping("/orders", produces = ["text/plain;charset=UTF-8"])
    fun orders(@RequestParam("page") page: Int, @RequestParam("size") size: Int): String {
        if (page !in 0..1000 || size !in 1..40) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        val result = probe.page(page, size)
        return buildString {
            append("TOTAL\t${result.total}\n")
            for (order in result.orders) {
                append("${order.id}\t${order.state}\t${order.itemIds.joinToString(",")}\n")
            }
        }
    }

    @PostMapping("/admin/append", produces = ["text/plain;charset=UTF-8"])
    fun append(): String = "${probe.append()}\n"

    @GetMapping("/metrics", produces = ["text/plain;charset=UTF-8"])
    fun metrics(): String = probe.metrics()
}
