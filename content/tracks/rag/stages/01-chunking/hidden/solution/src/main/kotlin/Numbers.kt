object Arguments {
    private val integer = Regex("0|[1-9][0-9]*")

    fun count(fields: List<String>, expected: Int) {
        if (fields.size != expected) fail("BAD_ARGUMENT")
    }

    fun integer(text: String, range: IntRange): Int {
        if (!integer.matches(text)) fail("BAD_ARGUMENT")
        val value = text.toIntOrNull() ?: fail("BAD_ARGUMENT")
        if (value !in range) fail("BAD_ARGUMENT")
        return value
    }

    inline fun <reified T : Enum<T>> choice(text: String): T =
        enumValues<T>().firstOrNull { it.name == text } ?: fail("BAD_ARGUMENT")

    fun config(fields: List<String>): ChunkConfig {
        count(fields, 4)
        val size = integer(fields[0], 1..10_000)
        return ChunkConfig(size, integer(fields[1], 0 until size), choice(fields[2]), choice(fields[3]))
    }
}
