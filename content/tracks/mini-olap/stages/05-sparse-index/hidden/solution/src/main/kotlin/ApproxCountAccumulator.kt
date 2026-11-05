class ApproxCountAccumulator(private val column: Column, resultRowCount: Int) : Accumulator() {
    private val sketches = Array(resultRowCount) { HyperLogLog(10) }
    private val hasher = ValueHasher(column)

    override fun accumulate(rows: IntArray, groups: Groups, offset: Int) {
        for (position in rows.indices) {
            val cell = column.cells[rows[position]]
            if (cell != column.nullCell) sketches[offset + groups.idOfRow[position]].add(hasher.hash(cell))
        }
    }

    override fun result(): ResultColumn = ApproximateResult(sketches.map { sixDecimals(it.estimate()) })
}

class ApproximateResult(private val estimates: List<java.math.BigDecimal>) : ResultColumn {
    override fun isNull(row: Int): Boolean = false

    override fun compare(left: Int, right: Int): Int = estimates[left].compareTo(estimates[right])

    override fun render(row: Int): String = estimates[row].toPlainString()
}
