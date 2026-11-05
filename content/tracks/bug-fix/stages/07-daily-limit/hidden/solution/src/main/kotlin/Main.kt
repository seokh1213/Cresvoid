class LimitApplication(
    private val reader: CommandReader,
    private val clock: CommandClock,
    private val service: DailyLimitService,
) {
    fun handle(line: String): LimitReply? {
        if (line.isBlank()) return null
        val command = reader.read(line)
        clock.advanceTo(command.occurredAt)
        return service.execute(command)
    }
}

fun main() {
    val clock = CommandClock()
    val calendar = UsageCalendar()
    val accountingDay = AccountingDay(clock, calendar)
    val service = DailyLimitService(UsageLedger(), accountingDay)
    val application = LimitApplication(CommandReader(), clock, service)

    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    input.use { lines ->
        output.use { replies ->
            while (true) {
                val line = lines.readLine() ?: break
                val reply = application.handle(line) ?: continue
                replies.write(reply.render())
                replies.newLine()
            }
        }
    }
}
