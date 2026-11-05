private sealed interface CacheCommand {
    data class Put(val productId: String, val revision: String, val ttl: Long) : CacheCommand
    data class Get(val productId: String) : CacheCommand
    data class Probe(val productId: String) : CacheCommand
    data class Revision(val productId: String) : CacheCommand
    data class Warm(val productId: String, val revision: String, val ttl: Long) : CacheCommand
    data class Invalidate(val productId: String) : CacheCommand
    data class Advance(val seconds: Long) : CacheCommand
    data object Sweep : CacheCommand
    data object Report : CacheCommand
}

fun main() {
    val input = System.`in`.bufferedReader()
    val configuration = input.readLine() ?: return
    val clock = CatalogClock()
    val cache = ProductCache(parseCapacity(configuration), clock)

    input.forEachLine { line ->
        if (line.isNotBlank()) {
            val command = parseCommand(line)
            execute(command, cache, clock)
        }
    }
}

private fun parseCapacity(configuration: String): Int {
    val header = configuration.trim().split(Regex("\\s+"))
    require(header.size == 2 && header[0] == "CACHE") { "expected CACHE capacity" }
    val capacity = header[1].toInt()
    require(capacity > 0) { "non-positive capacity" }
    return capacity
}

private fun parseCommand(line: String): CacheCommand {
    val fields = line.trim().split(Regex("\\s+"))
    val commandName = fields.first()
    val expectedSize = when (commandName) {
        "PUT", "WARM" -> 4
        "GET", "PROBE", "REVISION", "INVALIDATE", "ADVANCE" -> 2
        "SWEEP", "REPORT" -> 1
        else -> error("unknown command: $commandName")
    }
    require(fields.size == expectedSize) { "invalid command arity: $commandName" }
    return when (commandName) {
        "PUT" -> CacheCommand.Put(fields[1], fields[2], positiveTtl(fields[3]))
        "GET" -> CacheCommand.Get(fields[1])
        "PROBE" -> CacheCommand.Probe(fields[1])
        "REVISION" -> CacheCommand.Revision(fields[1])
        "WARM" -> CacheCommand.Warm(fields[1], fields[2], positiveTtl(fields[3]))
        "INVALIDATE" -> CacheCommand.Invalidate(fields[1])
        "ADVANCE" -> CacheCommand.Advance(fields[1].toLong().also { require(it >= 0) })
        "SWEEP" -> CacheCommand.Sweep
        "REPORT" -> CacheCommand.Report
        else -> error("unreachable command: $commandName")
    }
}

private fun positiveTtl(text: String): Long = text.toLong().also { require(it > 0) }

private fun execute(command: CacheCommand, cache: ProductCache, clock: CatalogClock) {
    when (command) {
        is CacheCommand.Put -> cache.put(command.productId, command.revision, command.ttl)
        is CacheCommand.Get -> cache.read(command.productId)
        is CacheCommand.Probe -> cache.probe(command.productId)
        is CacheCommand.Revision -> cache.revision(command.productId)
        is CacheCommand.Warm -> cache.warm(command.productId, command.revision, command.ttl)
        is CacheCommand.Invalidate -> cache.invalidate(command.productId)
        is CacheCommand.Advance -> clock.advance(command.seconds)
        CacheCommand.Sweep -> cache.sweep()
        CacheCommand.Report -> println(cache.report().format())
    }
}
