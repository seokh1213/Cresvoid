fun main() {
    val console = OrderConsole(OrderBatch())
    System.`in`.bufferedReader().use { input ->
        while (true) {
            val line = input.readLine() ?: break
            if (line.isBlank()) continue
            console.execute(parseCommand(line))
        }
    }
}

class OrderConsole(private val batch: OrderBatch) {
    fun execute(command: Command) {
        when (command) {
            is Command.Add -> println(batch.add(command.orderId, command.failures))
            is Command.Show -> println(batch.show(command.orderId))
            is Command.Run -> printBatch(command)
            Command.Queue -> println(batch.queue())
            Command.Invalid -> println("ERROR invalid-command")
        }
    }

    private fun printBatch(command: Command.Run) {
        val result = batch.run(command.pageSize, command.maxPages)
        result.pages.forEach { println(it.render()) }
        println(result.summary())
    }
}
