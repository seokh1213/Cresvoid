package lab

import org.apache.spark.api.java.function.VoidFunction2
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types.DataTypes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Timestamp
import java.time.Instant
import java.util.Comparator

val EPOCH: Instant = Instant.parse("2024-01-01T00:00:00Z")

class StreamSession(private val spark: SparkSession, private val mode: String) : AutoCloseable {
    private val directory = Files.createTempDirectory("watermark-session-")
    private val input = Files.createDirectory(directory.resolve("input"))
    private val sink = Files.createDirectory(directory.resolve("sink"))
    private val attempts = mutableListOf<Long>()
    private var nextFile = 0
    private var failNext = false
    private var watermark = -1L
    private var dropped = 0L
    private var query: StreamingQuery? = null

    fun accept(minutes: List<Int>, injectFailure: Boolean): Boolean {
        require(minutes.isNotEmpty() && minutes.all { it in 0..1440 })
        val pending = directory.resolve("pending.json")
        Files.write(pending, minutes.map { minute ->
            "{\"eventTime\":\"${EPOCH.plusSeconds(minute * 60L)}\"}"
        })
        Files.move(pending, input.resolve("events-${nextFile++}.json"), StandardCopyOption.ATOMIC_MOVE)
        failNext = injectFailure
        return drain()
    }

    fun drain(): Boolean {
        val schema = DataTypes.createStructType(arrayOf(DataTypes.createStructField("eventTime", DataTypes.TimestampType, false)))
        val events = spark.readStream().schema(schema).option("maxFilesPerTrigger", 1).json(input.toString())
        val running = aggregate(events).writeStream().outputMode(mode)
            .option("checkpointLocation", directory.resolve("checkpoint").toString())
            .trigger(Trigger.AvailableNow())
            .foreachBatch(VoidFunction2<Dataset<Row>, Long> { batch, batchId -> consume(batch, batchId) })
            .start()
        query = running
        return try {
            running.awaitTermination()
            captureProgress(running)
            true
        } catch (failure: org.apache.spark.sql.streaming.StreamingQueryException) {
            if (!failure.message.orEmpty().contains("injected-after-publish")) throw failure
            false
        } finally {
            running.stop()
            query = null
        }
    }

    private fun consume(batch: Dataset<Row>, batchId: Long) {
        attempts.add(batchId)
        // Fully consume even a replay: stateful foreachBatch must finish its Dataset actions.
        val rows = batch.collectAsList().map { row ->
            val start = row.getAs<Row>("window").getAs<Timestamp>("start").toInstant()
            val minute = (start.epochSecond - EPOCH.epochSecond) / 60
            "ROW $batchId $minute ${row.getAs<Long>("count")}"
        }.sorted()
        publish(sink, batchId, rows)
        if (failNext && rows.isNotEmpty()) {
            failNext = false
            error("injected-after-publish")
        }
    }

    private fun captureProgress(running: StreamingQuery) {
        val progress = running.recentProgress().toList()
        dropped += progress.sumOf { report -> report.stateOperators().sumOf { it.numRowsDroppedByWatermark() } }
        val time = progress.lastOrNull()?.eventTime()?.get("watermark")
        if (time != null) watermark = (Instant.parse(time).epochSecond - EPOCH.epochSecond) / 60
    }

    fun snapshot(): String = buildString {
        appendLine("WATERMARK $watermark")
        appendLine("DROPPED $dropped")
        attempts.forEach { appendLine("ATTEMPT $it") }
        Files.list(sink).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".txt") }.sorted().forEach { file ->
                Files.readAllLines(file).forEach { appendLine(it) }
            }
        }
    }

    override fun close() {
        query?.stop()
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}
