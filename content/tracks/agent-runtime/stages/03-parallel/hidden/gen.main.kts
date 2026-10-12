import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.random.Random

data class Options(val asks: Int, val fakes: Int, val uses: Int, val catalogs: Int, val enumSize: Int, val wide: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--asks" to "60",
        "--fakes" to "200",
        "--uses" to "6",
        "--catalogs" to "2",
        "--enum" to "40",
        "--wide" to "0",
        "--seed" to "1",
    )
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        asks = values.getValue("--asks").toInt(),
        fakes = values.getValue("--fakes").toInt(),
        uses = values.getValue("--uses").toInt(),
        catalogs = values.getValue("--catalogs").toInt(),
        enumSize = values.getValue("--enum").toInt(),
        wide = values.getValue("--wide").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val output = StringBuilder()
val largeScale = options.enumSize >= 1_000
val wideRun = options.wide > 0

fun emit(line: String) {
    output.append(line).append('\n')
}

class WeightedSampler<T>(private val values: List<T>, weights: List<Double>) {
    private val cumulativeWeights = weights.runningReduce(Double::plus)

    fun sample(): T {
        val target = random.nextDouble() * cumulativeWeights.last()
        var low = 0
        var high = cumulativeWeights.lastIndex
        while (low < high) {
            val middle = (low + high) / 2
            if (cumulativeWeights[middle] > target) high = middle else low = middle + 1
        }
        return values[low]
    }
}

fun <T> zipf(values: List<T>, offset: Double = 1.5): WeightedSampler<T> =
    WeightedSampler(values, values.indices.map { 1.0 / (it + offset) })

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun skewedIndex(size: Int): Int {
    val target = random.nextDouble()
    val index = (size * target * target * target).toInt()
    return index.coerceIn(0, size - 1)
}

sealed interface Json

data class JStr(val value: String) : Json

data class JNum(val value: BigDecimal) : Json

data class JLit(val literal: String) : Json

data class JBool(val value: Boolean) : Json

data object JNull : Json

data class JArr(val items: List<Json>) : Json

data class JObj(val fields: List<Pair<String, Json>>) : Json

fun JObj.field(name: String): Json? = fields.firstOrNull { it.first == name }?.second

class JsonStyle(val spacing: Double, val escapes: Double, val numberVariety: Double, val shuffleFields: Boolean)

val COMPACT = JsonStyle(spacing = 0.0, escapes = 0.0, numberVariety = 0.0, shuffleFields = false)

fun commandStyle(): JsonStyle = when (random.nextInt(10)) {
    0 -> JsonStyle(spacing = 0.5, escapes = 0.05, numberVariety = 0.3, shuffleFields = true)
    1 -> JsonStyle(spacing = 0.1, escapes = 0.3, numberVariety = 0.5, shuffleFields = false)
    2, 3 -> JsonStyle(spacing = 0.05, escapes = 0.02, numberVariety = 0.2, shuffleFields = true)
    else -> JsonStyle(spacing = 0.0, escapes = 0.01, numberVariety = 0.05, shuffleFields = false)
}

fun schemaStyle(): JsonStyle = when (random.nextInt(6)) {
    0 -> JsonStyle(spacing = 0.3, escapes = 0.03, numberVariety = 0.5, shuffleFields = false)
    1 -> JsonStyle(spacing = 0.0, escapes = 0.1, numberVariety = 0.8, shuffleFields = false)
    else -> JsonStyle(spacing = 0.0, escapes = 0.0, numberVariety = 0.1, shuffleFields = false)
}

val VARIANT = JsonStyle(spacing = 0.05, escapes = 0.05, numberVariety = 0.9, shuffleFields = true)

val GAPS = listOf(" ", " ", "  ", "\t", " \t")

fun Json.render(style: JsonStyle = COMPACT): String = buildString { appendJson(this@render, style) }

fun StringBuilder.gap(style: JsonStyle) {
    if (chance(style.spacing)) append(GAPS.random(random))
}

fun StringBuilder.appendJson(value: Json, style: JsonStyle) {
    when (value) {
        is JStr -> appendJsonString(value.value, style)
        is JNum -> append(numberLiteral(value.value, style.numberVariety))
        is JLit -> append(value.literal)
        is JBool -> append(value.value)
        JNull -> append("null")
        is JArr -> {
            append('[')
            value.items.forEachIndexed { index, item ->
                if (index > 0) append(',')
                gap(style)
                appendJson(item, style)
                gap(style)
            }
            append(']')
        }
        is JObj -> {
            append('{')
            val fields = if (style.shuffleFields && chance(0.7)) value.fields.shuffled(random) else value.fields
            fields.forEachIndexed { index, (name, item) ->
                if (index > 0) append(',')
                gap(style)
                appendJsonString(name, style)
                gap(style)
                append(':')
                gap(style)
                appendJson(item, style)
                gap(style)
            }
            append('}')
        }
    }
}

fun StringBuilder.appendJsonString(text: String, style: JsonStyle) {
    append('"')
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        index += Character.charCount(codePoint)
        when {
            codePoint == '"'.code -> append(if (chance(style.escapes)) hex4(codePoint) else "\\\"")
            codePoint == '\\'.code -> append(if (chance(style.escapes)) hex4(codePoint) else "\\\\")
            codePoint < 0x20 -> append(controlEscape(codePoint))
            codePoint == '/'.code && chance(style.escapes * 3) -> append("\\/")
            chance(style.escapes) -> append(unicodeEscape(codePoint))
            else -> appendCodePoint(codePoint)
        }
    }
    append('"')
}

val SHORT_ESCAPES = mapOf(8 to "\\b", 9 to "\\t", 10 to "\\n", 12 to "\\f", 13 to "\\r")

fun controlEscape(codePoint: Int): String {
    val short = SHORT_ESCAPES[codePoint]
    return if (short != null && random.nextInt(4) != 0) short else hex4(codePoint)
}

fun unicodeEscape(codePoint: Int): String =
    String(Character.toChars(codePoint)).map { hex4(it.code) }.joinToString("")

fun hex4(code: Int): String {
    val hex = code.toString(16).padStart(4, '0')
    return "\\u" + if (random.nextBoolean()) hex else hex.uppercase()
}

val ZERO_LITERALS = listOf("0", "-0", "0.0", "-0.0", "0e0", "0E+2", "0.00e-1", "-0e5")

fun numberLiteral(value: BigDecimal, variety: Double): String {
    val plain = plainLiteral(value)
    if (!chance(variety)) return plain
    val alternative = alternativeLiteral(value)
    return if (alternative.length <= 40) alternative else plain
}

fun plainLiteral(value: BigDecimal): String {
    val stripped = value.stripTrailingZeros()
    return if (stripped.signum() == 0) "0" else stripped.toPlainString()
}

fun alternativeLiteral(value: BigDecimal): String {
    val stripped = value.stripTrailingZeros()
    if (stripped.signum() == 0) return ZERO_LITERALS.random(random)
    val sign = if (stripped.signum() < 0) "-" else ""
    val significant = stripped.unscaledValue().abs().toString()
    val padding = listOf(0, 0, 1, 2).random(random)
    val digits = significant + "0".repeat(padding)
    val exponent = -stripped.scale() - padding
    if (random.nextInt(4) == 0) return sign + "0." + digits + exponentPart(exponent + digits.length)
    val point = random.nextInt(1, digits.length + 1)
    val fraction = digits.substring(point)
    return sign + digits.substring(0, point) + (if (fraction.isEmpty()) "" else ".$fraction") +
        exponentPart(exponent + digits.length - point)
}

fun exponentPart(exponent: Int): String {
    if (exponent == 0 && chance(0.6)) return ""
    val letter = if (random.nextBoolean()) "e" else "E"
    val sign = when {
        exponent < 0 -> "-"
        random.nextBoolean() -> "+"
        else -> ""
    }
    return letter + sign + abs(exponent)
}

fun obj(vararg fields: Pair<String, Json>): JObj = JObj(fields.toList())

fun str(value: String): JStr = JStr(value)

fun num(value: Long): JNum = JNum(BigDecimal.valueOf(value))

fun decimal(text: String): JNum = JNum(BigDecimal(text))

fun numericValue(value: Json): BigDecimal? = when (value) {
    is JNum -> value.value
    is JLit -> BigDecimal(value.literal)
    else -> null
}

