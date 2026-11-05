fun main() {
    val ledger = Ledger()
    val payments = Payments(ledger)
    val ledgerCommands = LedgerCommands(ledger)
    val paymentCommands = PaymentCommands(payments)
    val settlements = Settlements(payments)
    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    while (true) {
        val line = input.readLine() ?: break
        val command = line.trim(' ')
        if (command.isEmpty()) continue
        val tokens = command.split(Regex(" +"))
        val response = when (tokens[0]) {
            "IMPORT" -> {
                val rows = List(tokens[2].toInt()) {
                    val fields = checkNotNull(input.readLine()).split(',')
                    SettlementRow(fields[0], fields[1], fields[2].toLong())
                }
                listOf(settlements.import(tokens[1], rows))
            }
            "RECONCILE" -> settlements.reconcile(tokens[1])
            else -> ledgerCommands.execute(tokens)
                ?: listOf(checkNotNull(paymentCommands.execute(tokens)) { "Unsupported command" })
        }
        response.forEach { output.appendLine(it) }
    }
    output.flush()
}
