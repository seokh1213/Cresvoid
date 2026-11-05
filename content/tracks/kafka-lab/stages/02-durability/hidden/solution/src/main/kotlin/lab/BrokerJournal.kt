package lab

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.NotEnoughReplicasAfterAppendException
import org.apache.kafka.common.errors.NotEnoughReplicasException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class BrokerJournal(private val bootstrap: String) : AutoCloseable {
    private val policies = deliveryPolicies().associateBy { it.name }
    private val adminConnection = lazy { Admin.create(mapOf(
        "bootstrap.servers" to bootstrap,
        "default.api.timeout.ms" to "20000",
        "request.timeout.ms" to "5000",
        "socket.connection.setup.timeout.ms" to "1000",
        "socket.connection.setup.timeout.max.ms" to "2000",
    )) }
    private val admin by adminConnection
    private val producers = ConcurrentHashMap<String, KafkaProducer<String, String>>()
    private val receipts = ConcurrentHashMap<String, String>()

    @Synchronized
    fun setup() {
        check(producers.isEmpty()) { "already configured" }
        val topics = policies.values.map { policy ->
            NewTopic(policy.topic, mapOf(0 to policy.replicas)).configs(mapOf(
                "min.insync.replicas" to policy.minimumIsr.toString(),
                "unclean.leader.election.enable" to "true",
            ))
        }
        admin.createTopics(topics).all().get(25, TimeUnit.SECONDS)
        policies.values.forEach { policy -> producers[policy.name] = producer(policy) }
    }

    private fun producer(policy: DeliveryPolicy): KafkaProducer<String, String> = KafkaProducer(mapOf(
        "bootstrap.servers" to bootstrap,
        "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
        "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
        "acks" to policy.acks,
        "enable.idempotence" to "false",
        "retries" to "0",
        "linger.ms" to "0",
        "request.timeout.ms" to "5000",
        "delivery.timeout.ms" to "12000",
        "max.block.ms" to "10000",
    ))

    fun contains(name: String): Boolean = name in policies

    fun send(name: String, id: String): String {
        val policy = policies.getValue(name)
        val status = try {
            producers.getValue(name).send(ProducerRecord(policy.topic, 0, id, id)).get(20, TimeUnit.SECONDS)
            "ACK"
        } catch (error: Exception) {
            val cause = if (error is ExecutionException) error.cause else error
            if (cause is NotEnoughReplicasException || cause is NotEnoughReplicasAfterAppendException) {
                "NOT_ENOUGH_REPLICAS"
            } else {
                System.err.println("send $name/$id: ${cause?.javaClass?.simpleName}")
                "ERROR"
            }
        }
        check(receipts.putIfAbsent("$name $id", status) == null) { "duplicate id" }
        return status
    }

    fun topology(): String {
        val descriptions = admin.describeTopics(policies.values.map { it.topic }).allTopicNames().get(25, TimeUnit.SECONDS)
        return policies.values.joinToString("\n") { policy ->
            val partition = descriptions.getValue(policy.topic).partitions().single()
            val isr = partition.isr().map { it.id() }.sorted().joinToString(",").ifEmpty { "-" }
            "${policy.name} ${partition.leader()?.id() ?: -1} $isr"
        }
    }

    fun receipts(): String = receipts.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value}" }

    fun records(name: String): String {
        val partition = TopicPartition(policies.getValue(name).topic, 0)
        val liveBrokers = admin.describeCluster().nodes().get(20, TimeUnit.SECONDS)
            .joinToString(",") { "${it.host()}:${it.port()}" }
        val properties = mapOf(
            "bootstrap.servers" to liveBrokers,
            "key.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
            "value.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
            "enable.auto.commit" to "false",
            "default.api.timeout.ms" to "15000",
            "request.timeout.ms" to "5000",
            "socket.connection.setup.timeout.ms" to "1000",
            "socket.connection.setup.timeout.max.ms" to "2000",
            "fetch.max.wait.ms" to "200",
        )
        KafkaConsumer<String, String>(properties).use { consumer ->
            consumer.assign(listOf(partition))
            consumer.seekToBeginning(listOf(partition))
            val end = consumer.endOffsets(listOf(partition)).getValue(partition)
            val values = mutableListOf<String>()
            val deadline = System.nanoTime() + Duration.ofSeconds(18).toNanos()
            while (consumer.position(partition) < end) {
                check(System.nanoTime() < deadline) { "read did not reach end offset" }
                consumer.poll(Duration.ofMillis(250)).forEach { record ->
                    if (record.offset() < end) values.add(record.value())
                }
            }
            return values.sorted().joinToString("\n")
        }
    }

    override fun close() {
        producers.values.forEach { it.close(Duration.ofSeconds(1)) }
        if (adminConnection.isInitialized()) admin.close(Duration.ofSeconds(1))
    }
}