fun quoted(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun canonical(value: Json): String = when (value) {
    is JStr -> quoted(value.value)
    is JNum, is JLit -> numericValue(value)!!.stripTrailingZeros().let { if (it.signum() == 0) "0" else it.toString() }
    is JBool -> value.value.toString()
    JNull -> "null"
    is JArr -> value.items.joinToString(",", "[", "]") { canonical(it) }
    is JObj -> value.fields.sortedBy { it.first }.joinToString(",", "{", "}") { quoted(it.first) + ":" + canonical(it.second) }
}

fun canonicalKey(tool: String, value: Json): String = tool + "\u0000" + canonical(value)

fun wordsOf(text: String): List<String> = text.trim().split(Regex("\\s+"))

val KOREAN_WORDS = wordsOf(
    """
    날씨 재고 가격 주문 배송 결제 검색 문서 회의록 요약 저장 메모 일정 알림 상품 쿠폰 리뷰 사진 지도 위치
    서울 부산 대구 제주 오늘 내일 다시 확인 정리 보고 찾기 읽기 쓰기 보내기 취소 완료 실패 성공 대기 결과
    """,
)
val ENGLISH_WORDS = wordsOf(
    "weather stock price order search memo note sync fetch read write ok done error retry later page item cart user",
)
val SPECIAL_PIECES = listOf(
    "\"인용\"", "a\\b", "a/b/c", "탭\t뒤", "줄\n바꿈", "😀", "é", "\u0001", "\u001f", "\u007f", "\u2028", "ＡＢＣ",
    "<html>", "{\"k\":1}", "100%", "#태그", "\b", "\u000c", "ß", "中文", "🙂🙃", "\r\n", "\u0085", "\\u0041",
)
val WORD_SAMPLER = zipf(KOREAN_WORDS + ENGLISH_WORDS, offset = 2.0)
val CITIES = zipf(wordsOf("서울 부산 대구 인천 광주 대전 울산 세종 제주 춘천 강릉 전주 목포 여수 Seoul Tokyo Paris"))

fun text(maxWords: Int = 5): String {
    val words = List(random.nextInt(1, maxWords + 1)) { if (chance(0.06)) SPECIAL_PIECES.random(random) else WORD_SAMPLER.sample() }
    val joined = words.joinToString(if (chance(0.1)) "" else " ")
    val decorated = when (random.nextInt(20)) {
        0 -> " $joined"
        1 -> "$joined "
        2 -> "$joined."
        else -> joined
    }
    return if (decorated.isBlankText()) "$decorated 끝" else decorated
}

fun String.isBlankText(): Boolean = all { it == ' ' || it == '\t' || it == '\n' || it == '\r' }

val BLANK_TEXTS = listOf("", " ", "  ", "\t", "\n", " \r\n ", "\t\t")

fun smallInt(limit: Int = 9): Long = (skewedIndex(limit) + 1).toLong()

fun anyScalar(): Json = when (random.nextInt(10)) {
    0 -> JNull
    1 -> JBool(random.nextBoolean())
    2 -> decimal("${random.nextInt(-50, 50)}.${random.nextInt(1, 10)}")
    3, 4 -> num(random.nextLong(-20, 200))
    5 -> obj("k" to num(smallInt()))
    6 -> JArr(List(random.nextInt(0, 3)) { num(smallInt()) })
    else -> str(text(2))
}

class Schema(
    val type: String? = null,
    val description: String? = null,
    val enum: List<Json>? = null,
    val minimum: String? = null,
    val maximum: String? = null,
    val required: List<String>? = null,
    val properties: List<Pair<String, Schema>>? = null,
    val items: Schema? = null,
) {
    val enumKeys: Set<String>? = enum?.mapTo(HashSet()) { canonical(it) }
}

fun Schema.toJson(): JObj {
    val keywords = listOfNotNull(
        type?.let { "type" to str(it) },
        description?.let { "description" to str(it) },
        properties?.let { list -> "properties" to JObj(list.map { (name, property) -> name to property.toJson() }) },
        required?.let { names -> "required" to JArr(names.map(::str)) },
        enum?.let { "enum" to JArr(it) },
        minimum?.let { "minimum" to JLit(it) },
        maximum?.let { "maximum" to JLit(it) },
        items?.let { "items" to it.toJson() },
    )
    return JObj(if (chance(0.5)) keywords.shuffled(random) else keywords)
}

fun typeMatches(type: String, value: Json): Boolean = when (type) {
    "object" -> value is JObj
    "array" -> value is JArr
    "string" -> value is JStr
    "number" -> value is JNum || value is JLit
    "integer" -> numericValue(value)?.let { it.stripTrailingZeros().scale() <= 0 } ?: false
    "boolean" -> value is JBool
    "null" -> value == JNull
    else -> error("모르는 type: $type")
}

fun collectErrors(schema: Schema, value: Json, path: String, errors: MutableList<String>) {
    if (schema.type != null && !typeMatches(schema.type, value)) {
        errors += "$path expected ${schema.type}"
        return
    }
    if (schema.enumKeys != null && canonical(value) !in schema.enumKeys) errors += "$path not in enum"
    val number = numericValue(value)
    if (number != null) {
        if (schema.minimum != null && number < BigDecimal(schema.minimum)) errors += "$path below minimum ${schema.minimum}"
        if (schema.maximum != null && number > BigDecimal(schema.maximum)) errors += "$path above maximum ${schema.maximum}"
    }
    if (value is JObj) {
        schema.required?.forEach { name -> if (value.field(name) == null) errors += "$path/$name required" }
        schema.properties?.forEach { (name, property) -> value.field(name)?.let { collectErrors(property, it, "$path/$name", errors) } }
    }
    if (value is JArr && schema.items != null) {
        value.items.forEachIndexed { index, item -> collectErrors(schema.items, item, "$path/$index", errors) }
    }
}

fun errorsOf(schema: Schema, value: Json): List<String> = mutableListOf<String>().also { collectErrors(schema, value, "", it) }

fun numberIn(schema: Schema, integer: Boolean): BigDecimal {
    val low = schema.minimum?.let(::BigDecimal) ?: BigDecimal(-20)
    val high = schema.maximum?.let(::BigDecimal) ?: (low + BigDecimal(200))
    if (low > high) return low
    return when (random.nextInt(6)) {
        0 -> if (integer) low.setScale(0, RoundingMode.CEILING) else low
        1 -> if (integer) high.setScale(0, RoundingMode.FLOOR) else high
        else -> {
            val span = (high - low).toDouble()
            val raw = low + BigDecimal(random.nextDouble() * span).setScale(if (integer) 0 else 2, RoundingMode.DOWN)
            if (integer) raw.setScale(0, RoundingMode.CEILING).min(high.setScale(0, RoundingMode.FLOOR)) else raw.min(high)
        }
    }
}

fun pickElement(elements: List<Json>): Json {
    if (elements.size < 1_000) return elements.random(random)
    val position = (1 - random.nextDouble().let { it * it }) * elements.size
    return elements[position.toInt().coerceAtMost(elements.size - 1)]
}

fun validValue(schema: Schema, depth: Int = 0): Json {
    val enum = schema.enum
    if (enum != null) {
        repeat(8) {
            val candidate = pickElement(enum)
            if (errorsOf(schema, candidate).isEmpty()) return candidate
        }
        return pickElement(enum)
    }
    return when (schema.type) {
        "object" -> validObject(schema, depth)
        "array" -> JArr(List(random.nextInt(0, 4)) { schema.items?.let { validValue(it, depth + 1) } ?: anyScalar() })
        "string" -> str(text(2))
        "integer" -> JNum(numberIn(schema, integer = true))
        "number" -> JNum(numberIn(schema, integer = false))
        "boolean" -> JBool(random.nextBoolean())
        "null" -> JNull
        else -> if (schema.minimum != null || schema.maximum != null) JNum(numberIn(schema, integer = false)) else anyScalar()
    }
}

fun validObject(schema: Schema, depth: Int): JObj {
    val properties = schema.properties.orEmpty()
    val required = schema.required.orEmpty()
    val fields = mutableListOf<Pair<String, Json>>()
    properties.forEach { (name, property) ->
        if (name in required || (depth < 4 && chance(if (largeScale) 0.3 else 0.55))) fields += name to validValue(property, depth + 1)
    }
    required.filter { name -> properties.none { it.first == name } }.forEach { fields += it to anyScalar() }
    return JObj(fields)
}

fun wrongTypeValue(type: String): Json = when (type) {
    "object" -> listOf(JArr(emptyList()), str("{}"), JNull).random(random)
    "array" -> listOf(obj(), str("[]"), num(1)).random(random)
    "string" -> listOf(num(smallInt()), JNull, JBool(true), JArr(listOf(str("a")))).random(random)
    "integer" -> listOf(decimal("2.5"), str("3"), decimal("-0.5"), JBool(false)).random(random)
    "number" -> listOf(str("3"), JNull, JArr(emptyList())).random(random)
    "boolean" -> listOf(num(0), str("true"), JNull).random(random)
    else -> listOf(num(0), str("null"), JBool(false)).random(random)
}

fun brokenValue(schema: Schema, value: Json, depth: Int): Json {
    val breakers = mutableListOf<() -> Json>()
    schema.type?.let { type -> breakers += { wrongTypeValue(type) } }
    if (schema.enum != null) breakers += { if (chance(0.3)) anyScalar() else outsideEnum(value) }
    schema.minimum?.let { minimum -> breakers += { JNum(BigDecimal(minimum) - listOf(BigDecimal.ONE, BigDecimal("0.5"), BigDecimal("0.01")).random(random)) } }
    schema.maximum?.let { maximum -> breakers += { JNum(BigDecimal(maximum) + listOf(BigDecimal.ONE, BigDecimal("0.5")).random(random)) } }
    if (value is JObj && depth < 4) breakers += { brokenObject(schema, value, depth) }
    if (value is JArr && schema.items != null) {
        breakers += {
            val padded = value.items + List(random.nextInt(0, 6)) { validValue(schema.items, depth + 1) }
            val position = random.nextInt(padded.size + 1)
            val broken = brokenValue(schema.items, validValue(schema.items, depth + 1), depth + 1)
            JArr(padded.take(position) + broken + padded.drop(position))
        }
    }
    if (breakers.isEmpty()) return value
    return breakers.random(random).invoke()
}

fun outsideEnum(value: Json): Json = when (value) {
    is JObj -> if (value.fields.isEmpty()) obj("z" to num(1)) else JObj(listOf(value.fields[0].first to outsideEnum(value.fields[0].second)) + value.fields.drop(1))
    is JNum -> JNum(value.value + BigDecimal.ONE)
    is JStr -> JStr(value.value + "~")
    is JArr -> JArr(value.items + num(9))
    is JBool -> JBool(!value.value)
    JNull, is JLit -> str("none")
}

fun brokenObject(schema: Schema, value: JObj, depth: Int): JObj {
    val present = schema.required.orEmpty().filter { value.field(it) != null }
    if (present.isNotEmpty() && chance(0.4)) {
        val dropped = present.random(random)
        return JObj(value.fields.filter { it.first != dropped })
    }
    val candidates = schema.properties.orEmpty().filter { (name, _) -> value.field(name) != null }
    if (candidates.isEmpty()) {
        val property = schema.properties.orEmpty().randomOrNull(random) ?: return value
        return JObj(value.fields + (property.first to wrongTypeValue(property.second.type ?: "string")))
    }
    val (name, property) = candidates.random(random)
    return JObj(value.fields.map { if (it.first == name) name to brokenValue(property, it.second, depth + 1) else it })
}

fun invalidInput(schema: Schema, base: JObj): JObj {
    var current = base
    repeat(listOf(1, 1, 1, 2, 3).random(random)) {
        current = brokenObject(schema, current, 0)
    }
    return current
}

val MALFORMED_SCHEMAS: List<String> = listOf(
    "{\"type\":\"array\"}",
    "{\"properties\":{\"x\":{\"type\":\"number\"}}}",
    "{}",
    "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"float\"}}}",
    "{\"type\":\"object\",\"minimum\":0}",
    "{\"type\":\"object\",\"maximum\":10}",
    "{\"type\":\"object\",\"enum\":[{}]}",
    "{\"type\":\"object\",\"items\":{\"type\":\"string\"}}",
    "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"number\",\"exclusiveMinimum\":0}}}",
    "{\"type\":\"object\",\"additionalProperties\":false}",
    "{\"type\":\"object\",\"title\":\"제목\"}",
    "{\"type\":\"object\",\"description\":1}",
    "{\"type\":\"object\",\"properties\":[]}",
    "{\"type\":\"object\",\"properties\":{\"x\":true}}",
    "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":[\"string\",\"null\"]}}}",
    "{\"type\":\"object\",\"required\":\"x\"}",
    "{\"type\":\"object\",\"required\":[1]}",
    "{\"type\":\"object\",\"required\":[\"x\",\"x\"]}",
    "{\"type\":\"object\",\"properties\":{\"x\":{\"enum\":[]}}}",
    "{\"type\":\"object\",\"properties\":{\"x\":{\"enum\":\"a\"}}}",
    "{\"type\":\"object\",\"properties\":{\"x\":{\"minimum\":\"1\"}}}",
    "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"array\",\"items\":[{\"type\":\"string\"}]}}}",
    "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"array\",\"items\":{\"type\":\"strin\"}}}}",
    "{\"type\":\"Object\"}",
    "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"object\",\"properties\":{\"b\":{\"properties\":{\"c\":{\"maximum\":null}}}}}}}",
    "{\"type\":\"object\",\"properties\":{\"x\":{\"description\":{\"text\":\"설명\"}}}}",
    "{\"type\":null}",
    "{\"type\":\"object\",\"\$schema\":\"https://json-schema.org/draft/2020-12/schema\"}",
)

sealed interface ToolState {
    data class Plain(val sideEffect: Boolean) : ToolState

    data class Checked(val schema: Schema, val sideEffect: Boolean) : ToolState

    data object Malformed : ToolState
}

class ToolKind(val name: String, val schema: Schema?, val sideEffect: Boolean = false, val makePlainInput: () -> JObj = { obj() }) {
    fun freshValidInput(): JObj = if (schema == null) makePlainInput() else validValue(schema) as JObj
}

fun sku(index: Int): String = "A$index"

val PLAIN_KINDS = listOf(
    ToolKind("stock", null) { obj("sku" to str(sku(skewedIndex(3000))), "wh" to num(smallInt())) },
    ToolKind("weather", null) { obj("city" to str(CITIES.sample())) },
    ToolKind("order", null) { obj("id" to num(smallInt(1_000_000))) },
    ToolKind("memo", null) { obj("note" to str(text(4))) },
    ToolKind("now", null) { obj() },
    ToolKind("pay", null, sideEffect = true) { obj("order" to num(smallInt(500))) },
    ToolKind("send_mail", null, sideEffect = true) { obj("to" to str(WORD_SAMPLER.sample() + "@example.com")) },
)

val SCHEMA_KINDS = listOf(
    ToolKind(
        "book",
        Schema(
            type = "object",
            description = "숙소를 예약한다",
            properties = listOf(
                "city" to Schema(type = "string", enum = listOf(str("서울"), str("부산"), str("제주"))),
                "nights" to Schema(type = "integer", minimum = "1", maximum = "14"),
                "guests" to Schema(
                    type = "array",
                    items = Schema(type = "object", properties = listOf("age" to Schema(type = "integer", minimum = "0")), required = listOf("age")),
                ),
            ),
            required = listOf("city", "nights"),
        ),
    ),
    ToolKind(
        "tag",
        Schema(
            type = "object",
            properties = listOf(
                "label" to Schema(minimum = "3", enum = listOf(str("a"), num(1), obj("k" to JArr(listOf(num(1), num(2)))), num(7))),
                "count" to Schema(type = "number", maximum = "10"),
                "ids" to Schema(type = "array", items = Schema(type = "integer", enum = listOf(num(1), num(2), num(3)))),
                "flag" to Schema(type = "boolean"),
            ),
        ),
    ),
    ToolKind(
        "fetch",
        Schema(type = "object", properties = listOf("url" to Schema(type = "string", description = "가져올 주소")), required = listOf("url")),
    ),
    ToolKind(
        "transfer",
        Schema(
            type = "object",
            description = "돈을 보낸다",
            properties = listOf(
                "from" to Schema(type = "string"),
                "to" to Schema(type = "string"),
                "amount" to Schema(type = "number", minimum = "0.01", maximum = "1e6"),
                "currency" to Schema(enum = listOf(str("KRW"), str("USD"))),
                "note" to Schema(type = "null"),
            ),
            required = listOf("from", "to", "amount"),
        ),
        sideEffect = true,
    ),
    ToolKind(
        "filter",
        Schema(
            type = "object",
            properties = listOf(
                "range" to Schema(
                    type = "object",
                    properties = listOf("lo" to Schema(type = "number"), "hi" to Schema(type = "number")),
                    required = listOf("lo", "hi"),
                ),
                "tags" to Schema(type = "array", items = Schema(type = "string", enum = listOf(str("new"), str("sale"), str("한정")))),
                "mode" to Schema(enum = listOf(JNull, str("all"), num(1), JBool(true), obj("k" to JArr(listOf(num(1)))), JArr(listOf(num(1), num(2))))),
                "limit" to Schema(type = "integer", minimum = "-0", maximum = "1E2"),
            ),
        ),
    ),
    ToolKind(
        "matrix",
        Schema(
            type = "object",
            properties = listOf(
                "rows" to Schema(type = "array", items = Schema(type = "array", items = Schema(type = "integer", minimum = "-5", maximum = "5"))),
            ),
            required = listOf("rows"),
        ),
    ),
    ToolKind("open", Schema(type = "object")),
    ToolKind("token", Schema(type = "object", properties = emptyList(), required = listOf("token", "session"))),
    ToolKind(
        "odd",
        Schema(
            type = "object",
            properties = listOf(
                "n" to Schema(type = "number", minimum = "5", maximum = "1"),
                "" to Schema(type = "string"),
                "a/b" to Schema(type = "integer", description = "이름에 / 가 있다"),
            ),
            required = emptyList(),
        ),
    ),
)

fun catalogSchema(index: Int, size: Int): Schema {
    val codes = List(size) { num(1_000L + it * 3L + index) }
    val variants = List(size) { obj("x" to num((it % 97).toLong()), "y" to num(it.toLong())) }
    val choices = List(size) { if (it % 3 == 0) decimal("${it / 10}.${it % 10 + 1}") else str("S$it") }
    return Schema(
        type = "object",
        description = "카탈로그 $index",
        properties = listOf(
            "a" to Schema(type = "integer", enum = codes),
            "b" to Schema(type = "object", enum = variants),
            "c" to Schema(enum = choices),
            "n" to Schema(type = "number", minimum = "0", maximum = "100"),
            "l" to Schema(type = "array", items = Schema(type = "integer", minimum = "-5", maximum = "5")),
        ),
        required = listOf("a", "b", "c"),
    )
}

val CATALOG_KINDS = List(options.catalogs) { ToolKind("k$it", catalogSchema(it, options.enumSize)) }
val ACTIVE_SCHEMA_KINDS = if (largeScale) emptyList() else SCHEMA_KINDS
val ALL_KINDS = PLAIN_KINDS + ACTIVE_SCHEMA_KINDS + CATALOG_KINDS
val KIND_SAMPLER = zipf(PLAIN_KINDS.take(2) + ACTIVE_SCHEMA_KINDS + PLAIN_KINDS.drop(2) + CATALOG_KINDS, offset = 1.5)
val UNDEFINED_TOOLS = listOf("ship", "mail", "Book", "FETCH", "translate", "x", "fetch2")

fun pickKind(): ToolKind = if (largeScale && chance(0.9)) CATALOG_KINDS.random(random) else KIND_SAMPLER.sample()

fun runnableKind(): ToolKind {
    repeat(10) {
        val kind = pickKind()
        if (fakeInputsByTool.getValue(kind.name).isNotEmpty()) return kind
    }
    return pickKind()
}

sealed interface FakeOutcome {
    data class Output(val text: String) : FakeOutcome

    data class Failure(val message: String) : FakeOutcome
}

val fakeInputsByTool = ALL_KINDS.associate { it.name to mutableListOf<JObj>() }

class Attempt(val outcome: FakeOutcome, val ms: Long?)

val fakeAttempts = HashMap<String, List<Attempt>>()

val FAKE_OUTPUTS = listOf("맑음\n최고 24도", "12", "15000", "", "예약 번호 51", "{\"ok\":true}", "없음", "3건", "<html>b</html>", "a\\b", "\"인용\"")
val FAKE_ERRORS = listOf("connection refused: retry after 30 seconds", "timeout", "500 Internal Server Error", "권한이 없다", "", "rate limited\n잠시 뒤 다시")
val DURATIONS = WeightedSampler(
    listOf(0L, 1L, 10L, 50L, 100L, 250L, 300L, 500L, 1_000L, 5_000L, 1_000_000L, 1_000_000_000L),
    listOf(6.0, 3.0, 5.0, 6.0, 8.0, 5.0, 6.0, 6.0, 4.0, 2.0, 1.0, 1.0),
)

fun randomOutcome(failureRate: Double = 0.15): FakeOutcome = when {
    chance(failureRate) -> FakeOutcome.Failure(FAKE_ERRORS.random(random))
    largeScale -> FakeOutcome.Output(random.nextInt(0, 500).toString())
    chance(0.5) -> FakeOutcome.Output(FAKE_OUTPUTS.random(random))
    else -> FakeOutcome.Output(text(4))
}

fun randomDuration(): Long? = if (chance(0.15)) null else DURATIONS.sample()

fun randomAttempts(): List<Attempt> {
    if (chance(0.65)) return listOf(Attempt(randomOutcome(), randomDuration()))
    return List(random.nextInt(1, 5)) { Attempt(randomOutcome(failureRate = 0.45), randomDuration()) }
}

fun attemptFields(attempt: Attempt): List<Pair<String, Json>> {
    val result = when (val outcome = attempt.outcome) {
        is FakeOutcome.Output -> "output" to str(outcome.text)
        is FakeOutcome.Failure -> "error" to str(outcome.message)
    }
    return listOf(result) + listOfNotNull(attempt.ms?.let { "ms" to num(it) })
}

fun emitFake(tool: String, input: JObj, attempts: List<Attempt>, style: JsonStyle) {
    val result = if (attempts.size == 1 && chance(0.7)) {
        attemptFields(attempts.single())
    } else {
        listOf("attempts" to JArr(attempts.map { JObj(attemptFields(it)) }))
    }
    emit("fake " + JObj(listOf("tool" to str(tool), "input" to input) + result).render(style))
    fakeAttempts[canonicalKey(tool, input)] = attempts
}

fun addFake(kind: ToolKind) {
    repeat(10) {
        val input = if (kind.schema != null && chance(0.05)) invalidInput(kind.schema, kind.freshValidInput()) else kind.freshValidInput()
        if (canonicalKey(kind.name, input) !in fakeAttempts) {
            fakeInputsByTool.getValue(kind.name) += input
            emitFake(kind.name, input, randomAttempts(), if (largeScale) COMPACT else commandStyle())
            return
        }
    }
}

fun replaceFake() {
    val kind = KIND_SAMPLER.sample()
    val inputs = fakeInputsByTool.getValue(kind.name)
    if (inputs.isEmpty()) return
    emitFake(kind.name, inputs[skewedIndex(inputs.size)], randomAttempts(), VARIANT)
}

fun nearMissPlain(value: JObj): JObj {
    if (value.fields.isEmpty()) return obj("memo_id" to num(1))
    val index = random.nextInt(value.fields.size)
    val (name, item) = value.fields[index]
    val mutated: Json = when (item) {
        is JNum -> if (chance(0.5)) JStr(plainLiteral(item.value)) else JNum(item.value + BigDecimal.ONE)
        is JStr -> JStr(item.value + "x")
        else -> num(0)
    }
    return JObj(value.fields.mapIndexed { position, field -> if (position == index) name to mutated else field })
}

val CATALOG_ENUM_FIELDS = setOf("a", "b", "c")

fun catalogMiss(input: JObj): JObj =
    JObj(input.fields.map { (name, value) -> if (name in CATALOG_ENUM_FIELDS && chance(0.4)) name to outsideEnum(value) else name to value })

fun toolUseInput(kind: ToolKind): JObj {
    val inputs = fakeInputsByTool.getValue(kind.name)
    val schema = kind.schema
    val catalogInLargeTest = largeScale && kind in CATALOG_KINDS
    val missShare = if (catalogInLargeTest) 0.2 else 0.0
    val knownShare = if (catalogInLargeTest) 0.45 else 0.5
    val freshShare = if (catalogInLargeTest) 0.15 else 0.2
    fun knownOrFresh(): JObj = if (inputs.isNotEmpty() && chance(0.5)) inputs[skewedIndex(inputs.size)] else kind.freshValidInput()
    val roll = random.nextDouble()
    return when {
        roll < missShare -> catalogMiss(knownOrFresh())
        inputs.isNotEmpty() && roll < missShare + knownShare -> inputs[skewedIndex(inputs.size)]
        roll < missShare + knownShare + freshShare -> kind.freshValidInput()
        schema == null -> if (inputs.isNotEmpty()) nearMissPlain(inputs[skewedIndex(inputs.size)]) else kind.freshValidInput()
        else -> invalidInput(schema, knownOrFresh())
    }
}

var idSerial = 0

fun freshId(): String {
    idSerial++
    val base = idSerial.toString(36)
    return when (random.nextInt(12)) {
        0 -> "toolu_$base"
        1 -> "call-$base"
        2 -> "T$base"
        else -> if (chance(0.02)) "id_${base}_".padEnd(64, 'z') else "t$base"
    }
}

sealed interface PlannedBlock

data class TextPart(val text: String) : PlannedBlock

data class ToolPart(val id: String, val name: String, val input: JObj, val explicitComplete: Boolean) : PlannedBlock

data class CutPart(val id: String, val name: String) : PlannedBlock

class SimMessage(val fromUser: Boolean, val toolIds: List<String>, var hasBlankText: Boolean)

class SimReply(val stop: String, val toolUses: List<ToolPart>, val cutInsideToolUse: Boolean)

class SimResult(val isError: Boolean, val content: String)

class Simulator {
    var maxTurns = 10
    var replyLimit = 1_000
    var replyLimitCap = 4_000
    var repeatLimit = 0
    var timeoutMs = 0L
    var retries = 0
    var invalidToolName = false
    val tools = HashMap<String, ToolState>()
    val script = ArrayDeque<SimReply>()
    val history = mutableListOf<SimMessage>()

    fun toolIdsInHistory(): List<String> = history.flatMap { it.toolIds }

    fun worstCaseGrowth(): Int {
        var growth = 1
        var turn = 1
        for (reply in script) {
            if (turn > maxTurns) break
            when {
                reply.stop == "tool" -> growth += 2
                reply.cutInsideToolUse -> Unit
                else -> return growth + 1
            }
            turn++
        }
        return growth
    }

    fun resultOf(use: ToolPart): SimResult {
        val state = tools[use.name] ?: return SimResult(true, "unknown tool: ${use.name}")
        val sideEffect = when (state) {
            is ToolState.Plain -> state.sideEffect
            is ToolState.Checked -> state.sideEffect
            ToolState.Malformed -> error("스키마 형식이 틀린 도구가 있으면 요청이 거절된다")
        }
        if (state is ToolState.Checked) {
            val errors = errorsOf(state.schema, use.input)
            if (errors.isNotEmpty()) return SimResult(true, "invalid input: " + errors.joinToString("; "))
        }
        val attempts = fakeAttempts[canonicalKey(use.name, use.input)] ?: return SimResult(true, "no result for this input")
        var count = 0
        while (true) {
            val attempt = attempts[minOf(count, attempts.lastIndex)]
            count++
            val timedOut = timeoutMs > 0 && (attempt.ms ?: 0) > timeoutMs
            val outcome = attempt.outcome
            val result = when {
                timedOut -> SimResult(true, "timeout after $timeoutMs ms" + if (sideEffect) "; outcome unknown" else "")
                outcome is FakeOutcome.Output -> SimResult(false, outcome.text)
                else -> SimResult(true, (outcome as FakeOutcome.Failure).message)
            }
            if (!result.isError || sideEffect || count > retries) return result
        }
    }

    fun ask(blank: Boolean): String {
        val last = history.lastOrNull()
        if (last != null && last.fromUser) last.hasBlankText = last.hasBlankText || blank else history += SimMessage(true, emptyList(), blank)
        var turn = 1
        var limit = replyLimit
        val errorCounts = HashMap<String, Int>()
        while (true) {
            val ids = toolIdsInHistory()
            val rejection = when {
                invalidToolName -> "tool_name"
                tools.values.any { it == ToolState.Malformed } -> "tool_schema"
                history.any { it.hasBlankText } -> "empty_content"
                ids.size != ids.toSet().size -> "duplicate_id"
                script.isEmpty() -> "script_exhausted"
                else -> null
            }
            if (rejection != null) {
                history.clear()
                return "rejected $rejection"
            }
            val reply = script.removeFirst()
            val replyIds = reply.toolUses.map { it.id }
            when {
                reply.stop == "end" -> {
                    history += SimMessage(false, replyIds, false)
                    return "end"
                }
                reply.stop == "tool" -> {
                    history += SimMessage(false, replyIds, false)
                    if (turn == maxTurns) return "max_turns"
                    history += SimMessage(true, emptyList(), false)
                    for (use in reply.toolUses) {
                        val result = resultOf(use)
                        if (result.isError) errorCounts.merge(use.name + "\u0000" + result.content, 1, Int::plus)
                    }
                    if (repeatLimit >= 1 && errorCounts.values.any { it >= repeatLimit }) return "repeated_error"
                }
                reply.cutInsideToolUse -> {
                    if (limit == replyLimitCap) return "length"
                    if (turn == maxTurns) return "max_turns"
                    limit = minOf(limit * 2, replyLimitCap)
                }
                else -> {
                    history += SimMessage(false, replyIds, false)
                    return "length"
                }
            }
            turn++
        }
    }
}

val simulator = Simulator()

fun blockJson(block: PlannedBlock): JObj = when (block) {
    is TextPart -> JObj(listOf("type" to str("text"), "text" to str(block.text)))
    is ToolPart -> {
        val fields = listOf("type" to str("tool"), "id" to str(block.id), "name" to str(block.name), "input" to block.input)
        JObj(if (block.explicitComplete) fields + ("incomplete" to JBool(false)) else fields)
    }
    is CutPart -> JObj(listOf("type" to str("tool"), "id" to str(block.id), "name" to str(block.name), "incomplete" to JBool(true)))
}

fun replyStyle(): JsonStyle = if (chance(if (largeScale) 0.45 else 0.15)) VARIANT else commandStyle()

fun commandLine(name: String, argument: String): String = when (random.nextInt(25)) {
    0 -> "  $name $argument"
    1 -> "$name    $argument  "
    else -> "$name $argument"
}

fun emitReply(stop: String, blocks: List<PlannedBlock>) {
    val reply = JObj(listOf("stop" to str(stop), "content" to JArr(blocks.map(::blockJson))))
    emit(commandLine("reply", reply.render(replyStyle())))
    simulator.script.addLast(SimReply(stop, blocks.filterIsInstance<ToolPart>(), cutInsideToolUse = blocks.last() is CutPart))
}

fun toolPart(id: String = freshId()): ToolPart {
    if (chance(0.06)) return ToolPart(id, UNDEFINED_TOOLS.random(random), pickKind().freshValidInput(), chance(0.1))
    val kind = pickKind()
    return ToolPart(id, kind.name, toolUseInput(kind), chance(0.1))
}

fun toolUseCount(): Int = random.nextInt(1, options.uses + 1)

fun toolReplyBlocks(tools: List<ToolPart> = List(toolUseCount()) { toolPart() }): List<PlannedBlock> {
    val blocks = mutableListOf<PlannedBlock>()
    if (chance(if (largeScale) 0.15 else 0.4)) blocks += TextPart(text())
    tools.forEach { tool ->
        blocks += tool
        if (chance(0.08)) blocks += TextPart(text(2))
    }
    if (chance(0.1)) blocks += TextPart(text(2))
    return blocks
}

fun answerBlocks(): List<PlannedBlock> = List(listOf(1, 1, 1, 2, 3).random(random)) { TextPart(text()) }

fun emitToolReply() = emitReply("tool", toolReplyBlocks())

fun emitAnswer() = emitReply("end", answerBlocks())

fun emitCompleteLength() {
    val blocks = when (random.nextInt(3)) {
        0 -> answerBlocks()
        1 -> toolReplyBlocks()
        else -> toolReplyBlocks() + TextPart(text())
    }
    emitReply("length", blocks)
}

fun emitCutReply() {
    val prefix = when (random.nextInt(4)) {
        0 -> emptyList()
        1 -> listOf(TextPart(text()))
        2 -> toolReplyBlocks(List(listOf(1, 2).random(random)) { toolPart() })
        else -> listOf(TextPart(text()), toolPart())
    }
    emitReply("length", prefix + CutPart(freshId(), pickKind().name))
}

var asksEmitted = 0

fun emitAsk(blank: Boolean = false) {
    if (simulator.history.size + simulator.worstCaseGrowth() > 196) emitReset()
    asksEmitted++
    val askText = if (blank) BLANK_TEXTS.random(random) else text(6)
    emit(commandLine("ask", obj("text" to str(askText)).render(commandStyle())))
    simulator.ask(blank)
    check(simulator.history.size <= 200) { "대화 기록이 200개를 넘었다" }
}

fun emitReset() {
    emit(
        when (random.nextInt(4)) {
            0 -> "reset {}"
            1 -> "reset   { }"
            else -> "reset"
        },
    )
    simulator.history.clear()
}

fun emitToolDefinition(kind: ToolKind, withSchema: Boolean = kind.schema != null, sideEffect: Boolean = kind.sideEffect) {
    val description = if (chance(0.4)) listOf("description" to str(text(3))) else emptyList()
    val sideEffectField = if (sideEffect || chance(0.2)) listOf("side_effect" to JBool(sideEffect)) else emptyList()
    val schema = kind.schema
    if (withSchema && schema != null) {
        val style = if (kind in CATALOG_KINDS && largeScale) COMPACT else schemaStyle()
        val fields = listOf("name" to str(kind.name)) + description + sideEffectField + ("schema" to schema.toJson())
        emit("tool " + JObj(fields).render(style))
        simulator.tools[kind.name] = ToolState.Checked(schema, sideEffect)
    } else {
        emit("tool " + JObj(listOf("name" to str(kind.name)) + sideEffectField + description).render(commandStyle()))
        simulator.tools[kind.name] = ToolState.Plain(sideEffect)
    }
}

fun emitMalformedDefinition(name: String) {
    emit("tool {\"name\":" + str(name).render() + ",\"schema\":" + MALFORMED_SCHEMAS.random(random) + "}")
    simulator.tools[name] = ToolState.Malformed
}

fun emitConfig(
    maxTurns: Int? = null,
    replyLimit: Int? = null,
    replyLimitCap: Int? = null,
    repeatLimit: Int? = null,
    concurrency: Int? = null,
    timeoutMs: Long? = null,
    retries: Int? = null,
    backoffMs: Long? = null,
    trace: Boolean? = null,
) {
    val fields = listOfNotNull(
        maxTurns?.let { "max_turns" to num(it.toLong()) },
        replyLimit?.let { "reply_limit" to num(it.toLong()) },
        replyLimitCap?.let { "reply_limit_cap" to num(it.toLong()) },
        repeatLimit?.let { "repeat_limit" to num(it.toLong()) },
        concurrency?.let { "concurrency" to num(it.toLong()) },
        timeoutMs?.let { "timeout_ms" to num(it) },
        retries?.let { "retries" to num(it.toLong()) },
        backoffMs?.let { "backoff_ms" to num(it) },
        trace?.let { "trace" to JBool(it) },
    )
    emit(commandLine("config", JObj(if (chance(0.3)) fields.shuffled(random) else fields).render(commandStyle())))
    maxTurns?.let { simulator.maxTurns = it }
    replyLimit?.let { simulator.replyLimit = it }
    replyLimitCap?.let { simulator.replyLimitCap = it }
    repeatLimit?.let { simulator.repeatLimit = it }
    timeoutMs?.let { simulator.timeoutMs = it }
    retries?.let { simulator.retries = it }
    check(simulator.replyLimit <= simulator.replyLimitCap)
}

val CONCURRENCIES = WeightedSampler(
    listOf(1, 2, 3, 4, 8, 64, 1_000, 100_000, 1_000_000),
    listOf(5.0, 4.0, 3.0, 3.0, 3.0, 2.0, 2.0, 2.0, 3.0),
)
val TIMEOUTS = WeightedSampler(
    listOf(0L, 1L, 50L, 100L, 300L, 500L, 1_000L, 1_000_000L, 1_000_000_000L),
    listOf(6.0, 1.0, 2.0, 3.0, 3.0, 3.0, 2.0, 1.0, 1.0),
)
val RETRIES = WeightedSampler(listOf(0, 1, 2, 3, 10), listOf(4.0, 3.0, 2.0, 1.0, 1.0))
val BACKOFFS = WeightedSampler(
    listOf(0L, 1L, 10L, 50L, 100L, 300L, 1_000L, 1_000_000L, 1_000_000_000L),
    listOf(5.0, 1.0, 3.0, 3.0, 3.0, 2.0, 2.0, 1.0, 1.0),
)

fun emitParallelConfig() {
    emitConfig(
        concurrency = (if (wideRun && chance(0.85)) 1_000_000 else CONCURRENCIES.sample()).takeIf { chance(0.8) },
        timeoutMs = TIMEOUTS.sample().takeIf { chance(0.7) },
        retries = RETRIES.sample().takeIf { chance(0.6) },
        backoffMs = BACKOFFS.sample().takeIf { chance(0.6) },
        trace = (chance(0.75)).takeIf { chance(0.6) },
    )
}

val largeRepeatLimit = 400

fun restingRepeatLimit(): Int = if (largeScale || wideRun) largeRepeatLimit else listOf(0, 0, 1000).random(random)

fun emitRandomConfig() {
    when (random.nextInt(9)) {
        7, 8 -> emitParallelConfig()
        0 -> emitConfig(maxTurns = listOf(1, 2, 3, 5, 10, 10, 1000).random(random))
        1 -> {
            val cap = listOf(1, 1000, 4000, 5000, 1_000_000).random(random)
            val limit = listOf(1, cap / 3, cap / 2, cap, 1000).filter { it in 1..cap }.random(random)
            emitConfig(replyLimit = limit, replyLimitCap = cap)
        }
        2 -> emitConfig(repeatLimit = if (largeScale || wideRun) largeRepeatLimit else listOf(0, 1, 2, 3, 5, 1000).random(random))
        3 -> emit("config {}")
        4 -> emitConfig(maxTurns = 10, replyLimit = 1000, replyLimitCap = 4000, repeatLimit = restingRepeatLimit())
        5 -> emitConfig(maxTurns = listOf(4, 6, 8).random(random), repeatLimit = restingRepeatLimit())
        else -> emitConfig(replyLimitCap = maxOf(simulator.replyLimit, listOf(4000, 7000, 1_000_000).random(random)))
    }
}

val UNKNOWN_COMMANDS = listOf("Ask", "ASK", "TOOL", "Reply", "fake2", "con-fig", "réset", "도구", "ask!", "tool.", "Reset", "1", "-", "ASK_")

fun okText(): JObj = obj("text" to str(text(2)))

fun badLine(): String = when (random.nextInt(60)) {
    0 -> UNKNOWN_COMMANDS.random(random) + " " + okText().render()
    1 -> "ask" + okText().render()
    2 -> "ask\t" + okText().render()
    3 -> "ask\u00a0" + okText().render()
    4 -> UNKNOWN_COMMANDS.random(random)
    5 -> "\t" + "ask " + okText().render()
    6 -> "ask {\"text\":\"${WORD_SAMPLER.sample()}\""
    7 -> "ask {\"text\":\"${WORD_SAMPLER.sample()}\",}"
    8 -> "ask {'text':'안녕'}"
    9 -> "ask " + okText().render() + " {}"
    10 -> "ask {\"text\":\"가\",\"text\":\"나\"}"
    11 -> "ask {\"text\":\"가\",\"\\u0074ext\":\"나\"}"
    12 -> "ask {\"text\":\"\\ud800\"}"
    13 -> "ask {\"text\":\"\\udc00x\"}"
    14 -> "ask {\"text\":\"\\ud800\\u0041\"}"
    15 -> "ask {\"text\":\"탭\t글자\"}"
    16 -> "ask [\"안녕\"]"
    17 -> "ask " + listOf("\"안녕\"", "1", "null", "true", "[]").random(random)
    18 -> "config {\"max_turns\":" + listOf("01", "1.", ".5", "+1", "1e", "NaN", "-", "0x10", "1_0").random(random) + "}"
    19 -> "reset " + listOf("nope", "{", "[]", "{}}", "\"\"").random(random)
    20 -> "ask {\"text\":\"\\x41\"}"
    21 -> "tool {\"name\":\"book\",\"schema\":{\"type\":\"object\",\"type\":\"array\"}}"
    22 -> "fake {\"tool\":\"stock\",\"input\":{\"a\":1,\"a\":2},\"output\":\"x\"}"
    23 -> "tool {\"name\":\"memo\"}x"
    24 -> "tool {\"name\":\"memo\",\"\":1,\"\":2}"
    25 -> "ask"
    26 -> "ask {\"text\":" + listOf("1", "null", "[\"x\"]", "{\"t\":1}", "true").random(random) + "}"
    27 -> "tool {}"
    28 -> "tool {\"name\":" + listOf("1", "null", "[\"x\"]", "true").random(random) + "}"
    29 -> "tool {\"name\":\"memo\",\"description\":" + listOf("1", "null", "[\"설명\"]", "{}").random(random) + "}"
    30 -> "fake " + listOf(
        "{\"input\":{},\"output\":\"x\"}",
        "{\"tool\":\"now\",\"output\":\"x\"}",
        "{\"tool\":\"now\",\"input\":{}}",
        "{\"tool\":1,\"input\":{},\"output\":\"x\"}",
        "{\"tool\":\"now\",\"input\":[],\"output\":\"x\"}",
        "{\"tool\":\"now\",\"input\":null,\"output\":\"x\"}",
        "{\"tool\":\"now\",\"input\":{},\"output\":12}",
    ).random(random)
    31 -> "reply " + listOf(
        "{\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}",
        "{\"stop\":\"end\"}",
        "{\"stop\":\"END\",\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}",
        "{\"stop\":1,\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}",
        "{\"stop\":\"end\",\"content\":{}}",
        "{\"stop\":\"end\",\"content\":[]}",
        "{\"stop\":\"end\",\"content\":[1]}",
        "{\"stop\":\"end\",\"content\":[{}]}",
        "{\"stop\":\"end\",\"content\":[{\"type\":\"image\"}]}",
    ).random(random)
    32 -> "reply {\"stop\":\"end\",\"content\":[{\"type\":\"text\"" + listOf("", ",\"text\":\"\"", ",\"text\":\" \\t\\n\"", ",\"text\":1").random(random) + "}]}"
    33 -> "reply {\"stop\":\"tool\",\"content\":[" + badToolBlock() + "]}"
    34 -> "reply {\"stop\":\"length\",\"content\":[{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":true},{\"type\":\"text\",\"text\":\"계속\"}]}"
    35 -> "reply {\"stop\":\"tool\",\"content\":[{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":true}]}"
    36 -> "reply {\"stop\":\"end\",\"content\":[{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"input\":{}}]}"
    37 -> "reply {\"stop\":\"tool\",\"content\":[{\"type\":\"text\",\"text\":\"도구를 쓰겠다.\"}]}"
    38 -> "config {\"max_turns\":" + listOf("0", "1001", "1.5", "\"3\"", "null", "-1", "1e4", "true").random(random) + "}"
    39 -> "config {\"reply_limit\":" + listOf("0", "1000001", "2.5", "\"1000\"", "-5").random(random) + "}"
    40 -> "config {\"reply_limit\":${simulator.replyLimitCap + random.nextInt(1, 1000)}}"
    41 -> "config {\"max_turns\":3,\"reply_limit\":0}"
    42 -> "fake {\"tool\":\"fetch\",\"input\":{\"url\":\"a\"},\"output\":\"A\",\"error\":\"실패\"}"
    43 -> "fake {\"tool\":\"fetch\",\"input\":{\"url\":\"a\"}}"
    44 -> "fake {\"tool\":\"fetch\",\"input\":{\"url\":\"a\"},\"error\":" + listOf("404", "null", "[\"x\"]", "{}", "true").random(random) + "}"
    45 -> "fake {\"tool\":\"fetch\",\"input\":{\"url\":\"a\"},\"output\":null,\"error\":\"실패\"}"
    46 -> "tool {\"name\":\"fetch\",\"schema\":" + listOf("\"object\"", "[]", "null", "1", "true", "[{\"type\":\"object\"}]").random(random) + "}"
    47 -> "config {\"repeat_limit\":" + listOf("-1", "1001", "1.5", "\"2\"", "null", "true", "1e4", "[]").random(random) + "}"
    48 -> "config {\"repeat_limit\":2,\"max_turns\":0}"
    49 -> "config {\"max_turns\":5,\"repeat_limit\":-3}"
    50 -> "tool {\"name\":\"fetch\",\"schema\":{\"type\":\"object\"},\"description\":7}"
    51 -> "fake {\"tool\":\"fetch\",\"input\":\"{}\",\"error\":\"x\"}"
    else -> "ask {\"text\":\"a\"" + listOf("]", ":", "}}", ",\"text\"").random(random) + "}"
}

