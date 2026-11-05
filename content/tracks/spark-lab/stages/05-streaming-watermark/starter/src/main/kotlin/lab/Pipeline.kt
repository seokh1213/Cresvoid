package lab

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.functions.window
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

const val WATERMARK_DELAY = "0 minutes"

fun aggregate(events: Dataset<Row>): Dataset<Row> = events
    .withWatermark("eventTime", WATERMARK_DELAY)
    .groupBy(window(col("eventTime"), "10 minutes"))
    .count()

fun publish(directory: Path, batchId: Long, rows: List<String>) {
    val committed = directory.resolve("batch-$batchId-${System.nanoTime()}.txt")
    val pending = Files.createTempFile(directory, ".pending-", ".txt")
    Files.write(pending, rows)
    Files.move(pending, committed, StandardCopyOption.ATOMIC_MOVE)
}
