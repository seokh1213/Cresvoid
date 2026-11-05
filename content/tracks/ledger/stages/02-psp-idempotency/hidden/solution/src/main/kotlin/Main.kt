fun main() {
    val ledger = Ledger()
    val ledgerCommands = LedgerCommands(ledger)
    val paymentCommands = PaymentCommands(Payments(ledger))
    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    input.forEachLine { line ->
        val command = line.trim(' ')
        if (command.isNotEmpty()) {
            val tokens = command.split(Regex(" +"))
            val response = ledgerCommands.execute(tokens)
                ?: listOf(checkNotNull(paymentCommands.execute(tokens)) { "Unsupported command" })
            response.forEach { output.appendLine(it) }
        }
    }
    output.flush()
}