fun badToolBlock(): String = listOf(
    "{\"type\":\"tool\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"아이디\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"${"a".repeat(65)}\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"메모\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\"}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"input\":[]}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":\"true\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":true,\"input\":{}}",
).random(random)

val FAKE_HEAD = "fake {\"tool\":\"stock\",\"input\":{\"sku\":\"A1\",\"wh\":1},"

fun badParallelLine(): String = when (random.nextInt(17)) {
    0 -> FAKE_HEAD + "\"output\":\"A\",\"attempts\":[{\"output\":\"A\"}]}"
    1 -> FAKE_HEAD + "\"attempts\":[]}"
    2 -> FAKE_HEAD + "\"attempts\":[{\"output\":\"A\",\"error\":\"x\"}]}"
    3 -> FAKE_HEAD + "\"attempts\":[{\"ms\":10}]}"
    4 -> FAKE_HEAD + "\"attempts\":" + listOf("{}", "\"x\"", "[1]", "[null]", "[[]]", "null").random(random) + "}"
    5 -> FAKE_HEAD + "\"attempts\":[{\"output\":\"A\",\"ms\":" + listOf("-1", "1000000001", "2.5", "\"10\"", "null", "1e10").random(random) + "}]}"
    6 -> FAKE_HEAD + "\"output\":\"A\",\"ms\":" + listOf("-1", "1000000001", "2.5", "\"5\"", "null", "true").random(random) + "}"
    7 -> FAKE_HEAD + "\"error\":\"x\",\"attempts\":[{\"output\":\"A\"}]}"
    8 -> FAKE_HEAD + "\"ms\":5,\"attempts\":[{\"output\":\"A\"}]}"
    9 -> FAKE_HEAD + "\"ms\":10}"
    10 -> FAKE_HEAD + "\"attempts\":[{\"output\":\"A\"},{\"error\":7}]}"
    11 -> "tool {\"name\":\"pay\",\"side_effect\":" + listOf("\"yes\"", "1", "null", "\"true\"", "[]").random(random) + "}"
    12 -> "config {\"concurrency\":" + listOf("0", "1000001", "1.5", "\"4\"", "null", "-2").random(random) + "}"
    13 -> "config {\"timeout_ms\":" + listOf("-1", "1000000001", "2.5", "\"1\"", "true").random(random) + "}"
    14 -> "config {\"retries\":" + listOf("-1", "11", "2.5", "\"1\"", "true", "1000000001").random(random) + "}"
    15 -> "config {\"backoff_ms\":" + listOf("-1", "1000000001", "2.5", "\"10\"", "true", "null", "1e10").random(random) + "}"
    else -> listOf(
        "config {\"trace\":" + listOf("1", "\"true\"", "null", "0", "[true]").random(random) + "}",
        "config {\"concurrency\":2,\"retries\":11}",
        "config {\"trace\":true,\"timeout_ms\":-5}",
        "config {\"retries\":3,\"concurrency\":0,\"trace\":true}",
    ).random(random)
}

