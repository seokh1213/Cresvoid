import java.math.BigDecimal
import kotlin.math.abs
import kotlin.random.Random

data class Options(val asks: Int, val fakes: Int, val uses: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--asks" to "60", "--fakes" to "200", "--uses" to "6", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        asks = values.getValue("--asks").toInt(),
        fakes = values.getValue("--fakes").toInt(),
        uses = values.getValue("--uses").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val output = StringBuilder()

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

data class JBool(val value: Boolean) : Json

data object JNull : Json

data class JArr(val items: List<Json>) : Json

data class JObj(val fields: List<Pair<String, Json>>) : Json

class JsonStyle(val spacing: Double, val escapes: Double, val numberVariety: Double, val shuffleFields: Boolean)

val COMPACT = JsonStyle(spacing = 0.0, escapes = 0.0, numberVariety = 0.0, shuffleFields = false)

fun commandStyle(): JsonStyle = when (random.nextInt(10)) {
    0 -> JsonStyle(spacing = 0.5, escapes = 0.05, numberVariety = 0.3, shuffleFields = true)
    1 -> JsonStyle(spacing = 0.1, escapes = 0.3, numberVariety = 0.5, shuffleFields = false)
    2, 3 -> JsonStyle(spacing = 0.05, escapes = 0.02, numberVariety = 0.2, shuffleFields = true)
    else -> JsonStyle(spacing = 0.0, escapes = 0.01, numberVariety = 0.05, shuffleFields = false)
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
val CURRENCIES = zipf(listOf("KRW", "USD", "JPY", "EUR"))

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

fun anyNumber(): JNum = when (random.nextInt(12)) {
    0 -> JNum(BigDecimal.ZERO)
    1 -> num(-smallInt(100))
    2 -> decimal("${random.nextInt(0, 1000)}.${random.nextInt(1, 100)}")
    3 -> JNum(BigDecimal.TEN.pow(random.nextInt(10, 25)))
    4 -> decimal("-0.${random.nextInt(1, 999).toString().padStart(3, '0')}")
    else -> num(smallInt(1000))
}

fun sku(index: Int): String = "A$index"

class ToolKind(val name: String, val makeInput: () -> JObj)

val STOCK = ToolKind("stock") { obj("sku" to str(sku(skewedIndex(5000))), "wh" to num(smallInt())) }
val TOOL_KINDS = listOf(
    STOCK,
    ToolKind("price") {
        val base = listOf("sku" to str(sku(skewedIndex(3000))), "currency" to str(CURRENCIES.sample()))
        JObj(if (chance(0.3)) base + ("coupon" to (if (chance(0.5)) JNull else str("C" + random.nextInt(100)))) else base)
    },
    ToolKind("weather") { obj("city" to str(CITIES.sample())) },
    ToolKind("search") {
        val base = listOf("q" to str(text(3)), "page" to num(smallInt(5)))
        val filters = obj(
            "brand" to JArr(List(random.nextInt(0, 3)) { str(WORD_SAMPLER.sample()) }),
            "min_price" to decimal("${random.nextInt(0, 500)}.5"),
        )
        JObj(if (chance(0.4)) base + ("filters" to filters) else base)
    },
    ToolKind("order") { obj("id" to num(smallInt(1_000_000))) },
    ToolKind("memo") { obj("note" to str(text(6))) },
    ToolKind("geo") {
        obj("lat" to decimal("37.${random.nextInt(1000, 9999)}"), "lng" to decimal("126.${random.nextInt(100, 999)}0"))
    },
    ToolKind("flags") {
        obj("on" to JBool(random.nextBoolean()), "tags" to JArr(List(random.nextInt(0, 4)) { str(WORD_SAMPLER.sample()) }), "extra" to JNull)
    },
    ToolKind("now") { obj() },
    ToolKind("Fetch-URL_v2") { obj("url" to str("https://example.com/${WORD_SAMPLER.sample()}/${random.nextInt(100)}?q=a/b")) },
    ToolKind("inventory_snapshot_" + "x".repeat(45)) { obj("at" to anyNumber(), "ids" to JArr(List(random.nextInt(1, 4)) { anyNumber() })) },
)
val TOOL_SAMPLER = zipf(TOOL_KINDS, offset = 1.2)
val UNDEFINED_TOOLS = listOf("ship", "mail", "refund", "Weather", "STOCK", "translate", "x", "price2")

class FakeEntry(val input: JObj)

val fakesByTool = TOOL_KINDS.associate { it.name to mutableListOf<FakeEntry>() }
val fakeKeys = HashSet<String>()

fun canonicalKey(tool: String, value: Json): String = tool + "\u0000" + canonical(value)

fun quoted(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun canonical(value: Json): String = when (value) {
    is JStr -> quoted(value.value)
    is JNum -> value.value.stripTrailingZeros().let { if (it.signum() == 0) "0" else it.toString() }
    is JBool -> value.value.toString()
    JNull -> "null"
    is JArr -> value.items.joinToString(",", "[", "]") { canonical(it) }
    is JObj -> value.fields.sortedBy { it.first }.joinToString(",", "{", "}") { quoted(it.first) + ":" + canonical(it.second) }
}

val FAKE_OUTPUTS = listOf("맑음\n최고 24도", "12", "15000", "", "주문 7: 배송 중", "{\"ok\":true}", "없음", "3건", "<html>b</html>", "a\\b", "\"인용\"")

fun fakeOutput(): String = if (chance(0.5)) FAKE_OUTPUTS.random(random) else text(4)

fun emitFake(tool: String, input: JObj, style: JsonStyle, output: String = fakeOutput()) {
    val fields = listOf("tool" to str(tool), "input" to input, "output" to str(output))
    emit("fake " + JObj(fields).render(style))
}

fun addNewFake(kind: ToolKind) {
    repeat(20) {
        val input = kind.makeInput()
        if (fakeKeys.add(canonicalKey(kind.name, input))) {
            fakesByTool.getValue(kind.name) += FakeEntry(input)
            emitFake(kind.name, input, commandStyle())
            return
        }
    }
}

fun addStockFake(serial: Int) {
    val input = obj("sku" to str(sku(serial / 9)), "wh" to num((serial % 9 + 1).toLong()))
    if (fakeKeys.add(canonicalKey(STOCK.name, input))) {
        fakesByTool.getValue(STOCK.name) += FakeEntry(input)
        emitFake(STOCK.name, input, COMPACT, output = if (chance(0.9)) random.nextInt(0, 500).toString() else fakeOutput())
    }
}

fun replaceFake() {
    val kind = TOOL_SAMPLER.sample()
    val entries = fakesByTool.getValue(kind.name)
    if (entries.isEmpty()) return
    emitFake(kind.name, entries[skewedIndex(entries.size)].input, VARIANT)
}

fun nearMiss(value: JObj): JObj {
    if (value.fields.isEmpty()) return obj("memo_id" to num(1))
    val index = random.nextInt(value.fields.size)
    val (name, item) = value.fields[index]
    val mutated: Json = when (item) {
        is JNum -> if (chance(0.5)) JStr(plainLiteral(item.value)) else JNum(item.value + BigDecimal.ONE)
        is JStr -> if (chance(0.5)) JStr(item.value + "x") else JStr(item.value.uppercase() + "!")
        is JBool -> JBool(!item.value)
        JNull -> JStr("null")
        is JArr -> if (item.items.size > 1) JArr(item.items.reversed()) else JArr(item.items + num(0))
        is JObj -> nearMiss(item)
    }
    return when {
        chance(0.15) -> JObj(value.fields.filterIndexed { position, _ -> position != index })
        chance(0.15) && value.fields.none { it.first == "memo_id" } -> JObj(value.fields + ("memo_id" to num(1)))
        else -> JObj(value.fields.mapIndexed { position, field -> if (position == index) name to mutated else field })
    }
}

val largeScale = options.fakes >= 10_000
val hotToolShare = if (largeScale) 0.75 else 0.0

fun pickEntry(kind: ToolKind, entries: List<FakeEntry>): FakeEntry {
    if (largeScale && kind == STOCK) return entries[(entries.size * Math.pow(random.nextDouble(), 1.5)).toInt()]
    return entries[skewedIndex(entries.size)]
}

fun toolUseInput(kind: ToolKind): JObj {
    val entries = fakesByTool.getValue(kind.name)
    val roll = random.nextDouble()
    val exactShare = if (largeScale && kind == STOCK) 0.6 else 0.72
    return when {
        entries.isNotEmpty() && roll < exactShare -> pickEntry(kind, entries).input
        entries.isNotEmpty() && roll < exactShare + 0.16 -> nearMiss(pickEntry(kind, entries).input)
        else -> kind.makeInput()
    }
}

class SimMessage(val fromUser: Boolean, val toolIds: List<String>, var hasBlankText: Boolean)

class SimReply(val stop: String, val toolIds: List<String>, val cutInsideToolUse: Boolean)

class Simulator {
    var maxTurns = 10
    var replyLimit = 1_000
    var replyLimitCap = 4_000
    var invalidToolName = false
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

    fun ask(blank: Boolean): String {
        val last = history.lastOrNull()
        if (last != null && last.fromUser) last.hasBlankText = last.hasBlankText || blank else history += SimMessage(true, emptyList(), blank)
        var turn = 1
        var limit = replyLimit
        while (true) {
            val ids = toolIdsInHistory()
            val rejection = when {
                invalidToolName -> "tool_name"
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
            when {
                reply.stop == "end" -> {
                    history += SimMessage(false, reply.toolIds, false)
                    return "end"
                }
                reply.stop == "tool" -> {
                    history += SimMessage(false, reply.toolIds, false)
                    if (turn == maxTurns) return "max_turns"
                    history += SimMessage(true, emptyList(), false)
                }
                reply.cutInsideToolUse -> {
                    if (limit == replyLimitCap) return "length"
                    if (turn == maxTurns) return "max_turns"
                    limit = minOf(limit * 2, replyLimitCap)
                }
                else -> {
                    history += SimMessage(false, reply.toolIds, false)
                    return "length"
                }
            }
            turn++
        }
    }
}

val simulator = Simulator()

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

fun textBlockJson(block: TextPart): JObj = JObj(listOf("type" to str("text"), "text" to str(block.text)))

fun blockJson(block: PlannedBlock): JObj = when (block) {
    is TextPart -> textBlockJson(block)
    is ToolPart -> {
        val fields = listOf("type" to str("tool"), "id" to str(block.id), "name" to str(block.name), "input" to block.input)
        JObj(if (block.explicitComplete) fields + ("incomplete" to JBool(false)) else fields)
    }
    is CutPart -> JObj(listOf("type" to str("tool"), "id" to str(block.id), "name" to str(block.name), "incomplete" to JBool(true)))
}

fun emitReply(stop: String, blocks: List<PlannedBlock>) {
    val reply = JObj(listOf("stop" to str(stop), "content" to JArr(blocks.map(::blockJson))))
    emit(commandLine("reply", reply.render(replyStyle())))
    simulator.script.addLast(
        SimReply(stop, blocks.filterIsInstance<ToolPart>().map { it.id }, cutInsideToolUse = blocks.last() is CutPart),
    )
}

fun replyStyle(): JsonStyle = if (chance(if (largeScale) 0.7 else 0.15)) VARIANT else commandStyle()

fun commandLine(name: String, argument: String): String = when (random.nextInt(25)) {
    0 -> "  $name $argument"
    1 -> "$name    $argument  "
    else -> "$name $argument"
}

val undefinedToolFakes = mutableListOf<Pair<String, JObj>>()

fun addUndefinedToolFake() {
    val name = UNDEFINED_TOOLS.random(random)
    val input = TOOL_SAMPLER.sample().makeInput()
    undefinedToolFakes += name to input
    emitFake(name, input, commandStyle())
}

fun toolPart(id: String = freshId()): ToolPart {
    if (chance(0.08)) {
        val (name, input) = undefinedToolFakes.takeIf { it.isNotEmpty() && chance(0.5) }?.random(random)
            ?: (UNDEFINED_TOOLS.random(random) to TOOL_SAMPLER.sample().makeInput())
        return ToolPart(id, name, input, chance(0.1))
    }
    val kind = if (chance(hotToolShare)) STOCK else TOOL_SAMPLER.sample()
    return ToolPart(id, kind.name, toolUseInput(kind), chance(0.1))
}

fun toolUseCount(): Int = random.nextInt(1, options.uses + 1)

fun toolReplyBlocks(toolUses: Int = toolUseCount()): List<PlannedBlock> {
    val tools = List(toolUses) { toolPart() }
    val blocks = mutableListOf<PlannedBlock>()
    if (chance(0.4)) blocks += TextPart(text())
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
        2 -> toolReplyBlocks(listOf(1, 2).random(random))
        else -> listOf(TextPart(text()), toolPart())
    }
    emitReply("length", prefix + CutPart(freshId(), TOOL_SAMPLER.sample().name))
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

fun emitConfig(maxTurns: Int? = null, replyLimit: Int? = null, replyLimitCap: Int? = null) {
    val fields = listOfNotNull(
        maxTurns?.let { "max_turns" to num(it.toLong()) },
        replyLimit?.let { "reply_limit" to num(it.toLong()) },
        replyLimitCap?.let { "reply_limit_cap" to num(it.toLong()) },
    )
    emit(commandLine("config", JObj(fields).render(commandStyle())))
    maxTurns?.let { simulator.maxTurns = it }
    replyLimit?.let { simulator.replyLimit = it }
    replyLimitCap?.let { simulator.replyLimitCap = it }
    check(simulator.replyLimit <= simulator.replyLimitCap)
}

fun emitRandomConfig() {
    when (random.nextInt(6)) {
        0 -> emitConfig(maxTurns = listOf(1, 2, 3, 5, 10, 10, 1000).random(random))
        1 -> {
            val cap = listOf(1, 1000, 4000, 5000, 1_000_000).random(random)
            val limit = listOf(1, cap / 3, cap / 2, cap, 1000).filter { it in 1..cap }.random(random)
            emitConfig(replyLimit = limit, replyLimitCap = cap)
        }
        2 -> emitConfig(replyLimitCap = maxOf(simulator.replyLimit, listOf(4000, 7000, 1_000_000).random(random)))
        3 -> emit("config {}")
        4 -> emitConfig(maxTurns = 10, replyLimit = 1000, replyLimitCap = 4000)
        else -> emitConfig(maxTurns = listOf(4, 6, 8).random(random))
    }
}

val UNKNOWN_COMMANDS = listOf("Ask", "ASK", "TOOL", "Reply", "fake2", "con-fig", "réset", "도구", "ask!", "tool.", "Reset", "1", "-", "ASK_")

fun okText(): JObj = obj("text" to str(text(2)))

fun badLine(): String = when (random.nextInt(48)) {
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
    21 -> "ask {\"text\":\"\\u12\"}"
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
        "{\"tool\":\"now\",\"input\":\"{}\",\"output\":\"x\"}",
        "{\"tool\":\"now\",\"input\":null,\"output\":\"x\"}",
        "{\"tool\":\"now\",\"input\":{},\"output\":12}",
    ).random(random)
    31 -> "reply " + listOf(
        "{\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}",
        "{\"stop\":\"end\"}",
        "{\"stop\":\"END\",\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}",
        "{\"stop\":\"\",\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}",
        "{\"stop\":1,\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}",
        "{\"stop\":\"end\",\"content\":{}}",
        "{\"stop\":\"end\",\"content\":[]}",
        "{\"stop\":\"end\",\"content\":[1]}",
        "{\"stop\":\"end\",\"content\":[\"a\"]}",
        "{\"stop\":\"end\",\"content\":[{}]}",
        "{\"stop\":\"end\",\"content\":[{\"type\":\"image\"}]}",
        "{\"stop\":\"end\",\"content\":[{\"type\":\"Text\"}]}",
        "{\"stop\":\"end\",\"content\":[{\"type\":1}]}",
    ).random(random)
    32 -> "reply {\"stop\":\"end\",\"content\":[{\"type\":\"text\"" + listOf("", ",\"text\":\"\"", ",\"text\":\" \\t\\n\"", ",\"text\":1", ",\"text\":null").random(random) + "}]}"
    33 -> "reply {\"stop\":\"tool\",\"content\":[" + badToolBlock() + "]}"
    34 -> "reply {\"stop\":\"length\",\"content\":[{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":true},{\"type\":\"text\",\"text\":\"계속\"}]}"
    35 -> "reply {\"stop\":\"tool\",\"content\":[{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":true}]}"
    36 -> "reply {\"stop\":\"end\",\"content\":[{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"input\":{}}]}"
    37 -> "reply {\"stop\":\"end\",\"content\":[{\"type\":\"text\",\"text\":\"a\"},{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":true}]}"
    38 -> "reply {\"stop\":\"tool\",\"content\":[{\"type\":\"text\",\"text\":\"도구를 쓰겠다.\"}]}"
    39 -> "reply {\"stop\":\"length\",\"content\":[{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":true},{\"type\":\"tool\",\"id\":\"x2\",\"name\":\"memo\",\"incomplete\":true}]}"
    40 -> "config {\"max_turns\":" + listOf("0", "1001", "1.5", "\"3\"", "null", "-1", "1e4", "true").random(random) + "}"
    41 -> "config {\"reply_limit\":" + listOf("0", "1000001", "2.5", "\"1000\"", "-5", "1e7").random(random) + "}"
    42 -> "config {\"reply_limit_cap\":" + listOf("0", "1000001", "999.9", "[]").random(random) + "}"
    43 -> "config {\"reply_limit\":${simulator.replyLimitCap + random.nextInt(1, 1000)}}"
    44 -> "config {\"reply_limit_cap\":${simulator.replyLimit - 1}}".takeIf { simulator.replyLimit > 1 } ?: "config {\"max_turns\":0}"
    45 -> "config {\"max_turns\":3,\"reply_limit\":0}"
    46 -> "config {\"reply_limit\":2000,\"max_turns\":0,\"reply_limit_cap\":9000}"
    else -> "ask {\"text\":\"a\"" + listOf("]", ":", "}}", ",\"text\"").random(random) + "}"
}

