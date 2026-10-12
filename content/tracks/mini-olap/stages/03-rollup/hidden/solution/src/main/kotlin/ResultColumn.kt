sealed interface ResultColumn {
    fun isNull(row: Int): Boolean

    fun compare(left: Int, right: Int): Int

    fun render(row: Int): String
}

class NumberResult(private val numbers: LongArray, private val nulls: BooleanArray? = null) : ResultColumn {
    override fun isNull(row: Int): Boolean = nulls?.get(row) == true

    override fun compare(left: Int, right: Int): Int = numbers[left].compareTo(numbers[right])

    override fun render(row: Int): String = if (isNull(row)) "NULL" else numbers[row].toString()
}

class AverageResult(private val hundredths: LongArray, private val nulls: BooleanArray) : ResultColumn {
    override fun isNull(row: Int): Boolean = nulls[row]

    override fun compare(left: Int, right: Int): Int = hundredths[left].compareTo(hundredths[right])

    override fun render(row: Int): String = if (isNull(row)) "NULL" else renderHundredths(hundredths[row])
}

class TextResult(private val codes: IntArray, private val dictionary: Dictionary) : ResultColumn {
    override fun isNull(row: Int): Boolean = codes[row] == NULL_CODE

    override fun compare(left: Int, right: Int): Int = dictionary.compare(codes[left], codes[right])

    override fun render(row: Int): String = if (isNull(row)) "NULL" else "'${dictionary.text(codes[row])}'"
}