fun emitNoise() {
    when (random.nextInt(12)) {
        0 -> emit("")
        1 -> emit(" ".repeat(random.nextInt(1, 4)))
        2, 3, 4 -> emit(badParallelLine())
        else -> emit(badLine())
    }
}

fun episodeAnswer() {
    emitAnswer()
    emitAsk()
}

fun episodeToolLoop() {
    repeat(listOf(1, 1, 2, 2, 3, 4).random(random)) { emitToolReply() }
    if (chance(0.8)) emitAnswer() else emitCompleteLength()
    emitAsk()
}

fun episodeMaxTurns() {
    val maxTurns = listOf(1, 2, 2, 3).random(random)
    if (simulator.maxTurns != maxTurns) emitConfig(maxTurns = maxTurns)
    repeat(maxTurns) { emitToolReply() }
    emitAsk()
}

fun episodeCut() {
    val cutReplies = listOf(1, 1, 2, 3).random(random)
    if (chance(0.25)) {
        emitConfig(maxTurns = cutReplies, replyLimit = 1000, replyLimitCap = 1_000_000)
    } else if (chance(0.4)) {
        val cap = listOf(2000, 5000, 6000, 10_000).random(random)
        emitConfig(replyLimit = listOf(1000, 1500, cap / 4, cap).random(random), replyLimitCap = cap)
    }
    repeat(cutReplies) { emitCutReply() }
    when (random.nextInt(3)) {
        0 -> emitToolReply()
        1 -> emitCompleteLength()
        else -> Unit
    }
    emitAnswer()
    emitAsk()
}

