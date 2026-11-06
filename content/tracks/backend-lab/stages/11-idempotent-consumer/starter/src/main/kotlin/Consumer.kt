package lab

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.Duration

class EventHandler {
    fun apply(db: Postgres, event: Event) {
        val id = sqlId(event.id)
        val entity = sqlId(event.entity)
        db.query("BEGIN")
        try {
            db.query("INSERT INTO entities(entity_id) VALUES ($entity) ON CONFLICT DO NOTHING")
            val previous = db.query("SELECT version FROM entities WHERE entity_id=$entity FOR UPDATE").single().single().toInt()
            db.query("""
                UPDATE entities SET version=${event.version}, value=${event.value}, total=total+${event.delta}
                WHERE entity_id=$entity;
                INSERT INTO effects(event_id,entity_id,version,value,delta,previous_version,after_version)
                VALUES ($id,$entity,${event.version},${event.value},${event.delta},$previous,${event.version});
            """.trimIndent())
            db.query("COMMIT")
        } catch (failure: Exception) {
            db.query("ROLLBACK")
            throw failure
        }
    }
}

fun consume() {
    val properties = mapOf<String, Any>(
        "bootstrap.servers" to (System.getenv("KAFKA_BOOTSTRAP") ?: "kafka:9092"),
        "group.id" to "projection", "auto.offset.reset" to "earliest", "enable.auto.commit" to "false",
        "key.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
        "value.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
        "max.poll.records" to "1", "session.timeout.ms" to "6000", "heartbeat.interval.ms" to "1000",
    )
    val handler = EventHandler()
    KafkaConsumer<String, String>(properties).use { consumer ->
        Postgres().use { db ->
            consumer.subscribe(listOf("events"))
            while (true) {
                for (record in consumer.poll(Duration.ofMillis(250))) {
                    val event = Event.parse(record.value())
                    db.query("INSERT INTO deliveries(event_id) VALUES (${sqlId(event.id)})")
                    handler.apply(db, event)
                    // Crash after the DB transaction and before the corresponding Kafka offset commit.
                    if (event.id == System.getenv("CRASH_EVENT")) Runtime.getRuntime().halt(73)
                    consumer.commitSync(mapOf(TopicPartition(record.topic(), record.partition()) to OffsetAndMetadata(record.offset() + 1)))
                }
            }
        }
    }
}
