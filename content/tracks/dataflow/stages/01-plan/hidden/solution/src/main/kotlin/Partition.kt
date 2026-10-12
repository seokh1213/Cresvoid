class Partition(val size: Int, val keys: IntArray, val values: Values) {
    fun withKeys(newKeys: IntArray): Partition = Partition(size, newKeys, values)

    fun withValues(newValues: Values): Partition = Partition(size, keys, newValues)

    fun gather(rows: IntArray, from: Int, to: Int): Partition =
        Partition(to - from, IntArray(to - from) { keys[rows[from + it]] }, values.gather(rows, from, to))

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

    abstract fun compare(row: Int, otherRow: Int): Int

    abstract fun format(row: Int): String
}

class Numbers(val numbers: LongArray) : Values() {
    override fun gather(rows: IntArray, from: Int, to: Int): Numbers = Numbers(LongArray(to - from) { numbers[rows[from + it]] })

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

private fun concatValues(parts: List<Partition>, size: Int): Values = when (parts.first().values) {
    is Numbers -> concatNumbers(parts, size)
    is Lists -> concatLists(parts, size)
}

private fun concatNumbers(parts: List<Partition>, size: Int): Numbers {
    val numbers = LongArray(size)
    var offset = 0
    for (part in parts) {
        (part.values as Numbers).numbers.copyInto(numbers, offset, 0, part.size)
        offset += part.size
    }
    return Numbers(numbers)
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
