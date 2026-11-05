import kotlin.random.Random

val options = args.toList().chunked(2).associate { pair ->
    require(pair.size == 2 && pair[0] in setOf("--seed", "--messages")) { "Use --messages 1..2000 --seed <integer>" }
    pair[0] to pair[1].toInt()
}
val random = Random(options["--seed"] ?: 1)
val messageCount = options["--messages"] ?: 240
require(messageCount in 1..2000)

data class RawJson(val text: String)
typealias Fields = Map<String, Any?>
fun fields(vararg entries: Pair<String, Any?>): Fields = linkedMapOf(*entries)

fun quoted(text: String): String = buildString {
    append('"')
    for (character in text) append(when (character) {
        '"' -> "\\\""
        '\\' -> "\\\\"
        '\b' -> "\\b"
        '\u000c' -> "\\f"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        else -> if (character < ' ') "\\u%04x".format(character.code) else character.toString()
    })
    append('"')
}

fun json(value: Any?): String = when (value) {
    null -> "null"
    is RawJson -> value.text
    is String -> quoted(value)
    is Number, is Boolean -> value.toString()
    is Map<*, *> -> value.entries.shuffled(random).joinToString(",", "{", "}") { (key, entry) -> "${quoted(key as String)}:${json(entry)}" }
    is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
    else -> error("Unsupported generator value")
}

val versionKey = "io.modelcontextprotocol/protocolVersion"
val capabilitiesKey = "io.modelcontextprotocol/clientCapabilities"
val quoteKey = "com.cresvoid/quote-preview"
fun metadata(capabilities: Any? = fields("experimental" to fields(quoteKey to fields())), version: Any? = "2026-07-28"): Fields =
    fields(versionKey to version, capabilitiesKey to capabilities)

fun nextId(): Any = listOf(
    "", "7", "일반", "희귀😀\n\"\\\u0001", random.nextInt(-100, 101),
    RawJson("-0"), RawJson("7.00e0"), RawJson("9007199254740991"),
    RawJson("-9007199254740991"), RawJson("\"\\u0037\""),
).random(random)

fun request(method: String, parameters: Fields = fields(), meta: Any? = metadata(), id: Any? = nextId()): String = json(fields(
    "jsonrpc" to "2.0", "id" to id, "method" to method,
    "params" to (fields("_meta" to meta) + parameters),
))

fun tool(name: Any?, arguments: Any?): String = request("tools/call", fields("name" to name, "arguments" to arguments))
fun line(id: Any?, quantity: Any?): Fields = fields("productId" to id, "quantity" to quantity)
fun quote(vararg lines: Any?): String = tool("cart_quote", fields("lines" to lines.toList()))

fun toolCases(): List<String> = buildList {
    add(request("tools/list"))
    add("\r\t " + request("tools/list") + "\r")
    var nested: Any? = "leaf"
    repeat(14) { nested = fields("nest" to nested) }
    add(request("tools/list", fields("ignored" to nested)))
    val longLine = request("tools/list", fields("ignored" to List(14) { "a".repeat(2048) }))
    add(longLine + " ".repeat(32768 - longLine.toByteArray(Charsets.UTF_8).size))
    add(tool("product_get", fields("productId" to "😀".repeat(1024))))
    add(request("tools/list", fields("unused" to listOf(null, false, fields("nested" to "무시")))))
    for (cursor in listOf(null, "", "tools:2", "resources:2", 0, fields())) add(request("tools/list", fields("cursor" to cursor)))
    val ids = listOf("p-100", "p-200", "p-300", "p-400", "p-500", "p-999", "", " p-100", "P-100", "𐀀", "\ue000", "희귀\n\"\\", "x".repeat(2048), "\b\u000c\r\t\u0000\u001f/")
    for (id in ids) add(tool("product_get", fields("productId" to id)))
    for (arguments in listOf(null, fields(), fields("productId" to null), fields("productId" to 1),
        fields("productId" to "p-100", "extra" to false), listOf("p-100"))) add(tool("product_get", arguments))
    for (name in listOf(null, 2, "", "Product_get", "unknown")) add(tool(name, fields("productId" to "p-100")))
    add(request("tools/call", fields("name" to "product_get")))
    add(request("tools/call", fields("arguments" to fields("productId" to "p-100"))))
    add(quote(line("p-100", 1), line("p-300", 1), line("p-100", 2)))
    add(quote(line("p-100", 4), line("p-100", 4)))
    add(quote(line("p-100", 4), line("p-100", 5)))
    add(quote(line("p-100", 8), line("p-300", 5), line("p-400", 3), line("p-500", 20)))
    add(quote(*Array(20) { line("p-500", 1) }))
    add(quote(*Array(20) { line("p-100", 1000) }))
    add(quote(*Array(21) { line("p-500", 1) }))
    add(quote(line("p-200", 1), line("p-100", 9)))
    add(quote(line("p-200", 1), line("z-missing", 1), line("a-missing", 1)))
    add(quote(line("\ue000", 1), line("𐀀", 1)))
    for (quantity in listOf(0, -1, 1000, 1001, null, true, "1", RawJson("1.5"), RawJson("1e0"), RawJson("1.0"), RawJson("1e-9999999999"))) {
        add(quote(line("p-500", quantity)))
    }
    for (arguments in listOf(null, fields(), fields("lines" to null), fields("lines" to fields()),
        fields("lines" to emptyList<Any>()), fields("lines" to listOf(line("p-100", 1)), "extra" to 0),
        fields("lines" to listOf(null)), fields("lines" to listOf(fields("productId" to "p-100"))),
        fields("lines" to listOf(line(null, 1))), fields("lines" to listOf(line("p-100", 1) + ("extra" to 0))))) {
        add(tool("cart_quote", arguments))
    }
    for (meta in listOf(null, 1, fields(), fields(versionKey to "2026-07-28"), fields(capabilitiesKey to fields()),
        metadata(null), metadata(version = null), metadata(version = 0))) add(request("tools/list", meta = meta))
    add(json(fields("jsonrpc" to "2.0", "id" to nextId(), "method" to "tools/list")))
    add(json(fields("jsonrpc" to "2.0", "id" to nextId(), "method" to "tools/list", "params" to null)))
    add(request("tools/list", meta = metadata(fields("experimental" to fields("rare/extension" to fields())))))
    add(json(fields("jsonrpc" to "2.0", "method" to "unknown")))
    add(json(fields("jsonrpc" to "2.0", "method" to "tools/call", "params" to fields("name" to "unknown"))))
    add(json(fields("jsonrpc" to "2.0", "method" to "tools/list", "params" to fields("_meta" to metadata(version = "old")))))
    add(json(fields("jsonrpc" to "2.0", "id" to nextId(), "result" to fields())))
    add(json(fields("jsonrpc" to "2.0", "error" to fields("code" to -32600, "message" to "peer"))))
    add(json(fields("jsonrpc" to "2.0", "id" to nextId(), "error" to fields("code" to RawJson("-32600.0"), "message" to "peer"))))
}