fun badToolBlock(): String = listOf(
    "{\"type\":\"tool\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"아이디\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"${"a".repeat(65)}\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x 1\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x.1\",\"name\":\"memo\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"메모\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":1,\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\"}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"input\":[]}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"input\":null}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":false}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":\"true\",\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":null,\"input\":{}}",
    "{\"type\":\"tool\",\"id\":\"x1\",\"name\":\"memo\",\"incomplete\":true,\"input\":{}}",
    "{\"type\":\"tool\",\"id\":7,\"name\":\"memo\",\"input\":{}}",
).random(random)

fun emitNoise() {
    when (random.nextInt(10)) {
        0 -> emit("")
        1 -> emit(" ".repeat(random.nextInt(1, 4)))
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
        toolReplyBlocks(1) + toolPart(reused)
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

val EPISODES: WeightedSampler<() -> Unit> = WeightedSampler(
    listOf(::episodeToolLoop, ::episodeAnswer, ::episodeMaxTurns, ::episodeCut, ::episodeBlankAsk, ::episodeDuplicateId, ::episodeExhausted),
    listOf(30.0, 8.0, 8.0, 8.0, 3.0, 4.0, 4.0),
)

val earlyKinds = TOOL_KINDS.filter { it.name != "geo" }
earlyKinds.forEach { kind ->
    val description = if (chance(0.5)) ",\"description\":" + str(text(4)).render(commandStyle()) else ""
    emit("tool {\"name\":" + str(kind.name).render(commandStyle()) + description + "}")
}

val bulkFakes = (options.fakes * 0.8).toInt()
val stockShare = if (largeScale) 0.92 else 0.5
var stockSerial = 0
repeat(bulkFakes) {
    if (chance(stockShare)) addStockFake(stockSerial++) else addNewFake(TOOL_SAMPLER.sample())
}
val geo = TOOL_KINDS.first { it.name == "geo" }
repeat(minOf(5, options.fakes)) { addNewFake(geo) }
emit("tool {\"name\":\"geo\",\"description\":\"위도와 경도로 주소를 찾는다\"}")

val lateFakesPerAsk = (options.fakes - bulkFakes).toDouble() / options.asks
val tailAsks = if (options.asks >= 4) 2 else 0
while (asksEmitted < options.asks - tailAsks) {
    if (chance(0.35)) repeat(random.nextInt(1, 3)) { emitNoise() }
    if (chance(0.06)) emitReset()
    if (chance(0.07)) emitRandomConfig()
    var lateFakes = lateFakesPerAsk
    while (lateFakes >= 1 || chance(lateFakes)) {
        if (chance(stockShare)) addStockFake(stockSerial++) else addNewFake(TOOL_SAMPLER.sample())
        lateFakes -= 1
    }
    if (chance(0.05)) replaceFake()
    if (chance(0.02)) emit("tool " + obj("name" to str(TOOL_SAMPLER.sample().name), "description" to str(text(3))).render(commandStyle()))
    if (chance(0.02)) addUndefinedToolFake()
    if (simulator.maxTurns <= 6 && chance(0.7)) emitConfig(maxTurns = 10)
    if (simulator.script.isEmpty()) EPISODES.sample().invoke() else emitAsk()
}

val invalidName = listOf("날씨", "a b", "", "x".repeat(65), "tool.v1", "weather!").random(random)
emit("tool " + obj("name" to str(invalidName)).render(commandStyle()))
simulator.invalidToolName = true
while (asksEmitted < options.asks) {
    if (chance(0.5)) emitAnswer()
    emitAsk(blank = chance(0.2))
}

System.out.write(output.toString().encodeToByteArray())
System.out.flush()