fun episodeBlankAsk() {
    if (chance(0.5)) emitAnswer()
    emitAsk(blank = true)
}

fun episodeDuplicateId() {
    val previous = simulator.toolIdsInHistory()
    val reused = if (previous.isNotEmpty() && chance(0.7)) previous.random(random) else null
    val blocks = if (reused != null) {
        toolReplyBlocks(listOf(toolPart(), toolPart(reused)))
    } else {
        val id = freshId()
        listOf(toolPart(id), TextPart(text(2)), toolPart(id))
    }
    emitReply(if (chance(0.7)) "tool" else "length", blocks)
    emitAnswer()
    emitAsk()
}

fun episodeExhausted() {
    if (chance(0.5)) {
        repeat(listOf(1, 2).random(random)) { emitToolReply() }
        if (simulator.maxTurns <= simulator.script.size) emitConfig(maxTurns = 10)
    }
    emitAsk()
}

fun episodeMalformedSchema() {
    val target = if (chance(0.7)) ACTIVE_SCHEMA_KINDS.randomOrNull(random) else null
    val name = target?.name ?: "draft_tool"
    emitMalformedDefinition(name)
    if (chance(0.5)) emitAnswer()
    emitAsk(blank = chance(0.1))
    if (chance(0.3)) emitAsk()
    emitToolDefinition(target ?: ToolKind(name, null), withSchema = chance(0.8))
}

