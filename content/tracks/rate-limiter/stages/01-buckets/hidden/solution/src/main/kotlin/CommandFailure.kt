enum class ErrorCode {
    UNKNOWN_COMMAND,
    BAD_ARGUMENT,
    EXISTS,
    NO_LIMITER,
}

class CommandFailure(code: ErrorCode) : RuntimeException() {
    val line: String = "ERR ${code.name}"
}

fun reject(code: ErrorCode): Nothing = throw CommandFailure(code)
