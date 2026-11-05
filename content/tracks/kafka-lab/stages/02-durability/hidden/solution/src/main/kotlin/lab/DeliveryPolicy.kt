package lab

data class DeliveryPolicy(val name: String, val replicas: List<Int>, val acks: String, val minimumIsr: Int) {
    val topic: String get() = "durability-$name"
}

fun deliveryPolicies(): List<DeliveryPolicy> = listOf(
    DeliveryPolicy("quorum", listOf(1, 2, 3), "all", 2),
    DeliveryPolicy("leader", listOf(1, 2, 3), "1", 2),
    DeliveryPolicy("flexible", listOf(1, 2, 3), "all", 1),
    DeliveryPolicy("single", listOf(1), "all", 1),
)
