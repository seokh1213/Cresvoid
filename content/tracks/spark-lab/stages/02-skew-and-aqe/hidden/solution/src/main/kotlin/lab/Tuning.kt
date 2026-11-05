package lab

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.broadcast

object Tuning {
    fun join(spark: SparkSession, facts: Dataset<Row>, rates: Dataset<Row>): Dataset<Row> {
        spark.conf().set("spark.sql.adaptive.enabled", "true")
        // Explicit broadcast overrides the disabled automatic broadcast threshold.
        return facts.join(broadcast(rates), "key")
            .select("id", "weight", "amount")
    }
}
