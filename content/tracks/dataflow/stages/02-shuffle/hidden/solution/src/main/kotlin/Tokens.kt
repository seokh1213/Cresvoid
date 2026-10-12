const val KEY_MAX_LENGTH = 24
const val NULL_TOKEN = "NULL"

private val NAME_PATTERN = Regex("[a-z][a-z0-9_]{0,31}")

fun tokensOf(line: String): List<String> = line.split(' ').filter { it.isNotEmpty() }

fun isName(token: String): Boolean = NAME_PATTERN.matches(token)

fun isKey(token: String): Boolean = isKey(token, 0, token.length)

fun isKey(text: String, start: Int, end: Int): Boolean {
    if (end - start !in 1..KEY_MAX_LENGTH) return false
    for (index in start until end) {
        val char = text[index]
        if (char !in 'a'..'z' && char !in '0'..'9' && char != '_') return false
    }
    return true
}

fun integerIn(token: String, range: LongRange): Long? = parseInteger(token, 0, token.length)?.takeIf { it in range }

// Long 을 넘는 정수 토큰은 어느 자리의 허용 범위에도 들지 않으므로 정수 토큰이 아닌 것처럼 null 로 돌려준다
fun parseInteger(text: String, start: Int, end: Int): Long? {
    val negative = start < end && text[start] == '-'
    val digitsStart = if (negative) start + 1 else start
    if (digitsStart == end) return null
    if (text[digitsStart] == '0' && (negative || end - digitsStart > 1)) return null
    var magnitude = 0L
    for (index in digitsStart until end) {
        val digit = text[index] - '0'
        if (digit !in 0..9 || magnitude > (Long.MAX_VALUE - digit) / 10) return null
        magnitude = magnitude * 10 + digit
    }
    return if (negative) -magnitude else magnitude
}
