import java.io.PrintWriter
import java.math.BigDecimal
import java.math.RoundingMode

class Registry(private val output: PrintWriter) {
    private val nodes = sortedMapOf<String, Int>()
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
            is Command.Add -> add(command.node, 1)
            is Command.Remove -> remove(command.node)
            is Command.VirtualAdd -> add(command.node, 16 * command.capacity)
            is Command.Weight -> weight(command.node, command.capacity)
            Command.Load -> printLoad()
            Command.Invalid -> error("INPUT")
        }
    }

    private fun printKey(key: String) {
        val position = keys.getValue(key)
        output.println("KEY $key ${ring.owner(position) ?: "-"} MOD ${ring.moduloOwner(position) ?: "-"}")
    }

    private fun add(node: String, tokenCount: Int) {
        if (node in nodes) return error("NODE_EXISTS")
        nodes[node] = tokenCount
        reportMoves()
    }

    private fun weight(node: String, capacity: Int) {
        if (node !in nodes) return error("NODE_MISSING")
        nodes[node] = 16 * capacity
        reportMoves()
    }

    private fun printLoad() {
        val counts = ring.nodes.associateWith { 0 }.toMutableMap()
        for (position in keys.values) {
            val owner = ring.owner(position) ?: continue
            counts[owner] = counts.getValue(owner) + 1
        }
        output.println("LOAD ${nodes.size} ${keys.size}")
        counts.forEach { (node, count) -> output.println("NODE $node $count") }
        val ratio = if (nodes.isEmpty() || keys.isEmpty()) "0.000000" else {
            BigDecimal.valueOf(counts.values.max().toLong() * nodes.size)
                .divide(BigDecimal.valueOf(keys.size.toLong()), 6, RoundingMode.HALF_UP)
                .toPlainString()
        }
        output.println("RATIO $ratio")
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
