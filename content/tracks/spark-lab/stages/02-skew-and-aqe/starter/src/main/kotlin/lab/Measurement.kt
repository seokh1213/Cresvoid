package lab

import org.apache.spark.TaskContext
import org.apache.spark.api.java.function.MapPartitionsFunction
import org.apache.spark.scheduler.SparkListener
import org.apache.spark.scheduler.SparkListenerStageSubmitted
import org.apache.spark.scheduler.SparkListenerTaskEnd
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.Encoders
import java.io.Serializable
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class TaskLog : SparkListener() {
    private val durations = ConcurrentHashMap<Long, Long>()
    private val stageSizes = ConcurrentHashMap<Int, Int>()
    private val shuffleStages = ConcurrentHashMap.newKeySet<Int>()

    override fun onStageSubmitted(event: SparkListenerStageSubmitted) {
        stageSizes[event.stageInfo().stageId()] = event.stageInfo().numTasks()
    }

    override fun onTaskEnd(event: SparkListenerTaskEnd) {
        if (event.taskInfo().successful()) {
            durations[event.taskInfo().taskId()] = event.taskInfo().duration()
            if (event.taskMetrics().shuffleReadMetrics().totalBytesRead() > 0) shuffleStages.add(event.stageId())
        }
    }

    fun reset() {
        durations.clear()
        stageSizes.clear()
        shuffleStages.clear()
    }

    fun await(samples: List<PartitionSample>): List<Long> {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (samples.any { !durations.containsKey(it.taskId) }) {
            check(System.nanoTime() < deadline) { "Task listener did not catch up" }
            Thread.sleep(10)
        }
        return samples.map { durations.getValue(it.taskId) }
    }

    fun shufflePartitions(): Int = shuffleStages.maxOfOrNull { stageSizes.getValue(it) } ?: 0
}

data class PartitionSample(val taskId: Long, val rows: List<String>) : Serializable

class ConsumePartition : MapPartitionsFunction<Row, PartitionSample> {
    override fun call(input: Iterator<Row>): Iterator<PartitionSample> {
        val rows = input.asSequence().map { row ->
            "${row.getLong(0)}:${row.getLong(1)}:${row.getLong(2)}"
        }.toList()
        // A fixed sink cost makes partition imbalance visible without a CPU speed assumption.
        Thread.sleep(rows.size.toLong())
        return listOf(PartitionSample(TaskContext.get().taskAttemptId(), rows)).iterator()
    }
}

fun measure(joined: Dataset<Row>, log: TaskLog): String {
    val consumed = joined.mapPartitions(ConsumePartition(), Encoders.javaSerialization(PartitionSample::class.java))
    val samples = consumed.collectAsList()
    val durations = log.await(samples)
    val rows = samples.flatMap { it.rows }.sorted()
    val digest = MessageDigest.getInstance("SHA-256").digest(rows.joinToString("\n").toByteArray())
        .joinToString("") { "%02x".format(it) }
    val plan = consumed.queryExecution().executedPlan().toString()
    val fields = linkedMapOf(
        "rows" to rows.size.toString(),
        "digest" to digest,
        "partitions" to samples.size.toString(),
        "shuffle_partitions" to log.shufflePartitions().toString(),
        "partition_rows" to samples.joinToString(",") { it.rows.size.toString() },
        "task_ms" to durations.joinToString(","),
        "adaptive" to plan.contains("AdaptiveSparkPlan").toString(),
        "skewed" to plan.contains("skew=true").toString(),
        "plan_base64" to java.util.Base64.getEncoder().encodeToString(plan.toByteArray()),
    )
    return fields.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n"
}
