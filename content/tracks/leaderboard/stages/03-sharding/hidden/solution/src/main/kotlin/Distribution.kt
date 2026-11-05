class Distribution(private val boundaries: List<Long>) {
    private val rangeShards = Array(boundaries.size + 1) { RankedIndex() }
    private val hashShards = Array(boundaries.size + 1) { RankedIndex() }

    fun insert(participant: Participant) {
        rangeShards[rangeShard(participant.score)].insert(participant)
        hashShards[participant.user % hashShards.size].insert(participant)
    }

    fun remove(participant: Participant) {
        rangeShards[rangeShard(participant.score)].remove(participant)
        hashShards[participant.user % hashShards.size].remove(participant)
    }

    fun clear() {
        for (shard in rangeShards) shard.clear()
        for (shard in hashShards) shard.clear()
    }

    fun counts(mode: ShardMode): String = when (mode) {
        ShardMode.RANGE -> rangeShards.joinToString(",") { it.size.toString() }
        ShardMode.HASH -> hashShards.joinToString(",") { it.size.toString() }
    }

    fun rank(mode: ShardMode, participant: Participant): String {
        val contributions = when (mode) {
            ShardMode.RANGE -> rangeContributions(participant)
            ShardMode.HASH -> IntArray(hashShards.size) { hashShards[it].countBefore(participant) }
        }
        val calls = when (mode) {
            ShardMode.RANGE -> 1
            ShardMode.HASH -> hashShards.size
        }
        return "${contributions.sum() + 1} ${participant.score} $calls ${contributions.joinToString(",")}"
    }

    private fun rangeContributions(participant: Participant): IntArray {
        val target = rangeShard(participant.score)
        return IntArray(rangeShards.size) { shard ->
            when {
                shard > target -> rangeShards[shard].size
                shard == target -> rangeShards[shard].countBefore(participant)
                else -> 0
            }
        }
    }

    private fun rangeShard(score: Long): Int {
        var low = 0
        var high = boundaries.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (score >= boundaries[middle]) low = middle + 1 else high = middle
        }
        return low
    }
}
