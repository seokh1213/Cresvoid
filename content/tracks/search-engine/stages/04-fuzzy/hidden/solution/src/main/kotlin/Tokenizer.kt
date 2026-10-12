private val TOKEN_PATTERN = Regex("[A-Za-z0-9]+|[가-힣]+")

fun tokenize(text: String): List<String> =
    TOKEN_PATTERN.findAll(text).map { it.value.lowercase() }.toList()

fun isHangulSyllable(letter: Char): Boolean = letter in '가'..'힣'
