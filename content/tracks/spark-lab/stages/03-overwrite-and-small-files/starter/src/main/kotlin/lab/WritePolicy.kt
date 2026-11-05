package lab

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col

class WritePolicy {
    fun overwrite(spark: SparkSession, mode: String) {
        spark.conf().set("spark.sql.sources.partitionOverwriteMode", "STATIC")
    }

    fun layout(input: Dataset<Row>, strategy: String, tasks: Int): Dataset<Row> =
        when (strategy) {
            "coalesce" -> input
            "repartition" -> input
            else -> input
        }

    fun recordLimit(strategy: String): Long = 0L
}
