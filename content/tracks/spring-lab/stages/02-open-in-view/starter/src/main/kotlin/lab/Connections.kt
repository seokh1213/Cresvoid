package lab

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.AbstractDataSource
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

object BorrowedConnections {
    private val count = ThreadLocal.withInitial { 0 }
    fun held(): Int = count.get()

    fun track(connection: Connection): Connection {
        count.set(count.get() + 1)
        val closed = AtomicBoolean()
        return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, arguments ->
            try {
                val result = method.invoke(connection, *(arguments ?: emptyArray()))
                if (method.name == "close" && closed.compareAndSet(false, true)) count.set(count.get() - 1)
                result
            } catch (failure: InvocationTargetException) {
                throw failure.targetException
            }
        } as Connection
    }
}

@Configuration
class Database {
    @Bean(destroyMethod = "close")
    fun pool(@Value("\${spring.datasource.hikari.maximum-pool-size}") size: Int): HikariDataSource =
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:purchases;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = size
            minimumIdle = size
            connectionTimeout = 2000
            poolName = "purchase-pool"
        })

    @Bean
    @Primary
    fun dataSource(pool: HikariDataSource): DataSource = object : AbstractDataSource() {
        override fun getConnection(): Connection = BorrowedConnections.track(pool.connection)
        override fun getConnection(username: String, password: String): Connection =
            BorrowedConnections.track(pool.getConnection(username, password))
    }
}
