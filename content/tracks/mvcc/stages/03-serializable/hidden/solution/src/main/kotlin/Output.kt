import java.io.PrintStream

private const val NO_VALUE = "(none)"

enum class SyntaxError {
    UNKNOWN_COMMAND,
    BAD_ARGUMENT,
}

enum class StateError {
    EXISTS,
    NO_TX,
    ENDED,
}

class Output {
    private val text = StringBuilder()

    fun begun(transaction: String) = line(transaction, "BEGIN")

    fun value(transaction: String, key: String, value: String?) = line(transaction, "$key=${value ?: NO_VALUE}")

    fun written(transaction: String) = line(transaction, "OK")

    fun scanned(transaction: String, values: Map<String, String>) {
        val pairs = if (values.isEmpty()) NO_VALUE else values.entries.joinToString(" ") { "${it.key}=${it.value}" }
        line(transaction, pairs)
    }

    fun committed(transaction: String) = line(transaction, "COMMIT")

    fun writeConflict(transaction: String, key: String) = line(transaction, "ABORT WRITE_CONFLICT $key")

    fun serializationFailure(transaction: String) = line(transaction, "ABORT SERIALIZATION")

    fun rolledBack(transaction: String) = line(transaction, "ROLLBACK")

    fun waiting(transaction: String, holders: List<String>) = line(transaction, "WAIT ${holders.joinToString(" ")}")

    fun deadlockVictim(transaction: String) = line(transaction, "ABORT DEADLOCK")

    fun stateError(transaction: String, error: StateError) = line(transaction, "ERR ${error.name}")

    fun syntaxError(error: SyntaxError) {
        text.append("ERR ").append(error.name).append('\n')
    }

    fun writeTo(stream: PrintStream) {
        stream.print(text)
        stream.flush()
    }

    private fun line(transaction: String, body: String) {
        text.append(transaction).append(' ').append(body).append('\n')
    }
}
