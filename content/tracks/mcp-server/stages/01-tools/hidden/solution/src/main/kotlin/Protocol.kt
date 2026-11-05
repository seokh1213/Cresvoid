const val PROTOCOL_VERSION = "2026-07-28"
const val VERSION_KEY = "io.modelcontextprotocol/protocolVersion"
const val CAPABILITIES_KEY = "io.modelcontextprotocol/clientCapabilities"
const val QUOTE_CAPABILITY = "com.cresvoid/quote-preview"

class ProtocolFailure(val code: Long, override val message: String, val details: JsonObject? = null) : RuntimeException()

fun invalidParams(): Nothing = throw ProtocolFailure(-32602, "Invalid params")
fun JsonValue?.requireObject(): JsonObject = this as? JsonObject ?: invalidParams()
fun JsonValue?.requireString(): String = (this as? JsonString)?.value ?: invalidParams()

sealed interface IncomingMessage {
    data class Request(val id: JsonValue, val method: String, val params: JsonValue?) : IncomingMessage
    data object Ignored : IncomingMessage
}

fun classify(value: JsonValue): IncomingMessage {
    val envelope = value as JsonObject
    val method = envelope["method"] as? JsonString ?: return IncomingMessage.Ignored
    val id = envelope["id"] ?: return IncomingMessage.Ignored
    return IncomingMessage.Request(id, method.value, envelope["params"])
}

fun validateMetadata(params: JsonObject) {
    val metadata = params["_meta"].requireObject()
    metadata[VERSION_KEY].requireString()
    metadata[CAPABILITIES_KEY].requireObject()
}

fun successResponse(id: JsonValue, result: JsonObject): JsonObject = obj(
    "jsonrpc" to str("2.0"), "id" to id, "result" to result,
)

fun errorResponse(id: JsonValue?, failure: ProtocolFailure): JsonObject {
    val errorFields = mutableMapOf<String, JsonValue>(
        "code" to num(failure.code), "message" to str(failure.message),
    )
    failure.details?.let { errorFields["data"] = it }
    val envelope = mutableMapOf<String, JsonValue>("jsonrpc" to str("2.0"), "error" to JsonObject(errorFields))
    if (id != null) envelope["id"] = id
    return JsonObject(envelope)
}

fun cacheableResult(vararg fields: Pair<String, JsonValue>): JsonObject = obj(
    "resultType" to str("complete"), "ttlMs" to num(0), "cacheScope" to str("public"), *fields,
)
