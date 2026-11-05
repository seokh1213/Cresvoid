package lab

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

fun observations(spark: SparkSession, case: String): Map<String, () -> Dataset<Row>> = linkedMapOf(
    "eq_join" to { spark.sql("SELECT l.id,r.id FROM ${case}_lhs l JOIN ${case}_rhs r ON l.k = r.k") },
    "safe_join" to { spark.sql("SELECT l.id,r.id FROM ${case}_lhs l JOIN ${case}_rhs r ON l.k <=> r.k") },
    "not_in" to { spark.sql("SELECT id FROM ${case}_lhs WHERE k NOT IN (SELECT k FROM ${case}_blocked)") },
    "groups" to { spark.sql("SELECT k,COUNT(*),COUNT(k) FROM ${case}_lhs GROUP BY k") },
    "distinct_keys" to { spark.sql("SELECT DISTINCT k FROM ${case}_lhs") },
    "counts" to { spark.sql("SELECT COUNT(*),COUNT(k) FROM ${case}_lhs") },
    "positional_union" to { spark.table("${case}_first").union(spark.table("${case}_second")) },
    "named_union" to { spark.table("${case}_first").unionByName(spark.table("${case}_second")) },
    "strict_cast" to { spark.sql("SELECT id,CAST(raw AS INT) FROM ${case}_numbers") },
    "strict_add" to { spark.sql("SELECT id,n + 1 FROM ${case}_numbers") },
)
