package lab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import jakarta.persistence.EntityManagerFactory

@SpringBootApplication
class Application {
    @Bean
    fun transactionManager(factory: EntityManagerFactory): PlatformTransactionManager = JpaTransactionManager(factory)
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
