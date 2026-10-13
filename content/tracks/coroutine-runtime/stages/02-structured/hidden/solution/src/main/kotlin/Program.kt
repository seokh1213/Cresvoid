private val namePattern = Regex("[a-z][a-z0-9_-]{0,31}")
private val integerPattern = Regex("0|[1-9][0-9]*")

sealed interface Command {
    val line: Int
    data class Log(val value: String, override val line: Int) : Command
    data class Delay(val millis: Long, override val line: Int) : Command
    data class Spawn(val definition: String, val name: String, val options: ContextOptions, override val line: Int) : Command
    data class Await(val name: String, override val line: Int) : Command
    data class Result(val value: String, override val line: Int) : Command
    data class State(val name: String, override val line: Int) : Command
    data class Start(val name: String, override val line: Int) : Command
    data class Join(val name: String, override val line: Int) : Command
    data class Scope(val definition: String, val name: String, val options: ContextOptions, override val line: Int) : Command
    data class Context(val key: String, override val line: Int) : Command
}

data class ContextOptions(val lazy: Boolean = false, val tag: String? = null, val trace: String? = null)
data class TaskDefinition(val name: String, val async: Boolean, val commands: MutableList<Command> = mutableListOf())
data class RootDefinition(val definition: String, val name: String, val options: ContextOptions, val line: Int)
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
                val options = readOptions(words.drop(3), false)
                if (words.size < 3 || !name(words[1]) || !name(words[2]) || options == null) fail(line, "FORMAT")
                else if (roots.any { it.name == words[2] }) fail(line, "DUPLICATE")
                else roots.add(RootDefinition(words[1], words[2], options, line))
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
            "SPAWN" -> {
                val options = readOptions(words.drop(3), true)
                if (words.size >= 3 && name(words[1]) && name(words[2]) && options != null) Command.Spawn(words[1], words[2], options, line) else null
            }
            "AWAIT" -> if (words.size == 2 && name(words[1])) Command.Await(words[1], line) else null
            "RESULT" -> if (words.size == 2 && name(words[1])) Command.Result(words[1], line) else null
            "STATE" -> if (words.size == 2 && name(words[1])) Command.State(words[1], line) else null
            "START" -> if (words.size == 2 && name(words[1])) Command.Start(words[1], line) else null
            "JOIN" -> if (words.size == 2 && name(words[1])) Command.Join(words[1], line) else null
            "SCOPE" -> {
                val options = readOptions(words.drop(3), false)
                if (words.size >= 3 && name(words[1]) && name(words[2]) && options != null) Command.Scope(words[1], words[2], options, line) else null
            }
            "CONTEXT" -> if (words.size == 2 && words[1] in setOf("tag", "trace")) Command.Context(words[1], line) else null
            else -> null
        }
        if (command == null) fail(line, "FORMAT") else task.commands.add(command)
    }

    private fun readOptions(words: List<String>, lazyAllowed: Boolean): ContextOptions? {
        var index = 0
        var lazy = false
        if (lazyAllowed && words.firstOrNull() == "LAZY") {
            lazy = true
            index++
        }
        var tag: String? = null
        var trace: String? = null
        if (words.getOrNull(index)?.startsWith("tag=") == true) {
            tag = words[index++].substringAfter('=')
            if (!name(tag)) return null
        }
        if (words.getOrNull(index)?.startsWith("trace=") == true) {
            trace = words[index++].substringAfter('=')
            if (!name(trace)) return null
        }
        return if (index == words.size) ContextOptions(lazy, tag, trace) else null
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
            val children = mutableMapOf<String, String>()
            val lazyChildren = mutableMapOf<String, Command.Spawn>()
            for (command in task.commands) when (command) {
                is Command.Spawn -> {
                    if (command.name in children) fail(command.line, "DUPLICATE")
                    else children[command.name] = command.definition
                    if (command.options.lazy) lazyChildren[command.name] = command
                    if (command.definition !in tasks) fail(command.line, "UNKNOWN")
                }
                is Command.Scope -> {
                    if (command.name in children) fail(command.line, "DUPLICATE")
                    else children[command.name] = command.definition
                    val target = tasks[command.definition]
                    if (target == null) fail(command.line, "UNKNOWN")
                    else if (target.async) fail(command.line, "FORMAT")
                }
                is Command.Await -> {
                    val childDefinition = children[command.name]
                    if (childDefinition == null || tasks[childDefinition]?.async == false) fail(command.line, "REFERENCE")
                    lazyChildren.remove(command.name)
                }
                is Command.Start -> { if (command.name !in children) fail(command.line, "REFERENCE"); lazyChildren.remove(command.name) }
                is Command.Join -> { if (command.name !in children) fail(command.line, "REFERENCE"); lazyChildren.remove(command.name) }
                is Command.State -> if (command.name !in children) fail(command.line, "REFERENCE")
                else -> Unit
            }
            lazyChildren.values.forEach { fail(it.line, "REFERENCE") }
        }
    }

    private fun validateCycles() {
        val edges = mutableMapOf<String, MutableList<String>>()
        data class Edge(val source: String, val target: String, val line: Int)
        val references = tasks.values.flatMap { task ->
            task.commands.mapNotNull { command -> when (command) {
                is Command.Spawn -> Edge(task.name, command.definition, command.line)
                is Command.Scope -> Edge(task.name, command.definition, command.line)
                else -> null
            } }
        }.sortedBy { it.line }
        for (edge in references) {
            if (edge.target !in tasks) continue
            if (reachable(edge.target, edge.source, edges)) fail(edge.line, "CYCLE")
            edges.getOrPut(edge.source) { mutableListOf() }.add(edge.target)
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
