import java.math.BigDecimal

enum class SchemaType(val keyword: String) {
    OBJECT("object"),
    ARRAY("array"),
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    NULL("null"),
    ;

    fun matches(value: JsonValue): Boolean = when (this) {
        OBJECT -> value is JsonObject
        ARRAY -> value is JsonArray
        STRING -> value is JsonString
        INTEGER -> value is JsonNumber && value.isInteger()
        NUMBER -> value is JsonNumber
        BOOLEAN -> value is JsonBoolean
        NULL -> value == JsonNull
    }

    companion object {
        fun fromKeyword(keyword: String): SchemaType? = entries.firstOrNull { it.keyword == keyword }
    }
}

class NumberBound(val literal: String, val value: BigDecimal)

class Schema(
    val type: SchemaType?,
    val enumCanonicalJson: Set<String>?,
    val minimum: NumberBound?,
    val maximum: NumberBound?,
    val required: List<String>?,
    val properties: Map<String, Schema>?,
    val items: Schema?,
) {
    val followsTopLevelRules: Boolean
        get() = type == SchemaType.OBJECT && enumCanonicalJson == null && minimum == null && maximum == null && items == null

    fun validationErrors(input: JsonObject): List<String> = buildList { collectErrors(this@Schema, input, path = "") }
}

sealed interface InputSchema {
    data object Absent : InputSchema

    data object Malformed : InputSchema

    class Valid(val schema: Schema) : InputSchema
}

fun inputSchemaOf(json: JsonObject?): InputSchema {
    if (json == null) return InputSchema.Absent
    val schema = parseSchema(json)?.takeIf { it.followsTopLevelRules } ?: return InputSchema.Malformed
    return InputSchema.Valid(schema)
}

private val SCHEMA_KEYWORDS = setOf("type", "description", "properties", "required", "enum", "minimum", "maximum", "items")

private fun parseSchema(json: JsonValue): Schema? {
    if (json !is JsonObject || !SCHEMA_KEYWORDS.containsAll(json.fields.keys)) return null
    val keywords = json.fields
    if (keywords["description"]?.let { it !is JsonString } == true) return null
    return Schema(
        type = keywords["type"]?.let { parseType(it) ?: return null },
        enumCanonicalJson = keywords["enum"]?.let { parseEnum(it) ?: return null },
        minimum = keywords["minimum"]?.let { parseBound(it) ?: return null },
        maximum = keywords["maximum"]?.let { parseBound(it) ?: return null },
        required = keywords["required"]?.let { parseRequired(it) ?: return null },
        properties = keywords["properties"]?.let { parseProperties(it) ?: return null },
        items = keywords["items"]?.let { parseSchema(it) ?: return null },
    )
}

private fun parseType(json: JsonValue): SchemaType? = (json as? JsonString)?.let { SchemaType.fromKeyword(it.value) }

private fun parseEnum(json: JsonValue): Set<String>? =
    (json as? JsonArray)?.items?.takeIf { it.isNotEmpty() }?.mapTo(HashSet()) { it.canonicalJson() }

private fun parseBound(json: JsonValue): NumberBound? = (json as? JsonNumber)?.let { NumberBound(it.literal, it.toBigDecimal()) }

private fun parseRequired(json: JsonValue): List<String>? {
    val names = (json as? JsonArray)?.items?.map { (it as? JsonString)?.value ?: return null } ?: return null
    return names.takeIf { it.size == it.toSet().size }
}

private fun parseProperties(json: JsonValue): Map<String, Schema>? =
    (json as? JsonObject)?.fields?.mapValues { (_, property) -> parseSchema(property) ?: return null }

private fun MutableList<String>.collectErrors(schema: Schema, value: JsonValue, path: String) {
    if (schema.type != null && !schema.type.matches(value)) {
        add("$path expected ${schema.type.keyword}")
        return
    }
    if (schema.enumCanonicalJson != null && value.canonicalJson() !in schema.enumCanonicalJson) add("$path not in enum")
    if (value is JsonNumber) {
        val number = value.toBigDecimal()
        if (schema.minimum != null && number < schema.minimum.value) add("$path below minimum ${schema.minimum.literal}")
        if (schema.maximum != null && number > schema.maximum.value) add("$path above maximum ${schema.maximum.literal}")
    }
    if (value is JsonObject) {
        schema.required.orEmpty().filter { it !in value.fields }.forEach { add("$path/$it required") }
        schema.properties.orEmpty().forEach { (name, property) ->
            value.fields[name]?.let { collectErrors(property, it, "$path/$name") }
        }
    }
    if (value is JsonArray && schema.items != null) {
        value.items.forEachIndexed { index, item -> collectErrors(schema.items, item, "$path/$index") }
    }
}
