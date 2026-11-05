import kotlin.math.abs
import kotlin.math.floor

object Arguments {
    private val integer = Regex("0|[1-9][0-9]*")
    private val decimal = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]{1,6})?")

    fun count(fields: List<String>, expected: Int) {
        if (fields.size != expected) fail("BAD_ARGUMENT")
    }

    fun integer(text: String, range: IntRange): Int {
        if (!integer.matches(text)) fail("BAD_ARGUMENT")
        val value = text.toIntOrNull() ?: fail("BAD_ARGUMENT")
        if (value !in range) fail("BAD_ARGUMENT")
        return value
    }

    fun real(text: String): Double {
        if (!decimal.matches(text)) fail("BAD_ARGUMENT")
        val value = text.toDoubleOrNull() ?: fail("BAD_ARGUMENT")
        if (!value.isFinite() || abs(value) > 1000.0) fail("BAD_ARGUMENT")
        return value
    }

    fun vector(text: String): DoubleArray {
        val parts = text.split(',')
        if (parts.size !in 1..32) fail("BAD_ARGUMENT")
        return parts.map(::real).toDoubleArray()
    }

    fun chunkId(text: String): ChunkId {
        val parts = text.split(':')
        count(parts, 2)
        return ChunkId(integer(parts[0], 1..1_000_000_000), integer(parts[1], 0..1_000_000_000))
    }

    inline fun <reified T : Enum<T>> choice(text: String): T =
        enumValues<T>().firstOrNull { it.name == text } ?: fail("BAD_ARGUMENT")

    fun config(fields: List<String>): ChunkConfig {
        count(fields, 4)
        val size = integer(fields[0], 1..10_000)
        return ChunkConfig(size, integer(fields[1], 0 until size), choice(fields[2]), choice(fields[3]))
    }
}

fun roundedUnits(value: Double, places: Int): Long = floor(value * scale(places) + 0.5).toLong()
fun scale(places: Int): Long = if (places == 4) 10_000L else 1_000_000L
fun fixedUnits(units: Long, places: Int): String {
    val magnitude = abs(units)
    val sign = if (units < 0) "-" else ""
    return "$sign${magnitude / scale(places)}.${(magnitude % scale(places)).toString().padStart(places, '0')}"
}
fun fixed(value: Double): String = fixedUnits(roundedUnits(value, 6), 6)
