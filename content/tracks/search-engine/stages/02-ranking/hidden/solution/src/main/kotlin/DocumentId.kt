@JvmInline
value class DocumentId private constructor(private val value: Int) : Comparable<DocumentId> {

    override fun compareTo(other: DocumentId): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()

    companion object {
        private val FORMAT = Regex("[1-9][0-9]{0,8}")

        fun parseOrNull(text: String): DocumentId? =
            if (FORMAT.matches(text)) DocumentId(text.toInt()) else null
    }
}