fun resourceCases(): List<String> = buildList {
    for (method in listOf("resources/list", "prompts/list")) {
        for (size in 1..5) {
            for (prefix in listOf("", "p-", "p-3", "c", "inspect_", "missing")) {
                add(request(method, fields("pageSize" to size, "namePrefix" to prefix)))
            }
            val kind = method.substringBefore('/')
            for (offset in 1..4) {
                val cursor = if (size == 2) "$kind:$offset" else "$kind:$offset:$size:"
                add(request(method, fields("cursor" to cursor)))
                add(request(method, fields("cursor" to cursor, "pageSize" to size)))
                add(request(method, fields("cursor" to cursor, "pageSize" to size % 5 + 1)))
            }
        }
        for (parameters in listOf(
            fields("pageSize" to RawJson("3.0e0")), fields("pageSize" to null),
            fields("pageSize" to 0), fields("pageSize" to 6), fields("pageSize" to RawJson("1.5")),
            fields("namePrefix" to null), fields("namePrefix" to "P-"),
            fields("namePrefix" to "x".repeat(33)),
            fields("cursor" to "resources:3:3:p-"),
            fields("cursor" to "resources:3:3:p-", "namePrefix" to ""),
            fields("cursor" to "prompts:1:1:", "pageSize" to 1, "namePrefix" to ""),
        )) add(request(method, parameters))
        add(request(method))
        for (cursor in listOf("resources:2", "resources:4", "prompts:2", "", "resources:0", "resources:02", "resources:6", null, 2, false)) {
            add(request(method, fields("cursor" to cursor)))
        }
    }
    for (uri in listOf("shop://products/p-100", "shop://products/p-200", "shop://products/p-300", "shop://products/p-400", "shop://products/p-500",
        "shop://products/p-999", "shop://products/p-100/", "shop://products/p-%31%30%30", "SHOP://products/p-100", "shop://products/p-100?q=1", "", null, 1)) {
        add(request("resources/read", fields("uri" to uri)))
    }
    add(request("resources/read"))
    add(request("prompts/get", fields("name" to "summarize_catalog")))
    add(request("prompts/get", fields("name" to "summarize_catalog", "arguments" to fields())))
    for (arguments in listOf(fields("leftId" to "p-400", "rightId" to "p-100"), fields("leftId" to "p-300", "rightId" to "p-300"),
        fields("leftId" to "p-100"), fields("leftId" to "p-100", "rightId" to "p-999"))) {
        add(request("prompts/get", fields("name" to "compare_products", "arguments" to arguments)))
    }
    add(request("prompts/get", fields("name" to "inspect_product", "arguments" to fields("productId" to "p-100"))))
    for (question in listOf("", "구매 전 확인", "희귀😀\n\"\\\b\t\r\u0000", "q".repeat(2048), null, 1)) {
        add(request("prompts/get", fields("name" to "inspect_product", "arguments" to fields("productId" to "p-300", "question" to question))))
    }
    for (name in listOf("compare_products", "inspect_product", "summarize_catalog", "unknown", null, 1)) {
        for (arguments in listOf(null, false, fields("unknown" to "value"), fields("productId" to "p-999"))) {
            add(request("prompts/get", fields("name" to name, "arguments" to arguments)))
        }
    }
    add(request("prompts/get"))
    add(json(fields("jsonrpc" to "2.0", "method" to "prompts/get", "params" to fields("arguments" to null))))
}

val output = System.out.bufferedWriter(Charsets.UTF_8)
var emitted = 0
while (emitted < messageCount) {
    val cases = toolCases().toMutableList()
    cases += resourceCases()
    for (message in cases.shuffled(random)) {
        if (emitted == messageCount) break
        output.write(message)
        output.newLine()
        emitted++
    }
}
output.flush()
