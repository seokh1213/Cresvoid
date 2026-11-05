package lab

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col

class WritePolicy {
    fun overwrite(spark: SparkSession, mode: String) {
        spark.conf().set("spark.sql.sources.partitionOverwriteMode", mode)
    }

    fun layout(input: Dataset<Row>, strategy: String, tasks: Int): Dataset<Row> =
        when (strategy) {
            "coalesce" -> input.coalesce(1)
            "repartition" -> input.repartition(tasks, col("dt"))
            else -> input
        }

    fun recordLimit(strategy: String): Long = if (strategy == "capped") 5L else 0L
}
