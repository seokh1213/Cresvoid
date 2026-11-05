package lab

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

object Tuning {
    fun join(spark: SparkSession, facts: Dataset<Row>, rates: Dataset<Row>): Dataset<Row> {
        spark.conf().set("spark.sql.adaptive.enabled", "false")
        return facts.hint("merge").join(rates.hint("merge"), "key")
            .select("id", "weight", "amount")
    }
}
