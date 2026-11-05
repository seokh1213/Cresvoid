const val NOT_AN_INT = Long.MIN_VALUE
private const val INT_LIMIT = 1_000_000_000L

fun parseInt(text: String, start: Int = 0, end: Int = text.length): Long {
    val negative = start < end && text[start] == '-'
    val digitsStart = if (negative) start + 1 else start
    if (digitsStart == end) return NOT_AN_INT
    if (text[digitsStart] == '0') return if (!negative && end - digitsStart == 1) 0 else NOT_AN_INT
    var magnitude = 0L
    for (position in digitsStart until end) {
        val digit = text[position] - '0'
        if (digit !in 0..9) return NOT_AN_INT
        magnitude = magnitude * 10 + digit
        if (magnitude > INT_LIMIT) return NOT_AN_INT
    }
    return if (negative) -magnitude else magnitude
}
