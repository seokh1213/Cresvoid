sealed interface Command {
    data class Add(val term: String, val amount: Long) : Command
    data class Query(val count: Int, val prefix: String) : Command
    data class Cache(val mode: CacheMode, val capacity: Int) : Command
    data class CachedQuery(val count: Int, val prefix: String) : Command
    data object Refresh : Command
    data object Cost : Command
}

object CommandParser {
    private val separators = Regex("[ \t]+")

    fun parse(line: String): Command? {
        val fields = line.trim(' ', '\t').split(separators)
        return when (fields.first()) {
            "ADD" -> if (fields.size == 3 && validWord(fields[1])) {
                number(fields[2], Long.MAX_VALUE)?.let { Command.Add(fields[1], it) }
            } else null
            "QUERY" -> query(fields)?.let { Command.Query(it.count, it.prefix) }
            "CACHE" -> if (fields.size == 3) {
                val mode = CacheMode.entries.find { it.name == fields[1] }
                val capacity = number(fields[2], 100)?.toInt()
                if (mode != null && capacity != null) Command.Cache(mode, capacity) else null
            } else null
            "CQUERY" -> query(fields)?.let { Command.CachedQuery(it.count, it.prefix) }
            "REFRESH" -> Command.Refresh.takeIf { fields.size == 1 }
            "COST" -> Command.Cost.takeIf { fields.size == 1 }
            else -> null
        }
    }

    private data class QueryArguments(val count: Int, val prefix: String)

    private fun query(fields: List<String>): QueryArguments? {
        if (fields.size != 3) return null
        val count = number(fields[1], 100)?.toInt() ?: return null
        val prefix = prefix(fields[2]) ?: return null
        return QueryArguments(count, prefix)
    }

    private fun prefix(text: String): String? = when {
        text == "*" -> ""
        validWord(text) -> text
        else -> null
    }

    private fun validWord(text: String): Boolean = text.length in 1..32 && text.all {
        it in 'a'..'z' || it in '0'..'9' || it in '\u1100'..'\u11ff' ||
            it in '\u3131'..'\u318e' || it in '\uac00'..'\ud7a3'
    }

    private fun number(text: String, maximum: Long): Long? {
        if (text.isEmpty()) return null
        var value = 0L
        for (digit in text) {
            if (digit !in '0'..'9') return null
            val amount = digit - '0'
            if (value > (maximum - amount) / 10) return null
            value = value * 10 + amount
        }
        return value.takeIf { it in 1..maximum }
    }
}
