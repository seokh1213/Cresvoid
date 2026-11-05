sealed interface Payload {
    data class Value(val text: String) : Payload
    data object Tombstone : Payload
}

data class Record(val key: String, val sequence: Long, val payload: Payload)

fun Record?.lookupOutput(): String = when (val found = this?.payload) {
    is Payload.Value -> "VALUE ${found.text}"
    Payload.Tombstone, null -> "NOT_FOUND"
}
