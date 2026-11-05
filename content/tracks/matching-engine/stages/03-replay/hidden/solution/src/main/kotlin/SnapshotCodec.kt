import java.io.BufferedReader

object SnapshotCodec {
    fun write(engine: MatchingEngine, lastSequence: Long, output: (String) -> Unit) {
        output("SNAPSHOT $lastSequence ${engine.arrivalClock} ${engine.tradeClock} ${engine.usedIds.size} ${engine.book.size}")
        engine.usedIds.sorted().forEach { output("USED $it") }
        engine.book.byArrival().forEach { order ->
            output("OPEN ${order.id} ${order.owner} ${order.side} ${order.price} ${order.remaining} ${order.arrival}")
        }
    }

    fun read(header: SnapshotHeader, input: BufferedReader, output: (String) -> Unit): MatchingEngine {
        val engine = MatchingEngine(output)
        repeat(header.usedCount) {
            engine.usedIds.add(CommandParser.tokens(checkNotNull(input.readLine()))[1])
        }
        repeat(header.openCount) {
            val fields = CommandParser.tokens(checkNotNull(input.readLine()))
            engine.book.add(Order(fields[1], fields[2], Side.valueOf(fields[3]), fields[4].toLong(), fields[5].toLong(), fields[6].toLong()))
        }
        engine.restoreClocks(header.arrivalClock, header.tradeClock)
        return engine
    }
}
