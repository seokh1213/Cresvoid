package lab

import java.sql.Connection

object Policy {
    val isolation: Int = Connection.TRANSACTION_REPEATABLE_READ

    fun insert(connection: Connection, batch: Int, id: Int): Boolean =
        connection.prepareStatement("INSERT INTO slots(batch, id) VALUES (?, ?)").use { statement ->
            statement.setInt(1, batch)
            statement.setInt(2, id)
            statement.executeUpdate() == 1
        }
}
