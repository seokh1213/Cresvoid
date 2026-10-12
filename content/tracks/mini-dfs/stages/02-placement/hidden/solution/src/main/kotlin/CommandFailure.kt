enum class ErrorCode {
    UNKNOWN_COMMAND,
    BAD_ARGUMENT,
    NOT_FOUND,
    NOT_DIR,
    NOT_FILE,
    EXISTS,
    ROOT,
    SUBTREE,
    NSQUOTA,
    DSQUOTA,
    NODE_EXISTS,
    NO_NODE,
}

class CommandFailure(code: ErrorCode, directoryPath: String?) : RuntimeException() {
    val line: String = listOfNotNull("ERR", code.name, directoryPath).joinToString(" ")
}

fun reject(code: ErrorCode, directoryPath: String? = null): Nothing = throw CommandFailure(code, directoryPath)
