fun main() {
    val sources = TransferSources()
    SegmentStore().use { store ->
        val receiver = SegmentReceiver(sources, store)
        generateSequence(::readlnOrNull).forEach { line ->
            val command = line.trim()
            if (command.isNotEmpty()) {
                println(execute(command.split(Regex("\\s+")), sources, receiver, store))
            }
        }
    }
}

private fun execute(
    tokens: List<String>,
    sources: TransferSources,
    receiver: SegmentReceiver,
    store: SegmentStore,
): String = when (tokens[0]) {
    "SOURCE" -> {
        val content = decodeHex(tokens[2])
        val responseLimits = tokens[3].split(',').map(String::toInt)
        sources.register(tokens[1], content, responseLimits)
        "SOURCE ${tokens[1]} ${content.size}"
    }
    "FETCH" -> {
        val request = SegmentRequest(tokens[1], tokens[2], tokens[3].toInt(), tokens[4].toInt())
        receiver.receive(request)
    }
    "SHOW" -> store.describe(tokens[1])
    else -> error("Unknown command")
}

private fun decodeHex(encoded: String): ByteArray {
    if (encoded == "-") return byteArrayOf()
    return ByteArray(encoded.length / 2) { index ->
        encoded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
