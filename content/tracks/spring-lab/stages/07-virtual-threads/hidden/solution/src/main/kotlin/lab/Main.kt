package lab

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication(proxyBeanMethods = false)
class Application {
    @Bean
    fun pool(): HikariDataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:h2:mem:quotes;DB_CLOSE_DELAY=-1"
        maximumPoolSize = 4
        minimumIdle = 4
        connectionTimeout = 30_000
    })

    @Bean
    fun resources(pool: HikariDataSource): Resources = Resources(pool)

    @Bean
    fun fixture(): Downstream = Downstream()

    @Bean
    fun quotes(resources: Resources, downstream: Downstream): QuoteService = QuoteService(resources, downstream)
}

fun main() {
    SpringApplication(Application::class.java).apply {
        setDefaultProperties(mapOf(
            "server.port" to (System.getenv("PORT") ?: "8080"),
            "server.address" to "0.0.0.0",
            "spring.threads.virtual.enabled" to "true",
            "spring.main.keep-alive" to "true",
            "spring.jpa.open-in-view" to "false",
            "spring.main.banner-mode" to "off",
            "logging.level.root" to "ERROR"
        ))
    }.run()
}
