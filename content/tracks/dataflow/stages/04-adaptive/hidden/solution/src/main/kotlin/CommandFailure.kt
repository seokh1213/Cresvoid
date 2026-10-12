enum class ErrorCode {
    UNKNOWN_COMMAND,
    SYNTAX,
    EXISTS,
    NOT_FOUND,
    ROW,
    TYPE,
    UNKNOWN_SETTING,
}

class CommandFailure(code: ErrorCode, detail: Any?) : RuntimeException() {
    val line: String = listOfNotNull("ERR", code.name, detail?.toString()).joinToString(" ")
}

fun fail(code: ErrorCode, detail: Any? = null): Nothing = throw CommandFailure(code, detail)

fun syntaxError(): Nothing = fail(ErrorCode.SYNTAX)
