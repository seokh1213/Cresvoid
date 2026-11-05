fun main() {
    val input = System.`in`.bufferedReader(Charsets.UTF_8)
    val output = System.out.bufferedWriter(Charsets.UTF_8)
    input.forEachMessage { line ->
        val response = processLine(line)
        if (response != null) {
            output.write(response.encode())
            output.newLine()
            output.flush()
        }
    }
}

fun processLine(line: String): JsonObject? {
    val value = try {
        JsonParser(line).parse()
    } catch (_: JsonSyntaxException) {
        return errorResponse(null, ProtocolFailure(-32700, "Parse error"))
    }
    return when (val message = classify(value)) {
        is IncomingMessage.Request -> respond(message)
        is IncomingMessage.Invalid -> errorResponse(message.recoveredId, ProtocolFailure(-32600, "Invalid Request"))
        IncomingMessage.Ignored -> null
    }
}

fun respond(request: IncomingMessage.Request): JsonObject = try {
    val params = request.params.requireObject()
    val capabilities = validateMetadata(params)
    val result = dispatch(request.method, params, capabilities)
    successResponse(request.id, result)
} catch (failure: ProtocolFailure) {
    errorResponse(request.id, failure)
}

fun dispatch(method: String, params: JsonObject, capabilities: ClientCapabilities): JsonObject = when (method) {
    "tools/list" -> Tools.list(params)
    "tools/call" -> {
        val call = Tools.parseCall(params)
        if (call is ToolCall.CartQuote) requireQuoteCapability(capabilities)
        Tools.execute(call)
    }
    "resources/list" -> Resources.list(params)
    "resources/read" -> Resources.read(params)
    "prompts/list" -> Prompts.list(params)
    "prompts/get" -> Prompts.get(params)
    else -> throw ProtocolFailure(-32601, "Method not found")
}
