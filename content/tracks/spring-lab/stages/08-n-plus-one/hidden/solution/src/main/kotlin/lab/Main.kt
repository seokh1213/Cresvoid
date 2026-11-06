package lab

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    SpringApplication(Application::class.java).apply {
        setDefaultProperties(mapOf(
            "server.port" to (System.getenv("PORT") ?: "8080"),
            "spring.datasource.url" to "jdbc:h2:mem:orders;DB_CLOSE_DELAY=-1",
            "spring.datasource.username" to "sa",
            "spring.datasource.password" to "",
            "spring.jpa.hibernate.ddl-auto" to "create-drop",
            "spring.jpa.open-in-view" to "false",
            "spring.jpa.properties.hibernate.generate_statistics" to "true",
            "spring.jpa.properties.hibernate.cache.use_second_level_cache" to "false",
            "spring.jpa.properties.hibernate.cache.use_query_cache" to "false",
            "spring.jpa.properties.hibernate.default_batch_fetch_size" to "0",
            "spring.main.banner-mode" to "off",
            "logging.level.root" to "WARN",
            "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener" to "OFF",
        ))
    }.run(*args)
}
