enum class CommitTiming { BEFORE, AFTER }

class OrderConsumer(
    private val broker: MemoryBroker,
    private val inventory: Inventory,
    private val loyalty: LoyaltyAccounts,
    private val failures: FailurePlan,
) {
    private val lease = PartitionLease(broker)
    private val completedIds = mutableSetOf<String>()
    private val readyOffsets = sortedMapOf<Int, Int>()
    private var timing = CommitTiming.AFTER
    private var active = false
    var status: String = "NEW"
        private set
    var revokedProgress: Map<Int, Int> = emptyMap()
        private set

    fun start(commitTiming: CommitTiming) {
        timing = commitTiming
        lease.assign(setOf(0, 1))
        active = true
        status = "STARTED"
    }

    fun poll(limit: Int) {
        if (!active) {
            status = "STOPPED"
            return
        }
        val records = lease.poll(limit)
        if (records.isEmpty()) {
            status = "EMPTY"
            return
        }
        if (timing == CommitTiming.BEFORE) broker.commit(lease.positionSnapshot())
        try {
            records.forEach { record ->
                apply(record.order)
                readyOffsets[record.partition] = record.offset + 1
            }
            if (timing == CommitTiming.AFTER) broker.commit(readyOffsets)
            status = "OK"
        } catch (interrupted: WorkerInterrupted) {
            active = false
            status = "FAILED:${interrupted.eventId}:${interrupted.step}"
        }
    }

    private fun apply(order: OrderEvent) {
        if (order.eventId in completedIds) return
        failures.reach(order.eventId, ProcessingStep.BEFORE)
        inventory.reserve(order)
        failures.reach(order.eventId, ProcessingStep.STOCK)
        loyalty.credit(order)
        failures.reach(order.eventId, ProcessingStep.POINTS)
        completedIds.add(order.eventId)
    }

    fun rebalance(partitions: Set<Int>) {
        check(status != "CRASHED")
        onPartitionsRevoked(lease.partitions())
        readyOffsets.clear()
        lease.assign(partitions)
        active = true
        status = "ASSIGNED"
    }

    private fun onPartitionsRevoked(partitions: Set<Int>) {
        // 운영 진단에 남길 값이며 브로커에 저장하는 체크포인트는 아니다.
        revokedProgress = readyOffsets.filterKeys { it in partitions }
    }

    fun crash() {
        active = false
        status = "CRASHED"
    }

    fun describeProgress(): String {
        val fetched = lease.positionSnapshot().formatOffsets()
        val ready = readyOffsets.formatOffsets()
        val revoked = revokedProgress.formatOffsets()
        return "position=$fetched ready=$ready revoked=$revoked seen=${completedIds.sorted().joinToString(",").ifEmpty { "-" }}"
    }
}

private fun Map<Int, Int>.formatOffsets(): String = entries.sortedBy { it.key }
    .joinToString(",") { (partition, offset) -> "$partition:$offset" }.ifEmpty { "-" }
