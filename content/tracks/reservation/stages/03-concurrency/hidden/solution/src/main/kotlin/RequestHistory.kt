class RequestHistory {
    private data class RecordedRequest(val operation: Command.Mutation, val response: String)
    private val requests = mutableMapOf<String, RecordedRequest>()

    fun execute(request: Command.Request, perform: (Command.Mutation) -> String): String {
        val previous = requests[request.id]
        if (previous != null) {
            return if (previous.operation == request.operation) previous.response else "ERROR REQUEST_CONFLICT"
        }
        val response = perform(request.operation)
        requests[request.id] = RecordedRequest(request.operation, response)
        return response
    }
}
