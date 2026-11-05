package lab

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.apache.spark.scheduler.SparkListener
import org.apache.spark.scheduler.SparkListenerJobEnd
import org.apache.spark.scheduler.SparkListenerJobStart
import org.apache.spark.scheduler.SparkListenerStageCompleted
import org.apache.spark.sql.SparkSession

class WriteStages : SparkListener() {
    private val widths = CopyOnWriteArrayList<Int>()
    private val stageIds = mutableSetOf<Int>()
    private var finished = CountDownLatch(1)
    private var group = ""
    private var jobId = -1

    fun begin(group: String) {
        widths.clear()
        stageIds.clear()
        this.group = group
        finished = CountDownLatch(1)
    }

    override fun onJobStart(event: SparkListenerJobStart) {
        if (event.properties().getProperty("spark.jobGroup.id") != group) return
        jobId = event.jobId()
        val stages = event.stageInfos().iterator()
        while (stages.hasNext()) stageIds.add(stages.next().stageId())
    }

    override fun onStageCompleted(event: SparkListenerStageCompleted) {
        if (event.stageInfo().stageId() in stageIds) widths.add(event.stageInfo().numTasks())
    }

    override fun onJobEnd(event: SparkListenerJobEnd) {
        if (event.jobId() == jobId) finished.countDown()
    }

    fun firstWidth(): Int {
        check(finished.await(30, TimeUnit.SECONDS)) { "Spark listener did not finish" }
        return widths.first()
    }
}

class Storage(private val spark: SparkSession, private val policy: WritePolicy) {
    private val stages = WriteStages().also { spark.sparkContext().addSparkListener(it) }

    fun overwrite(mode: String): String = inWorkspace { directory ->
        val table = "daily_${directory.fileName.toString().replace("-", "_")}"
        val location = directory.resolve("daily").toUri()
        spark.sql("CREATE TABLE $table (id BIGINT, dt STRING) USING parquet PARTITIONED BY (dt) LOCATION '$location'")
        try {
            spark.sql("INSERT INTO $table VALUES (1, '2026-01-01'), (2, '2026-01-02'), (3, '2026-01-03')")
            policy.overwrite(spark, mode)
            spark.sql("INSERT OVERWRITE TABLE $table PARTITION (dt) SELECT * FROM VALUES (20L, '2026-01-02'), (21L, '2026-01-02') AS patch(id, dt)")
            val rows = spark.read().parquet(directory.resolve("daily").toString()).collectAsList()
            val dates = rows.map { it.getAs<String>("dt") }.distinct().sorted().joinToString(",")
            val ids = rows.map { it.getAs<Long>("id") }.sorted().joinToString(",")
            "dates=$dates\nids=$ids\nrows=${rows.size}\n"
        } finally {
            spark.sql("DROP TABLE IF EXISTS $table")
        }
    }

    fun files(request: FileRequest): String = inWorkspace { directory ->
        val total = request.tasks.toLong() * request.dates * request.rowsPerDate
        val input = spark.range(0, total, 1, request.tasks)
            .selectExpr("id", "concat('2026-01-0', cast((id % ${request.dates}) + 1 as string)) AS dt")
        val output = policy.layout(input, request.strategy, request.tasks)
        val group = directory.fileName.toString()
        spark.sparkContext().setJobGroup(group, "write files", false)
        stages.begin(group)
        output.write().mode("overwrite").option("maxRecordsPerFile", policy.recordLimit(request.strategy))
            .partitionBy("dt").parquet(directory.resolve("files").toString())
        val firstTasks = stages.firstWidth()
        spark.sparkContext().clearJobGroup()
        val paths = Files.walk(directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".parquet") }.toList()
        }
        val persisted = spark.read().parquet(directory.resolve("files").toString())
            .selectExpr("id", "dt", "input_file_name() AS file").collectAsList()
        val fileRows = persisted.groupingBy { it.getAs<String>("file") }.eachCount().values
        val ids = persisted.map { it.getAs<Long>("id") }.sorted().joinToString(",")
        val counts = paths.groupingBy { it.parent.fileName.toString() }.eachCount()
            .toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
        val records = persisted.sortedBy { it.getAs<Long>("id") }
            .joinToString(",") { "${it.getAs<Long>("id")}@${it.getAs<String>("dt")}" }
        "files=${paths.size}\nrows=${persisted.size}\nids=$ids\nrecords=$records\nper_date=$counts\nmax_file_rows=${fileRows.max()}\nfirst_tasks=$firstTasks\n"
    }

    private fun inWorkspace(operation: (Path) -> String): String {
        val directory = Files.createTempDirectory("daily-write-")
        return try {
            operation(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}

data class FileRequest(val tasks: Int, val dates: Int, val rowsPerDate: Int, val strategy: String)