fun unknownToolSource(): () -> ToolPart {
    val name = UNDEFINED_TOOLS.random(random)
    return { ToolPart(freshId(), name, pickKind().freshValidInput(), false) }
}

fun missingUrlSource(): () -> ToolPart = { ToolPart(freshId(), "fetch", obj("link" to str(text(1))), false) }

fun cityOutsideEnumSource(): () -> ToolPart = { ToolPart(freshId(), "book", obj("city" to str("대구"), "nights" to num(smallInt(14))), false) }

fun failingFakeSource(): () -> ToolPart {
    val candidates = ALL_KINDS.flatMap { kind -> fakeInputsByTool.getValue(kind.name).takeLast(30).map { kind.name to it } }
    val failing = candidates.filter { (name, input) -> simulator.resultOf(ToolPart("probe", name, input, false)).isError }
    val (name, input) = failing.randomOrNull(random) ?: return unknownToolSource()
    return { ToolPart(freshId(), name, input, false) }
}

fun missingFakeSource(): () -> ToolPart = { ToolPart(freshId(), "weather", obj("city" to str("도시" + random.nextInt(1000))), false) }

fun episodeRepeat() {
    val limit = listOf(1, 2, 2, 3).random(random)
    if (simulator.repeatLimit != limit) emitConfig(repeatLimit = limit)
    val sources = if (largeScale) {
        listOf(::unknownToolSource, ::failingFakeSource, ::missingFakeSource)
    } else {
        listOf(::unknownToolSource, ::missingUrlSource, ::cityOutsideEnumSource, ::failingFakeSource, ::missingFakeSource)
    }
    val source = sources.random(random)()
    repeat(limit + 1) {
        val tools = List(random.nextInt(0, 3)) { toolPart() } + List(random.nextInt(1, 3)) { source() }
        emitReply("tool", toolReplyBlocks(tools.shuffled(random)))
    }
    emitAnswer()
    emitAsk()
    if (largeScale || wideRun || chance(0.6)) emitConfig(repeatLimit = restingRepeatLimit())
}

