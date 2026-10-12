private val NAME_FORMAT = Regex("[A-Za-z0-9._-]{1,64}")

class DfsPath private constructor(val names: List<String>) {
    val isRoot: Boolean get() = names.isEmpty()

    val lastName: String get() = names.last()

    val parentNames: List<String> get() = names.dropLast(1)

    fun isAncestorOf(other: DfsPath): Boolean = other.names.size > names.size && other.names.subList(0, names.size) == names

    companion object {
        fun parse(text: String): DfsPath? {
            if (text == "/") return DfsPath(emptyList())
            if (!text.startsWith('/')) return null
            val names = text.substring(1).split('/')
            return if (names.all(::isName)) DfsPath(names) else null
        }

        private fun isName(text: String): Boolean = NAME_FORMAT.matches(text) && text != "." && text != ".."
    }
}
