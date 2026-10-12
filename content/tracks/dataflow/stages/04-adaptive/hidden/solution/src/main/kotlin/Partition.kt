const val NUMBER_BYTES = 8

private const val LIST_HEADER_BYTES = 4
private const val PAIR_BYTES = 16

class Partition(val size: Int, val keys: IntArray, val values: Values) {
    fun withKeys(newKeys: IntArray): Partition = Partition(size, newKeys, values)

    fun withValues(newValues: Values): Partition = Partition(size, keys, newValues)

    fun gather(rows: IntArray, from: Int, to: Int): Partition =
        Partition(to - from, IntArray(to - from) { keys[rows[from + it]] }, values.gather(rows, from, to))

    fun slice(from: Int, to: Int): Partition = Partition(to - from, keys.copyOfRange(from, to), values.slice(from, to))

    fun rowBytes(row: Int, dictionary: KeyDictionary): Int = dictionary.keyBytes(keys[row]) + values.bytes(row)

    companion object {
        fun concat(parts: List<Partition>): Partition {
            if (parts.size == 1) return parts.single()
            val size = parts.sumOf { it.size }
            val keys = IntArray(size)
            var offset = 0
            for (part in parts) {
                part.keys.copyInto(keys, offset, 0, part.size)
                offset += part.size
            }
            return Partition(size, keys, concatValues(parts, size))
        }
    }
}

sealed class Values {
    abstract fun gather(rows: IntArray, from: Int, to: Int): Values

    abstract fun slice(from: Int, to: Int): Values

    abstract fun bytes(row: Int): Int

    abstract fun compare(row: Int, otherRow: Int): Int

    abstract fun format(row: Int): String
}

class Numbers(val numbers: LongArray) : Values() {
    override fun gather(rows: IntArray, from: Int, to: Int): Numbers = Numbers(LongArray(to - from) { numbers[rows[from + it]] })

    override fun slice(from: Int, to: Int): Numbers = Numbers(numbers.copyOfRange(from, to))

    override fun bytes(row: Int): Int = NUMBER_BYTES

    override fun compare(row: Int, otherRow: Int): Int = numbers[row].compareTo(numbers[otherRow])

    override fun format(row: Int): String = numbers[row].toString()
}

class Lists(val starts: IntArray, val elements: LongArray) : Values() {
    fun length(row: Int): Int = starts[row + 1] - starts[row]

    override fun gather(rows: IntArray, from: Int, to: Int): Lists {
        val gatheredStarts = IntArray(to - from + 1)
        for (index in 0 until to - from) gatheredStarts[index + 1] = gatheredStarts[index] + length(rows[from + index])
        val gatheredElements = LongArray(gatheredStarts.last())
        for (index in 0 until to - from) {
            val row = rows[from + index]
            elements.copyInto(gatheredElements, gatheredStarts[index], starts[row], starts[row + 1])
        }
        return Lists(gatheredStarts, gatheredElements)
    }

    override fun slice(from: Int, to: Int): Lists =
        Lists(IntArray(to - from + 1) { starts[from + it] - starts[from] }, elements.copyOfRange(starts[from], starts[to]))

    override fun bytes(row: Int): Int = LIST_HEADER_BYTES + NUMBER_BYTES * length(row)

    override fun compare(row: Int, otherRow: Int): Int {
        val shared = minOf(length(row), length(otherRow))
        for (offset in 0 until shared) {
            val order = elements[starts[row] + offset].compareTo(elements[starts[otherRow] + offset])
            if (order != 0) return order
        }
        return length(row).compareTo(length(otherRow))
    }

    override fun format(row: Int): String =
        (starts[row] until starts[row + 1]).joinToString(",", prefix = "[", postfix = "]") { elements[it].toString() }
}

class Pairs(val left: LongArray, val right: LongArray) : Values() {
    override fun gather(rows: IntArray, from: Int, to: Int): Pairs =
        Pairs(LongArray(to - from) { left[rows[from + it]] }, LongArray(to - from) { right[rows[from + it]] })

    override fun slice(from: Int, to: Int): Pairs = Pairs(left.copyOfRange(from, to), right.copyOfRange(from, to))

    override fun bytes(row: Int): Int = PAIR_BYTES

    override fun compare(row: Int, otherRow: Int): Int {
        val order = left[row].compareTo(left[otherRow])
        return if (order != 0) order else right[row].compareTo(right[otherRow])
    }

    override fun format(row: Int): String = "(${left[row]},${right[row]})"
}

private fun concatValues(parts: List<Partition>, size: Int): Values = when (parts.first().values) {
    is Numbers -> Numbers(concatLongs(parts, size) { (it as Numbers).numbers })
    is Pairs -> Pairs(concatLongs(parts, size) { (it as Pairs).left }, concatLongs(parts, size) { (it as Pairs).right })
    is Lists -> concatLists(parts, size)
}

private fun concatLongs(parts: List<Partition>, size: Int, column: (Values) -> LongArray): LongArray {
    val concatenated = LongArray(size)
    var offset = 0
    for (part in parts) {
        column(part.values).copyInto(concatenated, offset, 0, part.size)
        offset += part.size
    }
    return concatenated
}

private fun concatLists(parts: List<Partition>, size: Int): Lists {
    val starts = IntArray(size + 1)
    val elements = LongArray(parts.sumOf { (it.values as Lists).starts[it.size] })
    var row = 0
    for (part in parts) {
        val lists = part.values as Lists
        val elementOffset = starts[row]
        lists.elements.copyInto(elements, elementOffset, 0, lists.starts[part.size])
        for (index in 1..part.size) starts[row + index] = elementOffset + lists.starts[index]
        row += part.size
    }
    return Lists(starts, elements)
}