fun runnableToolPart(): ToolPart {
    val kind = runnableKind()
    val inputs = fakeInputsByTool.getValue(kind.name)
    if (inputs.isEmpty()) return toolPart()
    return ToolPart(freshId(), kind.name, inputs[skewedIndex(inputs.size)], chance(0.1))
}

fun episodeParallel() {
    emitParallelConfig()
    repeat(listOf(1, 1, 2, 3).random(random)) {
        val tools = List(random.nextInt(2, options.uses + 3)) { if (chance(0.75)) runnableToolPart() else toolPart() }
        emitReply("tool", toolReplyBlocks(tools))
    }
    emitAnswer()
    emitAsk()
}

fun episodeTimeoutRepeat() {
    val timeout = listOf(50L, 100L, 300L).random(random)
    emitConfig(timeoutMs = timeout, retries = listOf(0, 1, 2).random(random), repeatLimit = listOf(1, 2, 3).random(random), trace = chance(0.7))
    val input = obj("city" to str("느린 도시 " + random.nextInt(1000)))
    emitFake("weather", input, listOf(Attempt(FakeOutcome.Failure("busy"), timeout / 2), Attempt(FakeOutcome.Output("맑음"), timeout * 3)), commandStyle())
    fakeInputsByTool.getValue("weather") += input
    repeat(simulator.repeatLimit + 1) {
        val tools = List(random.nextInt(0, 3)) { runnableToolPart() } + ToolPart(freshId(), "weather", input, false)
        emitReply("tool", toolReplyBlocks(tools.shuffled(random)))
    }
    emitAnswer()
    emitAsk()
    emitConfig(repeatLimit = restingRepeatLimit(), timeoutMs = TIMEOUTS.sample().takeIf { chance(0.5) })
}

