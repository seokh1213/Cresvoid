package lab

import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDate

data class Query(val sql: String, val bind: (PreparedStatement) -> Unit)

object Queries {
    fun prepare(db: Connection) {
        db.createStatement().use {
            it.execute("CREATE INDEX idx_category_id ON products(category_id, id)")
            it.execute("ANALYZE TABLE products")
        }
    }

    fun select(input: Map<String, String>): Query = when (input.getValue("kind")) {
        "day" -> day(LocalDate.parse(input.getValue("date")))
        "sku" -> sku(input.getValue("sku"))
        "category" -> category(input.getValue("category").toInt())
        "page" -> page(input)
        else -> throw IllegalArgumentException("unknown kind")
    }

    private fun day(date: LocalDate): Query = Query(
        "SELECT id FROM products WHERE created_at >= ? AND created_at < ? ORDER BY created_at, id LIMIT 25",
    ) {
        it.setString(1, date.atStartOfDay().toString())
        it.setString(2, date.plusDays(1).atStartOfDay().toString())
    }

    private fun sku(value: String): Query = Query(
        // ascii_bin ignores trailing spaces, so verify bytes after the indexed lookup.
        "SELECT id FROM products WHERE sku = ? AND CAST(sku AS BINARY) = CAST(? AS BINARY) ORDER BY id",
    ) {
        it.setString(1, value)
        it.setString(2, value)
    }

    private fun category(value: Int): Query = Query(
        "SELECT id FROM products WHERE category_id = ? ORDER BY id LIMIT 25",
    ) { it.setInt(1, value) }

    private fun page(input: Map<String, String>): Query {
        val price = input.getValue("price").toInt()
        return Query(
            "SELECT id FROM products WHERE price > ? ORDER BY price, id LIMIT 25",
        ) {
            it.setInt(1, price)
        }
    }
}
