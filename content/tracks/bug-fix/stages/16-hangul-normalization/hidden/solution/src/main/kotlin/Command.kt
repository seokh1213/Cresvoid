sealed interface CatalogCommand {
    data class Put(
        val id: Int,
        val title: String,
    ) : CatalogCommand
    data class Remove(
        val id: Int,
    ) : CatalogCommand
    data class Search(
        val text: String,
    ) : CatalogCommand
    data object ListProducts : CatalogCommand
    data class Rejected(val reason: String) : CatalogCommand
}

object CommandParser {
    fun parse(line: String): CatalogCommand {
        val fields = line.split('|')
        return when (fields.first()) {
            "PUT" -> parsePut(fields)
            "REMOVE" -> parseRemove(fields)
            "SEARCH" -> parseSearch(fields)
            "LIST" -> {
                if (fields.size == 1) CatalogCommand.ListProducts
                else CatalogCommand.Rejected("command")
            }
            else -> CatalogCommand.Rejected("command")
        }
    }

    private fun parsePut(fields: List<String>): CatalogCommand {
        if (fields.size != 3) {
            return CatalogCommand.Rejected("command")
        }
        val id = parseId(fields[1])
            ?: return CatalogCommand.Rejected("id")
        val title = fields[2]
        if (!SearchText.hasContent(title)) {
            return CatalogCommand.Rejected("title")
        }
        return CatalogCommand.Put(id, title)
    }

    private fun parseRemove(fields: List<String>): CatalogCommand {
        if (fields.size != 2) {
            return CatalogCommand.Rejected("command")
        }
        val id = parseId(fields[1])
            ?: return CatalogCommand.Rejected("id")
        return CatalogCommand.Remove(id)
    }

    private fun parseSearch(fields: List<String>): CatalogCommand {
        if (fields.size != 2) {
            return CatalogCommand.Rejected("command")
        }
        val text = fields[1]
        if (!SearchText.hasContent(text)) {
            return CatalogCommand.Rejected("query")
        }
        return CatalogCommand.Search(text)
    }

    private fun parseId(text: String): Int? {
        if (text.isEmpty() || text.first() !in '1'..'9') {
            return null
        }
        if (text.any { it !in '0'..'9' }) {
            return null
        }
        return text.toIntOrNull()
    }
}
