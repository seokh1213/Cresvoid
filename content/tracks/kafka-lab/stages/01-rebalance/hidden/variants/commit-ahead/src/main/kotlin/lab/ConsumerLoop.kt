package lab

import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration
import java.util.Properties

class ConsumerLoop(private val bootstrap: String, private val work: Work) : AutoCloseable {
    private var thread: Thread? = null
    @Volatile private var stopping = false
    @Volatile private var consumer: KafkaConsumer<String, String>? = null

    private fun properties() = Properties().apply {
        put("bootstrap.servers", bootstrap)
        put("group.id", "lab-consumer")
        put("group.protocol", "classic")
        put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        put("auto.offset.reset", "earliest")
        put("enable.auto.commit", "false")
        put("max.poll.records", "6")
        put("max.poll.interval.ms", "30000")
        put("session.timeout.ms", "10000")
        put("heartbeat.interval.ms", "500")
        put("default.api.timeout.ms", "5000")
    }

    @Synchronized
    fun start() {
        check(thread == null)
        stopping = false
        thread = Thread({ consume() }, "queue-consumer").also { it.start() }
    }

    private fun consume() {
        try {
            KafkaConsumer<String, String>(properties()).use { client ->
                consumer = client
                client.subscribe(listOf("work-queue"), object : ConsumerRebalanceListener {
                    override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) = Unit
                    override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
                        if (partitions.isNotEmpty()) work.assignments.incrementAndGet()
                    }
                })
                while (!stopping) {
                    val records = client.poll(Duration.ofMillis(100))
                    if (records.isEmpty) continue
                    try {
                        client.commitSync()
                    } catch (_: CommitFailedException) {
                        work.commitFailures.incrementAndGet()
                    }
                    for (record in records) work.process(record)
                }
            }
        } catch (_: WakeupException) {
            if (!stopping) work.fatalErrors.incrementAndGet()
        } catch (_: InterruptedException) {
            if (!stopping) work.fatalErrors.incrementAndGet()
        } catch (error: Exception) {
            if (!stopping) {
                work.fatalErrors.incrementAndGet()
                error.printStackTrace(System.err)
            }
        } finally {
            consumer = null
        }
    }

    @Synchronized
    override fun close() {
        stopping = true
        consumer?.wakeup()
        thread?.interrupt()
        thread?.join(10000)
        check(thread?.isAlive != true) { "consumer did not stop" }
        thread = null
    }
}
