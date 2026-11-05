import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

class HyperLogLog(private val precision: Int) {
    private val registers = ByteArray(1 shl precision)

    fun add(hash: Long) {
        val index = (hash ushr (64 - precision)).toInt()
        // 왼쪽으로 옮긴 뒤 생긴 채움 비트는 관측한 0에 포함하지 않는다.
        val rank = minOf((hash shl precision).countLeadingZeroBits() + 1, 65 - precision)
        if (rank > registers[index]) registers[index] = rank.toByte()
    }

    fun merge(other: HyperLogLog) {
        require(precision == other.precision)
        for (index in registers.indices) registers[index] = maxOf(registers[index], other.registers[index])
    }

    fun estimate(): Double {
        val count = registers.size.toDouble()
        val alpha = when (registers.size) {
            16 -> 0.673
            32 -> 0.697
            64 -> 0.709
            else -> 0.7213 / (1.0 + 1.079 / count)
        }
        var reciprocalSum = 0.0
        var emptyRegisters = 0
        for (register in registers) {
            reciprocalSum += Math.scalb(1.0, -register.toInt())
            if (register == 0.toByte()) emptyRegisters++
        }
        val raw = (alpha * count * count) / reciprocalSum
        return if (raw <= 2.5 * count && emptyRegisters > 0) {
            count * StrictMath.log(count / emptyRegisters)
        } else {
            raw
        }
    }
}

class ValueHasher(private val column: Column) {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun hash(cell: Int): Long {
        val text = when (column) {
            is IntColumn -> cell.toString()
            is StringColumn -> column.dictionary.text(cell)
        }
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        var hash = 0L
        for (index in 0 until 8) hash = (hash shl 8) or (bytes[index].toLong() and 255L)
        return hash
    }
}

fun sixDecimals(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP)
