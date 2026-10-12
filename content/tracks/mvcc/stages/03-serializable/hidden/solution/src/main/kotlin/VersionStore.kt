import java.util.SortedMap
import java.util.TreeMap

@JvmInline
value class Snapshot(private val committedCount: Int) {
    operator fun contains(commitNumber: Int): Boolean = commitNumber <= committedCount
}

class Version(val commitNumber: Int, val value: String)

class VersionStore {
    private val chains = TreeMap<String, MutableList<Version>>()
    private var committedCount = 0

    fun currentSnapshot(): Snapshot = Snapshot(committedCount)

    fun valueAt(key: String, snapshot: Snapshot): String? = chains[key]?.visibleIn(snapshot)?.value

    fun valuesAt(range: KeyRange, snapshot: Snapshot): SortedMap<String, String> {
        val values = TreeMap<String, String>()
        for ((key, chain) in chains.subMap(range.start, range.end)) {
            chain.visibleIn(snapshot)?.let { values[key] = it.value }
        }
        return values
    }

    fun hasVersionOutside(key: String, snapshot: Snapshot): Boolean {
        val newest = chains[key]?.last() ?: return false
        return newest.commitNumber !in snapshot
    }

    fun commit(writes: Map<String, String>): Int {
        committedCount += 1
        for ((key, value) in writes) {
            chains.getOrPut(key) { ArrayList() }.add(Version(committedCount, value))
        }
        return committedCount
    }

    private fun List<Version>.visibleIn(snapshot: Snapshot): Version? = lastOrNull { it.commitNumber in snapshot }
}
