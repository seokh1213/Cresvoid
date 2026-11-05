fun main() {
    val input = System.`in`.bufferedReader(Charsets.UTF_8)
    val output = System.out.bufferedWriter(Charsets.UTF_8)
    input.forEachMessage { line ->
        val message = classify(JsonParser(line).parse())
        if (message is IncomingMessage.Request) {
            val response = respond(message)
            output.write(response.encode())
            output.newLine()
            output.flush()
        }
    }
}

fun respond(request: IncomingMessage.Request): JsonObject = try {
    val params = request.params.requireObject()
    validateMetadata(params)
    val result = dispatch(request.method, params)
    successResponse(request.id, result)
} catch (failure: ProtocolFailure) {
    errorResponse(request.id, failure)
}

fun dispatch(method: String, params: JsonObject): JsonObject = when (method) {
    "tools/list" -> Tools.list(params)
    "tools/call" -> Tools.execute(Tools.parseCall(params))
    else -> invalidParams()
}
