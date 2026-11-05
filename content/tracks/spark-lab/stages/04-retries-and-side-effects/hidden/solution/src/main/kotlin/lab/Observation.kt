package lab

import org.apache.spark.SparkException
import org.apache.spark.api.java.JavaSparkContext
import org.apache.spark.api.java.function.Function
import org.apache.spark.api.java.function.VoidFunction
import org.apache.spark.scheduler.SparkListener
import org.apache.spark.scheduler.SparkListenerJobEnd
import org.apache.spark.scheduler.SparkListenerTaskStart
import org.apache.spark.sql.SparkSession
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class RunInput(val size: Int, val cut: Int)
private class DriverCounter(var value: Int = 0) : Serializable

private class JobEvents : SparkListener() {
    val completed = CountDownLatch(5)
    val speculative = AtomicInteger()

    override fun onTaskStart(event: SparkListenerTaskStart) {
        if (event.taskInfo().speculative()) speculative.incrementAndGet()
    }

    override fun onJobEnd(event: SparkListenerJobEnd) {
        completed.countDown()
    }
}

fun observe(spark: SparkSession, input: RunInput, directory: Path): String {
    val context = JavaSparkContext.fromSparkContext(spark.sparkContext())
    val events = JobEvents()
    context.sc().addSparkListener(events)
    try {
        return runObservations(context, input, directory, events)
    } finally {
        context.sc().removeSparkListener(events)
    }
}

private fun runObservations(
    context: JavaSparkContext,
    input: RunInput,
    directory: Path,
    events: JobEvents,
): String {
    val transformed = context.sc().longAccumulator("transformation")
    val action = context.sc().longAccumulator("action")
    val driver = DriverCounter()
    val inputIds = context.parallelize((0 until input.size).toList(), 1)
    val mapped = inputIds.map(Function<Int, Int> { id ->
        transformed.add(1)
        driver.value += 1
        id
    })
    val counted = mapped.count()
    mapped.foreach(VoidFunction { action.add(1) })

    Files.createDirectories(directory.resolve("published"))
    val recovered = context.sc().longAccumulator("recovered-action")
    var failedJobs = 0
    try {
        inputIds.foreachPartition(ExternalWrite(directory.toString(), input.cut, recovered))
    } catch (error: SparkException) {
        check(generateSequence<Throwable>(error) { it.cause }.any { it is InjectedWriteFailure })
        failedJobs += 1
    }
    val failedUpdates = recovered.value()
    check(failedJobs == 1)
    inputIds.foreachPartition(ExternalWrite(directory.toString(), null, recovered))

    val slow = context.sc().longAccumulator("slow-partitions")
    context.parallelize(listOf(0, 1, 2, 3), 4).foreach(VoidFunction { id ->
        if (id == 3) {
            slow.add(1)
            Thread.sleep(500)
        }
    })
    check(events.completed.await(30, TimeUnit.SECONDS))
    val raw = Files.readAllLines(directory.resolve("raw.log"))
    val attempts = Files.readAllLines(directory.resolve("attempts.log"))
    val published = readPublished(directory)
    return listOf(
        "counted=$counted",
        "transformation_updates=${transformed.value()}",
        "action_updates=${action.value()}",
        "failed_action_updates=$failedUpdates",
        "recovered_updates=${recovered.value()}",
        "driver_value=${driver.value}",
        "failed_jobs=$failedJobs",
        "submissions=2",
        "task_attempts=${attempts.joinToString(",")}",
        "slow_partitions=${slow.value()}",
        "speculation_enabled=${context.sc().getConf().getBoolean("spark.speculation", false)}",
        "speculative_tasks=${events.speculative.get()}",
        "raw=${raw.joinToString(",")}",
        "published=${published.joinToString(",")}",
    ).joinToString("\n", postfix = "\n")
}
