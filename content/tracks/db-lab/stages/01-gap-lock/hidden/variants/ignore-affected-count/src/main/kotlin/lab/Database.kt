package lab

import java.sql.Connection
import java.sql.DriverManager

fun connect(): Connection = DriverManager.getConnection(
    System.getenv("JDBC_URL") ?: "jdbc:mysql://mysql:3306/lab?allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=5000&socketTimeout=20000",
    "root",
    "lab",
).apply {
    createStatement().use { it.execute("SET SESSION innodb_lock_wait_timeout = 8") }
}

fun connectionId(connection: Connection): Long = connection.createStatement().use { statement ->
    statement.executeQuery("SELECT CONNECTION_ID()").use { rows ->
        check(rows.next())
        rows.getLong(1)
    }
}

fun readRange(connection: Connection, batch: Int) {
    connection.prepareStatement("SELECT id FROM slots WHERE batch = ? AND id > 10 AND id < 30 FOR SHARE").use { statement ->
        statement.setInt(1, batch)
        statement.executeQuery().use { rows -> while (rows.next()) rows.getInt(1) }
    }
}

fun isWaiting(monitor: Connection, session: Long): Boolean = monitor.prepareStatement(
    "SELECT COUNT(*) FROM performance_schema.data_lock_waits w " +
        "JOIN performance_schema.threads t ON t.THREAD_ID = w.REQUESTING_THREAD_ID " +
        "WHERE t.PROCESSLIST_ID = ?",
).use { statement ->
    statement.setLong(1, session)
    statement.executeQuery().use { rows ->
        check(rows.next())
        rows.getInt(1) > 0
    }
}
