import java.io.PrintWriter

class Registry(private val output: PrintWriter) {
    private val nodes = sortedSetOf<String>()
    private val keys = sortedMapOf<String, Int>()
    private var ring = HashRing(nodes)

    fun execute(command: Command) {
        when (command) {
            is Command.Hash -> output.println("HASH ${hashPosition(command.identifier)}")
            is Command.Put -> {
                keys.putIfAbsent(command.key, hashPosition(command.key))
                printKey(command.key)
            }
            is Command.Owner -> {
                if (command.key in keys) printKey(command.key) else error("KEY_MISSING")
            }
            is Command.Add -> add(command.node)
            is Command.Remove -> remove(command.node)
            Command.Invalid -> error("INPUT")
        }
    }

    private fun printKey(key: String) {
        val position = keys.getValue(key)
        output.println("KEY $key ${ring.owner(position) ?: "-"} MOD ${ring.moduloOwner(position) ?: "-"}")
    }

    private fun add(node: String) {
        if (node in nodes) return error("NODE_EXISTS")
        nodes.add(node)
        reportMoves()
    }

    private fun remove(node: String) {
        if (node !in nodes) return error("NODE_MISSING")
        nodes.remove(node)
        reportMoves()
    }

    private fun reportMoves() {
        val previous = ring
        ring = HashRing(nodes)
        val moves = keys.mapNotNull { (key, position) ->
            val before = previous.owner(position)
            val after = ring.owner(position)
            if (before == after) null else "MOVE $key ${before ?: "-"} ${after ?: "-"}"
        }
        val moduloMoves = keys.values.count { previous.moduloOwner(it) != ring.moduloOwner(it) }
        output.println("MOVED ${moves.size} MOD $moduloMoves")
        moves.forEach(output::println)
    }

    private fun error(reason: String) {
        output.println("ERROR $reason")
    }
}
