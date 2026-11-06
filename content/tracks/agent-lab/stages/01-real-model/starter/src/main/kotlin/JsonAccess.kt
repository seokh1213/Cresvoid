import java.math.BigDecimal

fun json(vararg fields: Pair<String, JsonValue>) = JsonObject(linkedMapOf(*fields))
fun text(value: String) = JsonString(value)
fun number(value: Number) = JsonNumber(value.toString())
fun JsonValue.obj(): JsonObject = this as? JsonObject ?: error("expected object")
fun JsonValue.array(): List<JsonValue> = (this as? JsonArray)?.items ?: error("expected array")
fun JsonValue.string(): String = (this as? JsonString)?.value ?: error("expected string")
operator fun JsonValue.get(name: String): JsonValue = obj().fields[name] ?: error("missing field")
fun JsonValue.optional(name: String): JsonValue? = obj().fields[name]
fun JsonValue.decimal(): BigDecimal = (this as? JsonNumber)?.literal?.toBigDecimal() ?: error("expected number")
fun JsonValue.integer(): Long = decimal().longValueExact()
