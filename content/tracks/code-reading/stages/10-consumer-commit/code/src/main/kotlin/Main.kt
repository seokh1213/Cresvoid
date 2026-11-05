fun main() {
    val simulation = OrderSimulation()
    generateSequence(::readlnOrNull).filter { it.isNotBlank() }.forEach(simulation::execute)
}

class OrderSimulation {
    private val broker = MemoryBroker()
    private val inventory = Inventory()
    private val loyalty = LoyaltyAccounts()
    private val failures = FailurePlan()
    private var consumer: OrderConsumer? = null

    fun execute(line: String) {
        val arguments = line.trim().split(Regex("\\s+"))
        when (arguments[0]) {
            "STOCK" -> {
                require(arguments.size == 3)
                inventory.seed(arguments[1], arguments[2].toInt())
            }
            "EVENT" -> append(arguments)
            "START" -> {
                require(arguments.size == 2)
                val previousStatus = consumer?.status
                check(previousStatus == null || previousStatus in setOf("CRASHED", "STOPPED") ||
                    previousStatus.startsWith("FAILED:"))
                consumer = OrderConsumer(broker, inventory, loyalty, failures).also {
                    it.start(CommitTiming.valueOf(arguments[1]))
                }
            }
            "FAIL" -> {
                require(arguments.size == 3)
                failures.schedule(arguments[1], ProcessingStep.valueOf(arguments[2]))
            }
            "POLL" -> {
                require(arguments.size == 2)
                worker().poll(arguments[1].toInt())
            }
            "REBALANCE" -> {
                require(arguments.size == 2)
                val partitions = arguments[1].split(',').map(String::toInt).toSet()
                require(partitions.isNotEmpty() && partitions.all { it in 0..1 })
                worker().rebalance(partitions)
            }
            "CRASH" -> {
                require(arguments.size == 1)
                worker().crash()
            }
            "REPORT" -> {
                require(arguments.size == 3)
                println("stock=${inventory.quantity(arguments[1])} points=${loyalty.balance(arguments[2])} " +
                    "offsets=${broker.describeOffsets()} state=${worker().status}")
            }
            "PROGRESS" -> {
                require(arguments.size == 1)
                println(worker().describeProgress())
            }
            else -> error("unknown command")
        }
    }

    private fun append(arguments: List<String>) {
        require(arguments.size == 7)
        val partition = arguments[1].toInt()
        val order = OrderEvent(
            eventId = arguments[2], sku = arguments[3], member = arguments[4],
            units = arguments[5].toInt(), points = arguments[6].toInt(),
        )
        require(order.units > 0 && order.points >= 0)
        broker.append(partition, order)
    }

    private fun worker(): OrderConsumer = checkNotNull(consumer) { "consumer not started" }
}
