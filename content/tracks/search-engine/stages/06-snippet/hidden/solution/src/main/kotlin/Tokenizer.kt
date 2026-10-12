private val TOKEN_PATTERN = Regex("[A-Za-z0-9]+|[가-힣]+")

data class SourceToken(val token: String, val start: Int, val endExclusive: Int)

fun tokenize(text: String): List<String> =
    TOKEN_PATTERN.findAll(text).map { it.value.lowercase() }.toList()

fun tokenizeSource(text: String): List<SourceToken> =
    TOKEN_PATTERN.findAll(text).map { SourceToken(it.value.lowercase(), it.range.first, it.range.last + 1) }.toList()

fun isHangulSyllable(letter: Char): Boolean = letter in '가'..'힣'
