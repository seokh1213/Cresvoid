package lab

import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicReference

class Database {
    private val url = System.getenv("JDBC_URL") ?: "jdbc:mysql://mysql:3306/lab?allowPublicKeyRetrieval=true&useSSL=false"
    private var holder: Connection? = null
    val ddlState = AtomicReference("IDLE")

    fun connect(): Connection = DriverManager.getConnection(url, "root", "lab-local").also { connection ->
        connection.createStatement().use { it.execute("SET SESSION lock_wait_timeout = 2") }
    }

    fun initialize() {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE IF NOT EXISTS items (id INT PRIMARY KEY, payload INT NOT NULL) ENGINE=InnoDB")
                statement.execute("INSERT INTO items VALUES (1, 7) ON DUPLICATE KEY UPDATE payload = 7")
            }
        }
    }

    fun read(): Int = connect().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT 7").use { result ->
                check(result.next())
                result.getInt(1)
            }
        }
    }

    @Synchronized
    fun hold() {
        check(holder == null)
        val connection = connect()
        try {
            connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            connection.autoCommit = false
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT payload FROM items WHERE id = 1").use { result ->
                    check(result.next())
                }
            }
            holder = connection
        } catch (error: Exception) {
            connection.close()
            throw error
        }
    }

    fun alter() {
        check(ddlState.compareAndSet("IDLE", "RUNNING"))
        Thread.startVirtualThread {
            try {
                connect().use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("SET SESSION lock_wait_timeout = 40")
                        statement.execute("ALTER TABLE items ADD COLUMN marker INT NOT NULL DEFAULT 0, ALGORITHM=INSTANT")
                    }
                }
                ddlState.set("DONE")
            } catch (_: Exception) {
                ddlState.set("FAILED")
            }
        }
    }

    @Synchronized
    fun release(mode: String) {
        val connection = holder ?: return
        try {
            when (mode) {
                "commit" -> connection.commit()
                "rollback" -> connection.rollback()
                else -> error("unknown release mode")
            }
        } finally {
            connection.close()
            holder = null
        }
    }
}
