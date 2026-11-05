package lab

import org.apache.spark.SparkThrowable
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row

fun render(query: () -> Dataset<Row>): String = try {
    val rows = query().collectAsList().map { row ->
        (0 until row.size()).joinToString(",") { column -> row.get(column)?.toString() ?: "~" }
    }
    "ROWS:" + rows.sorted().joinToString(";")
} catch (error: Exception) {
    val sparkError = generateSequence<Throwable>(error) { it.cause }
        .filterIsInstance<SparkThrowable>().firstOrNull() ?: throw error
    "ERROR:${sparkError.condition}"
}

fun renderBundle(queries: Map<String, () -> Dataset<Row>>): String =
    queries.entries.joinToString("\n", postfix = "\n") { (name, query) -> "$name\t${render(query)}" }
