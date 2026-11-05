import java.time.DateTimeException

fun main() {
    val batch = SettlementBatch()
    System.`in`.bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() }.forEach { line ->
            try {
                batch.execute(parseCommand(line))?.let(::println)
            } catch (_: IllegalArgumentException) {
                println("ERROR")
            } catch (_: DateTimeException) {
                println("ERROR")
            } catch (_: ArithmeticException) {
                println("ERROR")
            }
        }
    }
}

class SettlementBatch {
    private val rows = mutableListOf<OrderRow>()
    private val orderIds = mutableSetOf<String>()
    private val calculator = SettlementCalculator()
    private val planner = PayoutPlanner()

    fun execute(command: BatchCommand): String? = when (command) {
        is BatchCommand.AddRow -> {
            require(command.row.orderId !in orderIds)
            rows.add(command.row)
            orderIds.add(command.row.orderId)
            null
        }
        is BatchCommand.Report -> {
            val settlements = calculator.calculate(rows, command.method)
            BatchOutput.report(settlements)
        }
        is BatchCommand.Audit -> BatchOutput.audit(calculator.audit(rows, command.seller))
        is BatchCommand.Top -> {
            val settlements = calculator.calculate(rows, command.method)
            BatchOutput.top(planner.highestPayment(settlements))
        }
        is BatchCommand.Plan -> {
            val settlements = calculator.calculate(rows, command.method)
            BatchOutput.preview(planner.preview(settlements, command.request))
        }
        BatchCommand.Clear -> {
            rows.clear()
            orderIds.clear()
            null
        }
    }
}
