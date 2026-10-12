import java.time.LocalDate

typealias Row = List<Value>

data class Column(val name: String, val type: ColumnType)

data class PartitionValue(val results: List<Int>)

sealed interface PartitionField {
    val sourceColumn: Int
    val name: String

    fun transform(value: Value): Int

    fun format(result: Int): String

    fun allowedResults(condition: Condition): IntRange
}

class DayField(override val sourceColumn: Int, sourceName: String) : PartitionField {
    override val name = "${sourceName}_day"

    override fun transform(value: Value): Int = (value as TimestampValue).epochDay

    override fun format(result: Int): String = LocalDate.ofEpochDay(result.toLong()).toString()

    override fun allowedResults(condition: Condition): IntRange {
        val value = condition.value as TimestampValue
        val day = value.epochDay
        return when (condition.operator) {
            Operator.EQ -> day..day
            Operator.LT -> Int.MIN_VALUE..(if (value.secondOfDay == 0) day - 1 else day)
            Operator.LE -> Int.MIN_VALUE..day
            Operator.GT -> (if (value.secondOfDay == LAST_SECOND_OF_DAY) day + 1 else day)..Int.MAX_VALUE
            Operator.GE -> day..Int.MAX_VALUE
        }
    }
}

class BucketField(override val sourceColumn: Int, sourceName: String, private val buckets: Int) : PartitionField {
    override val name = "${sourceName}_bucket"

    override fun transform(value: Value): Int = bucketHash(writtenForm(value)) % buckets

    override fun format(result: Int): String = result.toString()

    override fun allowedResults(condition: Condition): IntRange =
        if (condition.operator == Operator.EQ) transform(condition.value).let { it..it } else 0 until buckets
}

private fun writtenForm(value: Value): String = when (value) {
    is LongValue -> value.number.toString()
    is StringValue -> value.text
    is TimestampValue -> error("bucket 의 원본 열은 long 열이나 string 열이다")
}

private fun bucketHash(text: String): Int = text.foldIndexed(0) { index, hash, char -> hash + (index + 1) * char.code }

class Schema(val columns: List<Column>, val fields: List<PartitionField>) {
    fun columnIndex(name: String): Int? = columns.indexOfFirst { it.name == name }.takeIf { it >= 0 }

    fun partitionOf(row: Row): PartitionValue = PartitionValue(fields.map { it.transform(row[it.sourceColumn]) })

    fun format(partition: PartitionValue): String =
        fields.indices.joinToString(",", "{", "}") { index ->
            "${fields[index].name}=${fields[index].format(partition.results[index])}"
        }

    fun formatRange(field: Int, range: IntRange): String =
        "${fields[field].name}=${fields[field].format(range.first)}..${fields[field].format(range.last)}"
}
