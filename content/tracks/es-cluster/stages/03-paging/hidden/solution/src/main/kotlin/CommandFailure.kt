enum class ErrorCode {
    UNKNOWN_COMMAND,
    NO_INDEX,
    INDEX_EXISTS,
    BAD_ARGUMENT,
    NOT_FOUND,
    RESULT_WINDOW,
}

class CommandFailure(val code: ErrorCode) : RuntimeException(code.name)

fun reject(code: ErrorCode): Nothing = throw CommandFailure(code)
