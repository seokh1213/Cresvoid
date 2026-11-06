package lab

import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDate

data class Query(val sql: String, val bind: (PreparedStatement) -> Unit)

object Queries {
    fun prepare(db: Connection) {
        db.createStatement().use { it.execute("ANALYZE TABLE products") }
    }

    fun select(input: Map<String, String>): Query = when (input.getValue("kind")) {
        "day" -> day(LocalDate.parse(input.getValue("date")))
        "sku" -> sku(input.getValue("sku"))
        "category" -> category(input.getValue("category").toInt())
        "page" -> page(input)
        else -> throw IllegalArgumentException("unknown kind")
    }

    private fun day(date: LocalDate): Query = Query(
        "SELECT id FROM products WHERE DATE(created_at) = ? ORDER BY created_at, id LIMIT 25",
    ) { it.setString(1, date.toString()) }

    private fun sku(value: String): Query = Query("SELECT id FROM products WHERE sku = ? ORDER BY id") {
        it.setLong(1, value.toLong())
    }

    private fun category(value: Int): Query = Query(
        "SELECT id FROM products WHERE category_id = ? ORDER BY id LIMIT 25",
    ) { it.setInt(1, value) }

    private fun page(input: Map<String, String>): Query = Query(
        "SELECT id FROM products ORDER BY price, id LIMIT 25 OFFSET ?",
    ) { it.setInt(1, input.getValue("offset").toInt()) }
}
