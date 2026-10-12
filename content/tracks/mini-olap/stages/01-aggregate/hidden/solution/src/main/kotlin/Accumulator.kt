sealed interface Accumulator {
    fun add(row: Row)

    fun result(): Value?
}

fun accumulatorFor(item: AggregateItem, table: Table): Accumulator = when (item) {
    CountRows -> RowCounter()
    is Aggregate -> {
        val column = table.indexOf(item.column)
        when (item.function) {
            AggregateFunction.COUNT -> ValueCounter(column)
            AggregateFunction.SUM -> Summer(column)
            AggregateFunction.AVG -> Averager(column)
            AggregateFunction.MIN -> Minimum(column)
            AggregateFunction.MAX -> Maximum(column)
        }
    }
}

class RowCounter : Accumulator {
    private var count = 0L

    override fun add(row: Row) {
        count++
    }

    override fun result(): Value = IntValue(count)
}

class ValueCounter(private val column: Int) : Accumulator {
    private var count = 0L

    override fun add(row: Row) {
        if (row[column] != null) count++
    }

    override fun result(): Value = IntValue(count)
}

class Summer(private val column: Int) : Accumulator {
    private var sum = 0L
    private var hasValue = false

    override fun add(row: Row) {
        val value = row[column] as? IntValue ?: return
        sum += value.number
        hasValue = true
    }

    override fun result(): Value? = if (hasValue) IntValue(sum) else null
}

class Averager(private val column: Int) : Accumulator {
    private var sum = 0L
    private var count = 0L

    override fun add(row: Row) {
        val value = row[column] as? IntValue ?: return
        sum += value.number
        count++
    }

    override fun result(): Value? = if (count == 0L) null else AverageValue(averageHundredths(sum, count))
}

class Minimum(private val column: Int) : Accumulator {
    private var smallest: Value? = null

    override fun add(row: Row) {
        val value = row[column] ?: return
        val current = smallest
        if (current == null || value < current) smallest = value
    }

    override fun result(): Value? = smallest
}

class Maximum(private val column: Int) : Accumulator {
    private var largest: Value? = null

    override fun add(row: Row) {
        val value = row[column] ?: return
        val current = largest
        if (current == null || value > current) largest = value
    }

    override fun result(): Value? = largest
}
