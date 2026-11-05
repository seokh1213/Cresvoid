data class OptionSelection(
    val name: String,
    val values: Array<String>,
) {
    override fun equals(other: Any?): Boolean =
        other is OptionSelection && name == other.name && values.contentEquals(other.values)

    override fun hashCode(): Int = 31 * name.hashCode() + values.contentHashCode()
}

object OptionText {
    private val identifier = Regex("[A-Za-z0-9_-]+")

    fun parse(encoded: String): List<OptionSelection> {
        if (encoded == "-") return emptyList()

        val selections = encoded.split(';').map { group ->
            val assignment = group.split('=')
            require(assignment.size == 2)
            val name = assignment[0]
            val values = assignment[1].split(',')
            require(identifier.matches(name))
            require(values.all { identifier.matches(it) })
            require(values.distinct().size == values.size)
            OptionSelection(name, values.sorted().toTypedArray())
        }
        require(selections.map { it.name }.distinct().size == selections.size)
        return selections.sortedBy { it.name }
    }

    fun format(selections: List<OptionSelection>): String {
        if (selections.isEmpty()) return "-"
        return selections.joinToString(";") { selection ->
            "${selection.name}=${selection.values.joinToString(",")}"
        }
    }

    fun productId(token: String): String {
        require(identifier.matches(token))
        return token
    }
}
