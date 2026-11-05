package lab

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

data class Tables(val facts: Dataset<Row>, val rates: Dataset<Row>)

fun tables(spark: SparkSession, profile: String): Tables {
    val hot = when (profile) {
        "ledger" -> "id < 3072"
        "replay" -> "id % 4 != 0"
        else -> error("Unknown profile")
    }
    val facts = spark.range(0, 4096, 1, 16).selectExpr(
        "id", "CASE WHEN $hot THEN 0L ELSE 1L + id % 127 END AS key",
        "id % 17 + 1 AS amount",
    )
    val rates = spark.range(0, 128, 1, 2).selectExpr("id AS key", "id + 3 AS weight")
        .union(spark.sql("SELECT 0L AS key, 7L AS weight"))
    return Tables(facts, rates)
}

fun configure(spark: SparkSession, mode: String) {
    val settings = mapOf(
        "spark.sql.shuffle.partitions" to "16",
        "spark.sql.autoBroadcastJoinThreshold" to "-1",
        "spark.sql.adaptive.autoBroadcastJoinThreshold" to "-1",
        "spark.sql.adaptive.enabled" to (mode != "off").toString(),
        "spark.sql.adaptive.coalescePartitions.enabled" to "false",
        "spark.sql.adaptive.skewJoin.enabled" to "true",
        "spark.sql.adaptive.forceOptimizeSkewedJoin" to "true",
        "spark.sql.adaptive.advisoryPartitionSizeInBytes" to "4096",
        "spark.sql.adaptive.skewJoin.skewedPartitionFactor" to if (mode == "factor") "10000" else "3",
        "spark.sql.adaptive.skewJoin.skewedPartitionThresholdInBytes" to if (mode == "bytes") "1073741824" else "8192",
    )
    settings.forEach { (name, value) -> spark.conf().set(name, value) }
}
