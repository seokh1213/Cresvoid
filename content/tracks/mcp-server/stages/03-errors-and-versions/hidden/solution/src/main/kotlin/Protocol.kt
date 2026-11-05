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
    data class Invalid(val recoveredId: JsonValue?) : IncomingMessage
}

fun classify(value: JsonValue): IncomingMessage {
    val envelope = value as? JsonObject ?: return IncomingMessage.Invalid(null)
    val id = validId(envelope["id"])
    val invalid = IncomingMessage.Invalid(id)
    if (envelope["jsonrpc"] != str("2.0")) return invalid
    if (envelope.contains("method")) {
        val method = envelope["method"] as? JsonString ?: return invalid
        if (envelope.contains("result") || envelope.contains("error")) return invalid
        if (envelope.contains("id")) {
            return if (id == null) invalid else IncomingMessage.Request(id, method.value, envelope["params"])
        }
        return if (!envelope.contains("params") || envelope["params"] is JsonObject) IncomingMessage.Ignored else invalid
    }
    return if (isResponse(envelope, id)) IncomingMessage.Ignored else invalid
}

private fun validId(value: JsonValue?): JsonValue? = when (value) {
    is JsonString -> value
    is JsonNumber -> value.integerOrNull()?.takeIf { it in -9007199254740991L..9007199254740991L }?.let(::num)
    else -> null
}

private fun isResponse(envelope: JsonObject, id: JsonValue?): Boolean {
    if (envelope.contains("params")) return false
    if (envelope.contains("result") == envelope.contains("error")) return false
    if (envelope.contains("result")) return id != null && envelope["result"] is JsonObject
    if (envelope.contains("id") && id == null) return false
    val error = envelope["error"] as? JsonObject ?: return false
    return (error["code"] as? JsonNumber)?.integerOrNull() != null && error["message"] is JsonString
}

data class ClientCapabilities(val quotePreview: Boolean)

fun validateMetadata(params: JsonObject): ClientCapabilities {
    val metadata = params["_meta"].requireObject()
    val version = metadata[VERSION_KEY].requireString()
    val capabilities = metadata[CAPABILITIES_KEY].requireObject()
    val experimental = if (capabilities.contains("experimental")) capabilities["experimental"].requireObject() else obj()
    if (experimental.fields.values.any { it !is JsonObject }) invalidParams()
    if (version != PROTOCOL_VERSION) throw ProtocolFailure(-32022, "Unsupported protocol version", obj(
        "requested" to str(version), "supported" to arr(str(PROTOCOL_VERSION)),
    ))
    return ClientCapabilities(experimental.contains(QUOTE_CAPABILITY))
}

fun requireQuoteCapability(capabilities: ClientCapabilities) {
    if (!capabilities.quotePreview) throw ProtocolFailure(-32021, "Missing required client capability", obj(
        "requiredCapabilities" to obj("experimental" to obj(QUOTE_CAPABILITY to obj())),
    ))
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
