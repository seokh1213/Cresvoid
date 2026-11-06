package lab

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

data class RowCounts(val total: Int, val outer: Int, val inner: Int, val transactional: Int)

class Database : AutoCloseable {
    val source = DriverManagerDataSource("jdbc:h2:mem:probe_${UUID.randomUUID()}", "sa", "")
    private val keeper = source.connection
    private val jdbc = JdbcTemplate(source)
    val transactions = DataSourceTransactionManager(source).apply { isNestedTransactionAllowed = true }

    init {
        jdbc.execute("CREATE TABLE events (origin VARCHAR(16) NOT NULL, tx_active BOOLEAN NOT NULL)")
    }

    fun insert(origin: String) {
        jdbc.update(
            "INSERT INTO events(origin, tx_active) VALUES (?, ?)",
            origin,
            TransactionSynchronizationManager.isActualTransactionActive(),
        )
    }

    fun counts(): RowCounts = RowCounts(
        count("SELECT COUNT(*) FROM events"),
        count("SELECT COUNT(*) FROM events WHERE origin IN ('before', 'after')"),
        count("SELECT COUNT(*) FROM events WHERE origin = 'inner'"),
        count("SELECT COUNT(*) FROM events WHERE tx_active = TRUE"),
    )

    private fun count(sql: String): Int = requireNotNull(jdbc.queryForObject(sql, Int::class.java))

    override fun close() = keeper.close()
}
