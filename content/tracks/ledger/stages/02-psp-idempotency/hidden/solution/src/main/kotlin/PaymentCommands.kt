class PaymentCommands(private val payments: Payments) {
    fun execute(tokens: List<String>): String? = when (tokens[0]) {
        "PAY" -> payments.accept(tokens[1], RequestDetails.Pay(tokens[2], tokens[3], tokens[4], tokens[5].toLong()))
        "REFUND" -> payments.accept(tokens[1], RequestDetails.Refund(tokens[2], tokens[3], tokens[4].toLong()))
        "PSP" -> payments.enqueue(tokens[1], ScriptResult.valueOf(tokens[2]))
        "CALL" -> payments.call(tokens[1])
        "QUERY" -> payments.query(tokens[1])
        "COMMIT" -> payments.commit(tokens[1])
        "STATUS" -> payments.status(tokens[1])
        "REMOTE" -> payments.remote(tokens[1])
        "CRASH" -> payments.crash()
        else -> null
    }
}
