import java.util.BitSet
import java.util.TreeMap
import kotlin.math.abs

class DailySketch(val table: Table, definition: Statement.CreateSketch) {
    private val precision = definition.precision.toInt()
    private val values = table.column(definition.valueColumn)
    private val days = table.column(definition.dayColumn)
    private val hasher = ValueHasher(values)
    private val sketches = TreeMap<Int, DaySketch>()
    private val exactIds = HashMap<Int, Int>()

    fun absorb(fromRow: Int) {
        for (row in fromRow until table.rowCount) {
            val day = days.cells[row]
            if (day == days.nullCell) continue
            val sketch = sketches.getOrPut(day) { DaySketch(HyperLogLog(precision)) }
            val cell = values.cells[row]
            if (cell == values.nullCell) continue
            val exactId = exactIds.getOrPut(cell) { exactIds.size }
            if (!sketch.exactValues[exactId]) {
                sketch.exactValues.set(exactId)
                sketch.sketch.add(hasher.hash(cell))
            }
        }
    }

    fun estimate(from: Int, to: Int): List<String> {
        val merged = HyperLogLog(precision)
        val exact = BitSet()
        for (day in sketches.subMap(from, true, to, true).values) {
            merged.merge(day.sketch)
            exact.or(day.exactValues)
        }
        val estimate = merged.estimate()
        val exactCount = exact.cardinality()
        val relativeError = if (exactCount == 0) 0.0 else abs(estimate - exactCount) / exactCount
        return listOf("estimate|exact|relative_error", "${sixDecimals(estimate)}|$exactCount|${sixDecimals(relativeError)}")
    }
}

private class DaySketch(val sketch: HyperLogLog) {
    val exactValues = BitSet()
}
