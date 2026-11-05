class HeaderProblem(val reason: String) : RuntimeException(reason)

class ImportHeader private constructor(private val columns: List<String>) {
    val width: Int get() = columns.size

    fun value(record: CsvRecord, column: String): String? {
        val index = columns.indexOf(column)
        return if (index < 0) null else record.fields[index]
    }

    companion object {
        private val required = listOf("sku", "name", "price")
        private val allowed = required + "stock"

        fun from(record: CsvRecord): ImportHeader {
            val columns = record.fields.map { it.trim().lowercase() }
            val empty = columns.indexOfFirst { it.isEmpty() }
            if (empty >= 0) throw HeaderProblem("EMPTY(${empty + 1})")
            val seen = mutableSetOf<String>()
            for (column in columns) {
                if (!seen.add(column)) throw HeaderProblem("DUPLICATE($column)")
                if (column !in allowed) throw HeaderProblem("UNKNOWN($column)")
            }
            for (column in required) {
                if (column !in columns) throw HeaderProblem("MISSING($column)")
            }
            return ImportHeader(columns)
        }
    }
}
