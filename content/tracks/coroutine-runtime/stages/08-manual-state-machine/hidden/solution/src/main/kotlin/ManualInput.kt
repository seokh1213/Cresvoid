private val manualNamePattern = Regex("[a-z][a-z0-9_-]{0,31}")
private val manualNumberPattern = Regex("0|[1-9][0-9]*")

data class ManualDeclaration(
    val name: String,
    val first: Int,
    val multiplier: Int,
    val firstDelay: Long,
    val secondDelay: Long,
    val outcome: ManualOutcome,
)

enum class ManualOutcome { OK, FAIL_FIRST, FAIL_SECOND }

sealed interface ManualParseResult {
    data class Valid(val declarations: List<ManualDeclaration>) : ManualParseResult
    data class Invalid(val error: InputError) : ManualParseResult
}

class ManualInputParser(private val lines: List<String>) {
    private val declarations = mutableListOf<ManualDeclaration>()
    private val names = mutableSetOf<String>()
    private var ran = false

    fun parse(): ManualParseResult {
        lines.forEachIndexed { index, raw ->
            val line = index + 1
            val trimmed = raw.trim(' ')
            if (trimmed.isEmpty()) return@forEachIndexed
            if (trimmed.any { it.isWhitespace() && it != ' ' } || ran) {
                return ManualParseResult.Invalid(InputError(line, "FORMAT"))
            }
            val words = trimmed.split(Regex(" +"))
            if (words.first() == "RUN") {
                if (words.size != 1 || declarations.isEmpty()) {
                    return ManualParseResult.Invalid(InputError(line, "FORMAT"))
                }
                ran = true
                return@forEachIndexed
            }
            val declaration = readDeclaration(words)
                ?: return ManualParseResult.Invalid(InputError(line, "FORMAT"))
            if (!names.add(declaration.name)) {
                return ManualParseResult.Invalid(InputError(line, "DUPLICATE"))
            }
            declarations.add(declaration)
        }
        if (!ran) return ManualParseResult.Invalid(InputError(lines.size + 1, "FORMAT"))
        return ManualParseResult.Valid(declarations)
    }

    private fun readDeclaration(words: List<String>): ManualDeclaration? {
        if (words.size != 7 || words[0] != "MANUAL" || !manualNamePattern.matches(words[1])) return null
        val first = readNumber(words[2], 1_000_000)?.toInt() ?: return null
        val multiplier = readNumber(words[3], 1_000)?.toInt() ?: return null
        val firstDelay = readNumber(words[4], 1_000_000_000) ?: return null
        val secondDelay = readNumber(words[5], 1_000_000_000) ?: return null
        val outcome = when (words[6]) {
            "OK" -> ManualOutcome.OK
            "FAIL_FIRST" -> ManualOutcome.FAIL_FIRST
            "FAIL_SECOND" -> ManualOutcome.FAIL_SECOND
            else -> return null
        }
        return ManualDeclaration(words[1], first, multiplier, firstDelay, secondDelay, outcome)
    }

    private fun readNumber(value: String, maximum: Long): Long? {
        if (!manualNumberPattern.matches(value)) return null
        return value.toLongOrNull()?.takeIf { it <= maximum }
    }
}
