package lab

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

fun repairedQueries(spark: SparkSession, case: String): Map<String, () -> Dataset<Row>> = linkedMapOf(
    "join" to {
        spark.sql("SELECT l.id,r.id,COUNT(r.id) OVER (PARTITION BY l.id) FROM ${case}_lhs l LEFT JOIN ${case}_rhs r ON l.k = r.k")
    },
    "exclude" to { spark.sql("SELECT id FROM ${case}_lhs WHERE k NOT IN (SELECT k FROM ${case}_blocked)") },
    "groups" to {
        spark.sql("""
            WITH l AS (SELECT k,COUNT(k) AS total,COUNT(k) AS present FROM ${case}_lhs GROUP BY k),
                 r AS (SELECT k,COUNT(*) AS total FROM ${case}_rhs GROUP BY k)
            SELECT l.k,l.total,l.present,COALESCE(r.total,0) FROM l LEFT JOIN r ON l.k = r.k
        """.trimIndent())
    },
    "distinct_keys" to { spark.sql("SELECT k,COUNT(k) FROM ${case}_lhs WHERE k IS NOT NULL GROUP BY k") },
    "counts" to {
        spark.sql("""
            SELECT (SELECT COUNT(k) FROM ${case}_lhs),(SELECT COUNT(k) FROM ${case}_lhs),
                   (SELECT COUNT(k) FROM ${case}_rhs),(SELECT COUNT(k) FROM ${case}_rhs),
                   (SELECT COUNT(*) FROM ${case}_lhs l WHERE EXISTS (SELECT 1 FROM ${case}_rhs r WHERE l.k = r.k))
        """.trimIndent())
    },
    "union" to {
        val first = spark.table("${case}_first").selectExpr("*", "1 AS first_count", "0 AS second_count")
        val second = spark.table("${case}_second").selectExpr("*", "0 AS first_count", "1 AS second_count")
        first.union(second).groupBy("user", "region").sum("first_count", "second_count")
    },
    "cast" to { spark.sql("SELECT id,CAST(raw AS INT) FROM ${case}_numbers") },
    "add" to { spark.sql("SELECT id,n + 1 FROM ${case}_numbers") },
)
