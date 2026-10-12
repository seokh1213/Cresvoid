class Scenario {
    private val groups = listOf(DetailCommands(), RefreshCommands(), FeedCommands(), PriceCommands())
    private var trace = true

    fun execute(line: String): List<String> {
        val words = line.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val command = words.first()
        val args = words.drop(1)
        if (command == "TRACE") {
            trace = parseSwitch(args)
            return emptyList()
        }
        val group = groups.firstOrNull { command in it.commands }
            ?: throw ScenarioException("알 수 없는 명령이다: $command")
        val output = group.execute(command, args) ?: return emptyList()
        return if (trace) output.events + output.result else listOf(output.result)
    }

    private fun parseSwitch(args: List<String>): Boolean = when (args) {
        listOf("ON") -> true
        listOf("OFF") -> false
        else -> throw ScenarioException("TRACE 뒤에는 ON 이나 OFF 가 온다")
    }
}

interface CommandGroup {
    val commands: Set<String>

    fun execute(command: String, args: List<String>): RunOutput?
}

class RunOutput(val events: List<String>, val result: String)

class ScenarioException(message: String) : RuntimeException(message)

private val NUMBER = Regex("0|[1-9][0-9]{0,9}")
private val NAME = Regex("[a-z0-9-]{1,20}")

fun parseNumber(token: String, range: LongRange = 0L..1_000_000_000L): Long {
    val number = token.takeIf(NUMBER::matches)?.toLong()
    if (number == null || number !in range) throw ScenarioException("범위 안의 정수가 아니다: $token")
    return number
}

fun parseName(token: String): String =
    token.takeIf(NAME::matches) ?: throw ScenarioException("이름이 아니다: $token")

fun parseDuration(token: String): Long = parseNumber(token, 1L..1_000_000_000L)

fun List<String>.requireSize(size: Int, command: String) {
    if (this.size != size) throw ScenarioException("$command 의 인자 수가 맞지 않다")
}
