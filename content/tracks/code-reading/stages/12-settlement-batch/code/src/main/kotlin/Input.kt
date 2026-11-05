import java.time.LocalDate

sealed interface BatchCommand {
    data class AddRow(val row: OrderRow) : BatchCommand
    data class Report(val method: AggregationMethod) : BatchCommand
    data class Audit(val seller: String) : BatchCommand
    data class Top(val method: AggregationMethod) : BatchCommand
    data class Plan(
        val method: AggregationMethod,
        val request: ScheduleRequest,
    ) : BatchCommand
    data object Clear : BatchCommand
}

fun parseCommand(line: String): BatchCommand {
    val fields = line.trim().split(Regex("\\s+"))
    return when (fields.first()) {
        "ROW" -> {
            require(fields.size == 7)
            BatchCommand.AddRow(parseRow(fields))
        }
        "REPORT" -> {
            require(fields.size == 2)
            BatchCommand.Report(AggregationMethod.valueOf(fields[1]))
        }
        "AUDIT" -> {
            require(fields.size == 2)
            BatchCommand.Audit(fields[1])
        }
        "TOP" -> {
            require(fields.size == 2)
            BatchCommand.Top(AggregationMethod.valueOf(fields[1]))
        }
        "PLAN" -> parsePlan(fields)
        "CLEAR" -> {
            require(fields.size == 1)
            BatchCommand.Clear
        }
        else -> throw IllegalArgumentException("unknown command")
    }
}

private fun parseRow(fields: List<String>): OrderRow = OrderRow(
    seller = fields[1],
    orderedOn = LocalDate.parse(fields[2]),
    quantity = fields[3].toInt(),
    unitPrice = fields[4].toInt(),
    feeBps = fields[5].toInt(),
    orderId = fields[6],
)

private fun parsePlan(fields: List<String>): BatchCommand.Plan {
    require(fields.size == 5)
    val schedule = fields[3].split(':')
    require(schedule.size == 2)
    return BatchCommand.Plan(
        method = AggregationMethod.valueOf(fields[1]),
        request = ScheduleRequest(
            anchor = LocalDate.parse(fields[2]),
            monthCount = schedule[0].toInt(),
            skipMonths = schedule[1].toInt(),
            banks = fields[4].split(','),
        ),
    )
}
