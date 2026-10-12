enum class ErrorCode {
    UNKNOWN_COMMAND,
    SYNTAX,
    EXISTS,
    NO_TABLE,
    VALUE_COUNT,
    TYPE,
    NO_COLUMN,
    NOT_GROUPED,
    NOT_SELECTED,
}

class CommandFailure(code: ErrorCode, detail: String?) : RuntimeException() {
    val line: String = listOfNotNull("ERR", code.name, detail).joinToString(" ")
}

fun reject(code: ErrorCode, detail: String? = null): Nothing = throw CommandFailure(code, detail)

fun syntaxError(): Nothing = reject(ErrorCode.SYNTAX)
