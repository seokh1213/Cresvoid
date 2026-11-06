package lab

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Configuration(proxyBeanMethods = false)
@ComponentScan("lab")
@EnableTransactionManagement(proxyTargetClass = true)
class Configuration {
    @Bean(destroyMethod = "close")
    fun dataSource() = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:h2:mem:pool_lab;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000"
        maximumPoolSize = 5
        minimumIdle = 5
        connectionTimeout = 2_000
        validationTimeout = 1_000
        poolName = "lab-pool"
    })

    @Bean
    fun jdbc(dataSource: HikariDataSource) = JdbcTemplate(dataSource)

    @Bean
    fun transactionManager(dataSource: HikariDataSource) = DataSourceTransactionManager(dataSource)
}

class Batch {
    val readyCount = AtomicInteger()
    val finishedCount = AtomicInteger()
    private val released = CountDownLatch(1)
    private val attempts = CountDownLatch(4)

    fun ready() {
        readyCount.incrementAndGet()
        check(released.await(20, TimeUnit.SECONDS)) { "release deadline" }
    }

    fun release() = released.countDown()

    fun finishedAttempt() {
        attempts.countDown()
        check(attempts.await(15, TimeUnit.SECONDS)) { "attempt deadline" }
    }
}

class Job(val id: String, val rollback: Boolean, val batch: Batch) {
    var auditMillis = 0.0
        private set
    var auditError = "none"
        private set

    fun measureAudit(action: () -> Unit) {
        val began = System.nanoTime()
        try {
            action()
        } catch (error: RuntimeException) {
            auditError = generateSequence<Throwable>(error) { it.cause }.last().javaClass.simpleName
            throw error
        } finally {
            auditMillis = (System.nanoTime() - began) / 1e6
        }
    }
}

class Observation(private val pool: HikariDataSource, private val jdbc: JdbcTemplate) : AutoCloseable {
    val events = ConcurrentHashMap<String, String>()
    private val peakActive = AtomicInteger()
    private val peakWaiting = AtomicInteger()
    private val sampler = Executors.newSingleThreadScheduledExecutor()

    init {
        jdbc.execute("CREATE TABLE audit_log(id VARCHAR(100) PRIMARY KEY)")
        jdbc.execute("CREATE TABLE orders(id VARCHAR(100) PRIMARY KEY)")
        sampler.scheduleAtFixedRate({ sample() }, 0, 5, TimeUnit.MILLISECONDS)
    }

    private fun sample() {
        peakActive.accumulateAndGet(pool.hikariPoolMXBean.activeConnections, ::maxOf)
        peakWaiting.accumulateAndGet(pool.hikariPoolMXBean.threadsAwaitingConnection, ::maxOf)
    }

    fun state(batch: Batch): String {
        sample()
        val bean = pool.hikariPoolMXBean
        val active = bean.activeConnections
        val waiting = bean.threadsAwaitingConnection
        return linkedMapOf(
            "ready" to batch.readyCount.get(), "finished" to batch.finishedCount.get(),
            "maximum" to pool.maximumPoolSize, "active" to active,
            "waiting" to waiting, "total" to bean.totalConnections,
            "peak_active" to peakActive.get(), "peak_waiting" to peakWaiting.get(),
            "audits" to jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Int::class.java),
            "orders" to jdbc.queryForObject("SELECT COUNT(*) FROM orders", Int::class.java),
        ).entries.joinToString("\n") { "${it.key}=${it.value}" }
    }

    fun records(): String {
        val audits = jdbc.queryForList("SELECT id FROM audit_log ORDER BY id", String::class.java)
        val orders = jdbc.queryForList("SELECT id FROM orders ORDER BY id", String::class.java)
        return (audits.map { "audit|$it" } + orders.map { "order|$it" }).joinToString("\n")
    }

    override fun close() = sampler.shutdownNow().let { Unit }
}
