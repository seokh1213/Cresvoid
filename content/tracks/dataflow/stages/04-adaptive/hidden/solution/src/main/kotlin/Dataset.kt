class Dataset(val name: String, val partitionCount: Int) {
    private val partitions = Array(partitionCount) { StoredPartition() }

    var bytes = 0L
        private set

    fun add(partition: Int, key: Int, value: Long, rowBytes: Int) {
        partitions[partition].add(key, value)
        bytes += rowBytes
    }

    fun mark(): DatasetMark = DatasetMark(IntArray(partitionCount) { partitions[it].size }, bytes)

    fun rollback(mark: DatasetMark) {
        partitions.forEachIndexed { index, partition -> partition.size = mark.sizes[index] }
        bytes = mark.bytes
    }

    fun snapshot(): List<Partition> = partitions.map { Partition(it.size, it.keys, Numbers(it.values)) }
}

class DatasetMark(val sizes: IntArray, val bytes: Long)

private class StoredPartition {
    var keys = IntArray(16)
    var values = LongArray(16)
    var size = 0

    fun add(key: Int, value: Long) {
        if (size == keys.size) {
            keys = keys.copyOf(size * 2)
            values = values.copyOf(size * 2)
        }
        keys[size] = key
        values[size] = value
        size++
    }
}
