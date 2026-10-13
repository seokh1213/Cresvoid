private val namePattern = Regex("[a-z][a-z0-9_-]{0,31}")
private val integerPattern = Regex("0|[1-9][0-9]*")

sealed interface Command {
    val line: Int
    data class Log(val value: String, override val line: Int) : Command
    data class Delay(val millis: Long, override val line: Int) : Command
    data class Spawn(val definition: String, val name: String, override val line: Int) : Command
    data class Await(val name: String, override val line: Int) : Command
    data class Result(val value: String, override val line: Int) : Command
}

data class TaskDefinition(val name: String, val async: Boolean, val commands: MutableList<Command> = mutableListOf())
data class RootDefinition(val definition: String, val name: String, val line: Int)
data class Program(val tasks: Map<String, TaskDefinition>, val roots: List<RootDefinition>)
data class InputError(val line: Int, val code: String)

sealed interface ParseResult {
    data class Valid(val program: Program) : ParseResult
    data class Invalid(val error: InputError) : ParseResult
}

class ProgramParser(private val lines: List<String>) {
    private val tasks = linkedMapOf<String, TaskDefinition>()
    private val roots = mutableListOf<RootDefinition>()
    private val errors = mutableListOf<InputError>()
    private var current: TaskDefinition? = null
    private var readingRoots = false
    private var ran = false

    fun parse(): ParseResult {
        lines.forEachIndexed { index, raw -> readLine(raw, index + 1) }
        val missingLine = lines.size + 1
        if (current != null || !ran) fail(missingLine, "FORMAT")
        if (tasks.isEmpty() || roots.isEmpty()) fail(lines.indexOfLast { it.trim(' ') == "RUN" }.takeIf { it >= 0 }?.plus(1) ?: missingLine, "FORMAT")
        validateReferences()
        validateCycles()
        val first = errors.minWithOrNull(compareBy<InputError>({ it.line }, { priority(it.code) }))
        return if (first == null) ParseResult.Valid(Program(tasks, roots)) else ParseResult.Invalid(first)
    }

    private fun readLine(raw: String, line: Int) {
        val trimmed = raw.trim(' ')
        if (trimmed.isEmpty()) return
        if (trimmed.any { it.isWhitespace() && it != ' ' } || ran) {
            fail(line, "FORMAT")
            return
        }
        val words = trimmed.split(Regex(" +"))
        val task = current
        if (task != null) {
            if (words == listOf("END")) {
                checkResult(task, line)
                current = null
            } else {
                readCommand(task, words, line)
            }
            return
        }
        when (words.first()) {
            "TASK" -> {
                if (readingRoots || words.size != 3 || !name(words[1]) || words[2] !in setOf("LAUNCH", "ASYNC")) {
                    fail(line, "FORMAT")
                } else if (words[1] in tasks) {
                    fail(line, "DUPLICATE")
                } else {
                    current = TaskDefinition(words[1], words[2] == "ASYNC").also { tasks[it.name] = it }
                }
            }
            "ROOT" -> {
                readingRoots = true
                if (tasks.isEmpty()) fail(line, "FORMAT")
                if (words.size != 3 || !name(words[1]) || !name(words[2])) fail(line, "FORMAT")
                else if (roots.any { it.name == words[2] }) fail(line, "DUPLICATE")
                else roots.add(RootDefinition(words[1], words[2], line))
            }
            "RUN" -> if (words.size == 1) ran = true else fail(line, "FORMAT")
            else -> fail(line, "FORMAT")
        }
    }

    private fun readCommand(task: TaskDefinition, words: List<String>, line: Int) {
        val command = when (words.first()) {
            "LOG" -> if (words.size == 2 && name(words[1])) Command.Log(words[1], line) else null
            "DELAY" -> if (words.size == 2 && integerPattern.matches(words[1])) {
                words[1].toLongOrNull()?.takeIf { it <= 1_000_000_000 }?.let { Command.Delay(it, line) }
            } else null
            "SPAWN" -> if (words.size == 3 && name(words[1]) && name(words[2])) Command.Spawn(words[1], words[2], line) else null
            "AWAIT" -> if (words.size == 2 && name(words[1])) Command.Await(words[1], line) else null
            "RESULT" -> if (words.size == 2 && name(words[1])) Command.Result(words[1], line) else null
            else -> null
        }
        if (command == null) fail(line, "FORMAT") else task.commands.add(command)
    }

    private fun checkResult(task: TaskDefinition, endLine: Int) {
        val results = task.commands.filterIsInstance<Command.Result>()
        if (!task.async) results.forEach { fail(it.line, "FORMAT") }
        else {
            if (results.isEmpty()) fail(endLine, "FORMAT")
            results.drop(1).forEach { fail(it.line, "FORMAT") }
            results.firstOrNull()?.takeIf { it != task.commands.last() }?.let { fail(it.line, "FORMAT") }
        }
    }

    private fun validateReferences() {
        for (root in roots) {
            val task = tasks[root.definition]
            if (task == null) fail(root.line, "UNKNOWN")
            else if (task.async) fail(root.line, "FORMAT")
        }
        for (task in tasks.values) {
            val children = mutableMapOf<String, Command.Spawn>()
            for (command in task.commands) when (command) {
                is Command.Spawn -> {
                    if (command.name in children) fail(command.line, "DUPLICATE")
                    else children[command.name] = command
                    if (command.definition !in tasks) fail(command.line, "UNKNOWN")
                }
                is Command.Await -> {
                    val spawn = children[command.name]
                    if (spawn == null || tasks[spawn.definition]?.async == false) fail(command.line, "REFERENCE")
                }
                else -> Unit
            }
        }
    }

    private fun validateCycles() {
        val edges = mutableMapOf<String, MutableList<String>>()
        val references = tasks.values.flatMap { task ->
            task.commands.filterIsInstance<Command.Spawn>().map { task.name to it }
        }.sortedBy { it.second.line }
        for ((source, command) in references) {
            if (command.definition !in tasks) continue
            if (reachable(command.definition, source, edges)) fail(command.line, "CYCLE")
            edges.getOrPut(source) { mutableListOf() }.add(command.definition)
        }
    }

    private fun reachable(start: String, destination: String, edges: Map<String, List<String>>): Boolean {
        val stack = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        stack.add(start)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node == destination) return true
            if (seen.add(node)) edges[node]?.forEach(stack::addLast)
        }
        return false
    }

    private fun fail(line: Int, code: String) { errors.add(InputError(line, code)) }
    private fun name(value: String): Boolean = namePattern.matches(value)
    private fun priority(code: String): Int = listOf("FORMAT", "DUPLICATE", "UNKNOWN", "REFERENCE", "CYCLE").indexOf(code)
}
