package lab

import java.sql.Connection

object Policy {
    val isolation: Int = Connection.TRANSACTION_READ_COMMITTED

    fun insert(connection: Connection, batch: Int, id: Int): Boolean =
        connection.prepareStatement("INSERT IGNORE INTO slots(batch, id) VALUES (?, ?)").use { statement ->
            statement.setInt(1, batch)
            statement.setInt(2, id)
            statement.executeUpdate(); true
        }
}
