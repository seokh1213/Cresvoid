package lab

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val bootstrap = System.getenv("KAFKA_BOOTSTRAP") ?: "kafka:9092"
private val partition = TopicPartition("order-events", 0)

fun initializeKafka() = Admin.create(mapOf("bootstrap.servers" to bootstrap)).use { admin ->
    admin.createTopics(listOf(NewTopic(partition.topic(), 1, 1))).all().get(30, TimeUnit.SECONDS)
}

class Publisher(private val crashEvent: String = "") : AutoCloseable {
    private val producer = KafkaProducer<String, String>(mapOf(
        "bootstrap.servers" to bootstrap,
        "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
        "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
        "acks" to "all", "enable.idempotence" to "true",
        "delivery.timeout.ms" to "15000", "request.timeout.ms" to "5000", "max.block.ms" to "15000",
    ))

    fun send(order: Order) {
        producer.send(ProducerRecord(partition.topic(), order.id, order.body())).get(20, TimeUnit.SECONDS)
        Postgres().use { db ->
            db.query("INSERT INTO published(event_id,order_id,amount) VALUES " +
                "(${sqlId(order.eventId)},${sqlId(order.id)},${order.amount})")
        }
        // The broker acknowledged the record, but the relay has not recorded completion yet.
        if (order.eventId == crashEvent) Runtime.getRuntime().halt(73)
    }

    override fun close() = producer.close(Duration.ofSeconds(2))
}

class Projection : AutoCloseable {
    private val processed = AtomicLong(0)
    private val failure = AtomicReference<Throwable?>()
    @Volatile private var running = true
    private val thread = Thread.ofPlatform().name("projection").start { consume() }

    fun progress(): String {
        check(failure.get() == null) { "Projection failed" }
        val end = Admin.create(mapOf("bootstrap.servers" to bootstrap)).use { admin ->
            admin.listOffsets(mapOf(partition to OffsetSpec.latest())).all().get(10, TimeUnit.SECONDS)
                .getValue(partition).offset()
        }
        return "${processed.get()}|$end"
    }

    private fun consume() {
        try {
            KafkaConsumer<String, String>(mapOf(
                "bootstrap.servers" to bootstrap, "group.id" to "orders-view",
                "key.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
                "enable.auto.commit" to "false",
            )).use { consumer ->
                consumer.assign(listOf(partition))
                consumer.seekToBeginning(listOf(partition))
                while (running) {
                    for (record in consumer.poll(Duration.ofMillis(100))) {
                        val order = parseEvent(record.value())
                        Postgres().use { db ->
                            db.query("INSERT INTO receipts(offset_no,event_id,order_id,amount) VALUES " +
                                "(${record.offset()},${sqlId(order.eventId)},${sqlId(order.id)},${order.amount})")
                        }
                        OrderWorkflow.apply(order)
                        processed.set(record.offset() + 1)
                    }
                }
            }
        } catch (error: Exception) {
            failure.set(error)
        }
    }

    override fun close() {
        running = false
        thread.join(15_000)
    }
}
