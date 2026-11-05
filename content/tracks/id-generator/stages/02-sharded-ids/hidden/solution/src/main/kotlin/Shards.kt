private class Shard(var owner: String, val issuer: IdIssuer)

class Shards {
    private var settings: Settings? = null
    private val placements = mutableMapOf<Long, Shard>()

    fun execute(tokens: List<String>): String {
        if (tokens[0] == "SHARD_CONFIG") return configure(tokens)
        val current = settings ?: return "ERR UNCONFIGURED"
        return when (tokens[0]) {
            "PLACE", "MOVE", "SHARD_NEXT" -> changeShard(current, tokens)
            "SHARD_DECODE", "LOCATE" -> locateOrDecode(current.layout, tokens)
            else -> error("Unknown shard command")
        }
    }

    private fun configure(tokens: List<String>): String {
        val replacement = Settings.parse(tokens) ?: return "ERR CONFIG"
        settings = replacement
        placements.clear()
        return "OK"
    }

    private fun changeShard(current: Settings, tokens: List<String>): String {
        val number = tokens[1].toLong()
        if (!current.layout.containsMachine(number)) return "ERR SHARD"
        if (tokens[0] == "PLACE") {
            if (number in placements) return "ERR EXISTS"
            placements[number] = Shard(tokens[2], IdIssuer(number))
            return "OK"
        }
        val shard = placements[number] ?: return "ERR UNMAPPED"
        if (shard.owner != tokens[2]) return "ERR OWNER"
        return when (tokens[0]) {
            "MOVE" -> {
                shard.owner = tokens[3]
                "OK"
            }
            "SHARD_NEXT" -> shard.issuer.next(current, tokens.drop(4).map(String::toLong)).render()
            else -> error("Unknown shard mutation")
        }
    }

    private fun locateOrDecode(layout: Layout, tokens: List<String>): String {
        val id = tokens[1].toLong()
        if (!layout.containsId(id)) return "ERR ID"
        val parts = layout.decode(id)
        if (tokens[0] == "SHARD_DECODE") return parts.render()
        val shard = placements[parts.machine] ?: return "ERR UNMAPPED"
        return "SHARD ${parts.machine} ${shard.owner}"
    }
}
