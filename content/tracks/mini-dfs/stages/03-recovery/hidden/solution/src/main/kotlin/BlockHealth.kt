import java.util.TreeSet

enum class Redundancy {
    MISSING,
    UNDER_REPLICATED,
    SUFFICIENT,
    OVER_REPLICATED,
}

fun redundancyOf(block: Block): Redundancy {
    val known = block.knownReplicaCount
    return when {
        known == 0 -> Redundancy.MISSING
        known < block.replication -> Redundancy.UNDER_REPLICATED
        known == block.replication -> Redundancy.SUFFICIENT
        else -> Redundancy.OVER_REPLICATED
    }
}

private val FEWEST_REPLICAS_FIRST = Comparator<Block> { first, second ->
    val firstCount = first.knownReplicaCount
    val secondCount = second.knownReplicaCount
    when {
        firstCount != secondCount -> firstCount.compareTo(secondCount)
        else -> first.id.compareTo(second.id)
    }
}

private val BY_ID = Comparator<Block> { first, second -> first.id.compareTo(second.id) }

class BlockHealth {
    private val underReplicatedQueue = TreeSet(FEWEST_REPLICAS_FIRST)
    private val overReplicatedById = TreeSet(BY_ID)

    var blocks: Int = 0
        private set
    var replicas: Long = 0
        private set
    var missing: Int = 0
        private set

    val underReplicated: Int get() = underReplicatedQueue.size

    val safeBlocks: Int get() = blocks - missing

    fun track(block: Block) {
        blocks++
        replicas += block.knownReplicaCount
        when (redundancyOf(block)) {
            Redundancy.MISSING -> missing++
            Redundancy.UNDER_REPLICATED -> underReplicatedQueue.add(block)
            Redundancy.SUFFICIENT -> Unit
            Redundancy.OVER_REPLICATED -> overReplicatedById.add(block)
        }
    }

    fun untrack(block: Block) {
        blocks--
        replicas -= block.knownReplicaCount
        when (redundancyOf(block)) {
            Redundancy.MISSING -> missing--
            Redundancy.UNDER_REPLICATED -> underReplicatedQueue.remove(block)
            Redundancy.SUFFICIENT -> Unit
            Redundancy.OVER_REPLICATED -> overReplicatedById.remove(block)
        }
    }

    fun underReplicatedBlocks(limit: Int): List<Block> = underReplicatedQueue.asSequence().take(limit).toList()

    fun overReplicatedBlocks(): List<Block> = overReplicatedById.toList()
}
