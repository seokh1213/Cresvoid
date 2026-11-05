data class Layout(val timeBits: Int, val machineBits: Int, val sequenceBits: Int, val epoch: Long) {
    val maxElapsed = (1L shl timeBits) - 1
    val maxMachine = (1L shl machineBits) - 1
    val maxSequence = (1L shl sequenceBits) - 1
    val maxTime = epoch + maxElapsed
    val maxId = Long.MAX_VALUE ushr (63 - timeBits - machineBits - sequenceBits)

    fun containsTime(time: Long): Boolean = time in epoch..maxTime
    fun containsMachine(machine: Long): Boolean = machine in 0..maxMachine
    fun containsId(id: Long): Boolean = id in 0..maxId

    fun encode(time: Long, machine: Long, sequence: Long): Long =
        ((time - epoch) shl (machineBits + sequenceBits)) or (machine shl sequenceBits) or sequence

    fun decode(id: Long): IdParts = IdParts(
        epoch + (id ushr (machineBits + sequenceBits)),
        (id ushr sequenceBits) and maxMachine,
        id and maxSequence,
    )
}

data class IdParts(val time: Long, val machine: Long, val sequence: Long) {
    fun render(): String = "PARTS $time $machine $sequence"
}

enum class ClockPolicy { REJECT, WAIT }

data class Settings(val layout: Layout, val policy: ClockPolicy) {
    companion object {
        fun parse(tokens: List<String>): Settings? {
            val widths = tokens.subList(1, 4).map(String::toLong)
            if (widths.any { it !in 1..61 } || widths.sum() > 63) return null
            val epoch = tokens[4].toLong()
            val maxElapsed = (1L shl widths[0].toInt()) - 1
            if (epoch < 0 || epoch > Long.MAX_VALUE - maxElapsed) return null
            return Settings(
                Layout(widths[0].toInt(), widths[1].toInt(), widths[2].toInt(), epoch),
                ClockPolicy.valueOf(tokens[5]),
            )
        }
    }
}
