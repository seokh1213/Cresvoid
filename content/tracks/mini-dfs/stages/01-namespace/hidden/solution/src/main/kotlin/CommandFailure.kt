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
}

class CommandFailure(code: ErrorCode, directoryPath: String?) : RuntimeException() {
    val line: String = listOfNotNull("ERR", code.name, directoryPath).joinToString(" ")
}

fun reject(code: ErrorCode, directoryPath: String? = null): Nothing = throw CommandFailure(code, directoryPath)
