import java.time.LocalDate

const val SECONDS_PER_DAY = 86_400
const val LAST_SECOND_OF_DAY = SECONDS_PER_DAY - 1

private val LONG_TEXT = Regex("0|-?[1-9][0-9]{0,17}")
private val STRING_TEXT = Regex("[a-z0-9]{1,16}")
private val TIMESTAMP_TEXT = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}")

enum class ColumnType(val keyword: String) {
    LONG("long"),
    STRING("string"),
    TIMESTAMP("timestamp"),
    ;

    fun parse(text: String): Value? = when (this) {
        LONG -> if (LONG_TEXT.matches(text)) LongValue(text.toLong()) else null
        STRING -> if (STRING_TEXT.matches(text)) StringValue(text) else null
        TIMESTAMP -> parseTimestamp(text)
    }

    companion object {
        fun of(keyword: String): ColumnType? = entries.firstOrNull { it.keyword == keyword }
    }
}

sealed interface Value : Comparable<Value>

data class LongValue(val number: Long) : Value {
    override fun compareTo(other: Value): Int = number.compareTo((other as LongValue).number)
}

data class StringValue(val text: String) : Value {
    override fun compareTo(other: Value): Int = text.compareTo((other as StringValue).text)
}

data class TimestampValue(val epochSecond: Long) : Value {
    val epochDay: Int get() = (epochSecond / SECONDS_PER_DAY).toInt()
    val secondOfDay: Int get() = (epochSecond % SECONDS_PER_DAY).toInt()

    override fun compareTo(other: Value): Int = epochSecond.compareTo((other as TimestampValue).epochSecond)
}

private fun parseTimestamp(text: String): TimestampValue? {
    if (!TIMESTAMP_TEXT.matches(text)) return null
    val year = text.substring(0, 4).toInt()
    val month = text.substring(5, 7).toInt()
    val day = text.substring(8, 10).toInt()
    val hour = text.substring(11, 13).toInt()
    val minute = text.substring(14, 16).toInt()
    val second = text.substring(17, 19).toInt()
    if (year < 1970 || month !in 1..12 || day !in 1..daysInMonth(year, month)) return null
    if (hour > 23 || minute > 59 || second > 59) return null
    val epochDay = LocalDate.of(year, month, day).toEpochDay()
    return TimestampValue(epochDay * SECONDS_PER_DAY + hour * 3_600 + minute * 60 + second)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
