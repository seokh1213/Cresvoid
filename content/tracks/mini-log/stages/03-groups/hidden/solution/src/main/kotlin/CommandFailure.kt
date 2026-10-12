enum class ErrorCode {
    UNKNOWN_COMMAND,
    BAD_ARGUMENT,
    TOPIC_EXISTS,
    UNKNOWN_TOPIC,
    UNKNOWN_PARTITION,
    INVALID_PARTITIONS,
    RECORD_TOO_LARGE,
    OFFSET_OUT_OF_RANGE,
    UNKNOWN_PRODUCER,
    BATCH_EXISTS,
    UNKNOWN_BATCH,
    OUT_OF_ORDER_SEQUENCE,
    GROUP_EXISTS,
    UNKNOWN_GROUP,
    CONSUMER_EXISTS,
    UNKNOWN_CONSUMER,
    NOT_RUNNING,
    UNKNOWN_MEMBER,
    ILLEGAL_GENERATION,
}

// 오류 줄만 출력하므로 스택 트레이스를 만들지 않는다
class CommandFailure(code: ErrorCode, detail: String?) : RuntimeException(null, null, false, false) {
    val line: String = listOfNotNull("ERR", code.name, detail).joinToString(" ")
}

fun reject(code: ErrorCode, detail: String? = null): Nothing = throw CommandFailure(code, detail)
