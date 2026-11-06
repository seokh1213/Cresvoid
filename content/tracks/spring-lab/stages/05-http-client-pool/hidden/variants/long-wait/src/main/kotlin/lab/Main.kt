package lab

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication(proxyBeanMethods = false)
class Application {
    @Bean(destroyMethod = "close")
    fun pool(): HttpPool = HttpPool(PoolSettings())

    @Bean
    fun gateway(pool: HttpPool): Gateway = Gateway(pool)
}

@RestController
class Endpoints(private val pool: HttpPool, private val gateway: Gateway) {
    @GetMapping("/health", produces = ["text/plain"])
    fun health(): String = "ready"

    @GetMapping("/metrics", produces = ["text/plain"])
    fun metrics(): String = pool.metrics()

    @GetMapping("/fetch", produces = ["text/plain"])
    fun fetch(@RequestParam("id") id: String, @RequestParam("mode") mode: String) =
        gateway.fetch(id, mode)
}

fun main(args: Array<String>) {
    startManualSource()
    SpringApplication(Application::class.java).apply {
        setDefaultProperties(mapOf(
            "server.port" to (System.getenv("PORT") ?: "8080"),
            "server.address" to "0.0.0.0",
            "spring.main.banner-mode" to "off",
            "logging.level.root" to "ERROR",
            "spring.autoconfigure.exclude" to
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "server.tomcat.threads.max" to "128",
        ))
    }.run(*args)
}
