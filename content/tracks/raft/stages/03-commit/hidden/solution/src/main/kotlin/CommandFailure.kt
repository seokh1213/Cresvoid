enum class ErrorCode {
    UNKNOWN_COMMAND,
    BAD_ARGUMENT,
    NO_NODE,
    NODE_EXISTS,
    STARTED,
    SAME_NODE,
    BAD_PARTITION,
}

class CommandFailure(code: ErrorCode) : RuntimeException() {
    val line: String = "ERR ${code.name}"
}

fun reject(code: ErrorCode): Nothing = throw CommandFailure(code)
