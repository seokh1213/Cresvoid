enum class ErrorCode {
    UNKNOWN_COMMAND,
    BAD_ARGUMENT,
    EXISTS,
    NO_METRIC,
    WRONG_TYPE,
    OUT_OF_ORDER,
}

// 오류 줄만 출력하므로 스택 트레이스를 만들지 않는다
class CommandFailure(code: ErrorCode) : RuntimeException(null, null, false, false) {
    val line: String = "ERR ${code.name}"
}

fun reject(code: ErrorCode): Nothing = throw CommandFailure(code)