val FLAKY_DURATIONS = listOf(0L, 50L, 50L, 100L, 150L)

fun flakyToolPart(): ToolPart {
    val sideEffect = chance(0.2)
    val name = if (sideEffect) "pay" else "weather"
    val input = if (sideEffect) {
        obj("order" to num(random.nextLong(1_000, 1_000_000)))
    } else {
        obj("city" to str("붐비는 도시 " + random.nextInt(1_000_000)))
    }
    val attempts = List(random.nextInt(1, 4)) {
        Attempt(if (chance(0.55)) FakeOutcome.Failure("busy") else FakeOutcome.Output("ok"), FLAKY_DURATIONS.random(random))
    }
    emitFake(name, input, attempts, commandStyle())
    fakeInputsByTool.getValue(name) += input
    return ToolPart(freshId(), name, input, false)
}

fun episodeBackoffQueue() {
    emitConfig(
        concurrency = listOf(1, 2, 2, 3).random(random),
        timeoutMs = listOf(0L, 0L, 120L).random(random),
        retries = listOf(1, 2, 3).random(random),
        backoffMs = listOf(10L, 50L, 100L).random(random),
        trace = chance(0.8),
    )
    val tools = List(random.nextInt(3, 8)) { if (chance(0.85)) flakyToolPart() else runnableToolPart() }
    emitReply("tool", toolReplyBlocks(tools))
    emitAnswer()
    emitAsk()
    if (wideRun) emitConfig(concurrency = 1_000_000)
}

fun wideToolPart(): ToolPart {
    val input = obj("city" to str("넓은 도시 " + random.nextInt(1_000_000_000)))
    val attempts = List(random.nextInt(1, 4)) {
        Attempt(if (chance(0.5)) FakeOutcome.Failure("busy") else FakeOutcome.Output("ok"), random.nextLong(1, 2_000_000))
    }
    emitFake("weather", input, attempts, COMPACT)
    fakeInputsByTool.getValue("weather") += input
    return ToolPart(freshId(), "weather", input, false)
}

var wideEpisodes = 0

fun episodeWide() {
    if (simulator.maxTurns < 2) emitConfig(maxTurns = 10)
    wideEpisodes++
    emitConfig(
        concurrency = when (wideEpisodes) {
            1 -> 1
            2 -> listOf(7, 1_000).random(random)
            else -> 1_000_000
        },
        timeoutMs = listOf(0L, 1_000_000L, 1_000_000_000L).random(random),
        retries = listOf(1, 2, 3, 10).random(random),
        backoffMs = BACKOFFS.sample(),
        trace = chance(0.6),
    )
    val tools = List(random.nextInt(2_001, 2_600)) { if (chance(0.85)) wideToolPart() else toolPart() }
    emitReply("tool", tools)
    emitAnswer()
    emitAsk()
}

val EPISODES: WeightedSampler<() -> Unit> = WeightedSampler(
    listOf(
        ::episodeToolLoop,
        ::episodeAnswer,
        ::episodeMaxTurns,
        ::episodeCut,
        ::episodeBlankAsk,
        ::episodeDuplicateId,
        ::episodeExhausted,
        ::episodeMalformedSchema,
        ::episodeRepeat,
        ::episodeParallel,
        ::episodeTimeoutRepeat,
        ::episodeBackoffQueue,
    ),
    listOf(24.0, 5.0, 5.0, 5.0, 2.0, 3.0, 3.0, 3.0, 4.0, 20.0, 4.0, 8.0),
)

ALL_KINDS.forEach { kind -> emitToolDefinition(kind) }
if (largeScale || wideRun) emitConfig(repeatLimit = largeRepeatLimit)

val bulkFakes = (options.fakes * 0.8).toInt()
repeat(bulkFakes) { addFake(pickKind()) }

val lateFakesPerAsk = (options.fakes - bulkFakes).toDouble() / options.asks
val tailAsks = if (options.asks >= 4) 2 else 0
val wideAsks = (1..options.wide).map { it * options.asks / (options.wide + 1) }.toMutableList()
while (asksEmitted < options.asks - tailAsks) {
    if (chance(0.35)) repeat(random.nextInt(1, 3)) { emitNoise() }
    if (chance(0.06)) emitReset()
    if (chance(0.07)) emitRandomConfig()
    var lateFakes = lateFakesPerAsk
    while (lateFakes >= 1 || chance(lateFakes)) {
        addFake(pickKind())
        lateFakes -= 1
    }
    if (chance(0.05)) replaceFake()
    if (chance(0.02) && ACTIVE_SCHEMA_KINDS.isNotEmpty()) {
        val kind = ACTIVE_SCHEMA_KINDS.random(random)
        emitToolDefinition(kind, withSchema = simulator.tools[kind.name] is ToolState.Plain || chance(0.3))
    }
    if (simulator.maxTurns <= 6 && chance(0.7)) emitConfig(maxTurns = 10)
    when {
        simulator.script.isNotEmpty() -> emitAsk()
        wideAsks.isNotEmpty() && asksEmitted >= wideAsks.first() -> {
            wideAsks.removeFirst()
            episodeWide()
        }
        else -> EPISODES.sample().invoke()
    }
}

val invalidName = listOf("날씨", "a b", "", "x".repeat(65), "tool.v1", "weather!").random(random)
if (chance(0.5)) {
    emit("tool " + obj("name" to str(invalidName)).render(commandStyle()))
} else {
    emit("tool {\"name\":" + str(invalidName).render() + ",\"schema\":" + MALFORMED_SCHEMAS.random(random) + "}")
}
simulator.invalidToolName = true
while (asksEmitted < options.asks) {
    if (chance(0.5)) emitAnswer()
    emitAsk(blank = chance(0.2))
}

System.out.write(output.toString().encodeToByteArray())
System.out.flush()
