class KeyMarks(private val dictionary: KeyDictionary) {
    private var marks = IntArray(0)
    private var round = 0

    fun clear() {
        if (marks.size < dictionary.size) marks = IntArray(dictionary.size * 2)
        round++
    }

    fun markFirst(key: Int): Boolean {
        if (marks[key] == round) return false
        marks[key] = round
        return true
    }
}

class KeyGrouping(private val dictionary: KeyDictionary) {
    private val seen = KeyMarks(dictionary)
    private var accumulated = LongArray(0)
    private var counts = IntArray(0)
    private var cursors = IntArray(0)

    fun reduce(partition: Partition, reducer: Reducer): Partition = reducedRows(dictionary.sortInKeyOrder(accumulate(partition, reducer)))

    fun combine(partition: Partition, reducer: Reducer): Partition = reducedRows(accumulate(partition, reducer))

    fun group(partition: Partition): Partition {
        val numbers = (partition.values as Numbers).numbers
        val keys = dictionary.sortInKeyOrder(count(partition))
        val starts = IntArray(keys.size + 1)
        for ((index, key) in keys.withIndex()) {
            starts[index + 1] = starts[index] + counts[key]
            cursors[key] = starts[index]
        }
        val elements = LongArray(partition.size)
        for (row in 0 until partition.size) elements[cursors[partition.keys[row]]++] = numbers[row]
        for (index in keys.indices) elements.sort(starts[index], starts[index + 1])
        return Partition(keys.size, keys, Lists(starts, elements))
    }

    private fun accumulate(partition: Partition, reducer: Reducer): IntArray {
        val numbers = (partition.values as Numbers).numbers
        startRound()
        val keys = IntArray(partition.size)
        var distinct = 0
        for (row in 0 until partition.size) {
            val key = partition.keys[row]
            if (seen.markFirst(key)) {
                accumulated[key] = numbers[row]
                keys[distinct++] = key
            } else {
                accumulated[key] = reducer.combine(accumulated[key], numbers[row])
            }
        }
        return keys.copyOf(distinct)
    }

    private fun count(partition: Partition): IntArray {
        startRound()
        val keys = IntArray(partition.size)
        var distinct = 0
        for (row in 0 until partition.size) {
            val key = partition.keys[row]
            if (seen.markFirst(key)) {
                counts[key] = 0
                keys[distinct++] = key
            }
            counts[key]++
        }
        return keys.copyOf(distinct)
    }

    private fun reducedRows(keys: IntArray): Partition = Partition(keys.size, keys, Numbers(LongArray(keys.size) { accumulated[keys[it]] }))

    private fun startRound() {
        if (accumulated.size < dictionary.size) {
            accumulated = LongArray(dictionary.size * 2)
            counts = IntArray(dictionary.size * 2)
            cursors = IntArray(dictionary.size * 2)
        }
        seen.clear()
    }
}
