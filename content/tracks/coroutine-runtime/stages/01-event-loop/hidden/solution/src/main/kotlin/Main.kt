import java.nio.charset.StandardCharsets

fun main() {
    val lines = System.`in`.bufferedReader(StandardCharsets.UTF_8).readLines()
    when (val parsed = ProgramParser(lines).parse()) {
        is ParseResult.Invalid -> println("ERR ${parsed.error.line} ${parsed.error.code}")
        is ParseResult.Valid -> print(ProgramRunner(parsed.program).run())
    }
}

class ProgramRunner(private val program: Program) {
    private val output = StringBuilder()
    private val runtime = VirtualRuntime { time, path, event ->
        output.append(time).append(' ').append(path).append(' ').append(event).append('\n')
    }

    fun run(): String {
        runtime.run {
            program.roots.forEach { root ->
                val task = program.tasks.getValue(root.definition)
                runtime.launchRoot(root.name) { execute(task) }
            }
        }
        return output.toString()
    }

    private suspend fun RuntimeScope.execute(task: TaskDefinition): String {
        val children = mutableMapOf<String, RuntimeDeferred<String>>()
        for (command in task.commands) when (command) {
            is Command.Log -> runtime.record(context, "LOG ${command.value}")
            is Command.Delay -> delay(command.millis)
            is Command.Spawn -> {
                val childTask = program.tasks.getValue(command.definition)
                val path = context[PathElement]!!.path + "." + command.name
                if (childTask.async) {
                    children[command.name] = async(context = PathElement(path)) { execute(childTask) }
                } else {
                    launch(context = PathElement(path)) { execute(childTask) }
                }
            }
            is Command.Await -> {
                val value = children.getValue(command.name).await()
                runtime.record(context, "VALUE ${command.name} $value")
            }
            is Command.Result -> return command.value
        }
        return ""
    }
}
