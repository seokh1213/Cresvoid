package lab

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.h2.jdbcx.JdbcDataSource
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration

@Entity
@Table(name = "observed_row")
open class ObservedRow(
    @field:Column(name = "run_key", nullable = false) open var runKey: String = "",
    @field:Column(name = "ordinal_no", nullable = false) open var ordinal: Int = 0,
    @field:Column(nullable = false) open var payload: String = "",
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
}

data class InputRow(val runKey: String, val ordinal: Int, val payload: String)

class ImportSession(private val session: Session) {
    var maxManaged: Int = 0
        private set

    fun persist(row: ImportRow) {
        session.persist(row)
        maxManaged = maxOf(maxManaged, session.statistics.entityCount)
    }

    fun flush() = session.flush()
    fun clear() = session.clear()
}

class Database : AutoCloseable {
    val source = JdbcDataSource().apply { setURL("jdbc:h2:mem:batch;DB_CLOSE_DELAY=-1") }
    val factory: SessionFactory = Configuration().apply {
        addAnnotatedClass(ImportRow::class.java)
        addAnnotatedClass(ObservedRow::class.java)
        setProperty("hibernate.hbm2ddl.auto", "create-drop")
        setProperty("hibernate.jdbc.batch_size", "50")
        setProperty("hibernate.show_sql", "false")
        setProperty("hibernate.connection.autocommit", "false")
        properties["hibernate.connection.datasource"] = MeasuredDataSource(source)
    }.buildSessionFactory()

    fun rows(table: String, run: String, expected: Int): Map<String, Number> = source.connection.use { connection ->
        connection.prepareStatement("select id, ordinal_no, payload from $table where run_key = ?").use { query ->
            query.setString(1, run)
            query.executeQuery().use { result ->
                val ids = mutableSetOf<Long>()
                val ordinals = mutableSetOf<Int>()
                var count = 0
                var bad = 0
                while (result.next()) {
                    count++
                    val id = result.getLong(1)
                    val ordinal = result.getInt(2)
                    ids.add(id)
                    ordinals.add(ordinal)
                    if (id <= 0 || ordinal !in 1..expected || result.getString(3) != "value-$ordinal") bad++
                }
                mapOf("rows" to count, "ids" to ids.size, "ordinals" to ordinals.size, "bad_rows" to bad)
            }
        }
    }

    override fun close() = factory.close()
}
