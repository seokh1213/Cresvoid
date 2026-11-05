sealed interface ToolCall {
    data class ProductGet(val productId: String) : ToolCall
    data class CartQuote(val lines: List<CartLine>) : ToolCall
}
data class CartLine(val productId: String, val quantity: Int)

object Tools {
    fun list(params: JsonObject): JsonObject {
        if (params.contains("cursor")) invalidParams()
        return cacheableResult("tools" to arr(
            descriptor("cart_quote", "장바구니의 재고와 합계를 확인한다.", quoteSchema()),
            descriptor("product_get", "상품 한 개를 조회한다.", objectSchema(
                obj("productId" to obj("type" to str("string"))), "productId",
            )),
        ))
    }

    fun parseCall(params: JsonObject): ToolCall {
        val name = params["name"].requireString()
        val arguments = params["arguments"].requireObject()
        return when (name) {
            "product_get" -> {
                if (arguments.fields.keys != setOf("productId")) invalidParams()
                ToolCall.ProductGet(arguments["productId"].requireString())
            }
            "cart_quote" -> parseQuote(arguments)
            else -> invalidParams()
        }
    }

    fun execute(call: ToolCall): JsonObject = when (call) {
        is ToolCall.ProductGet -> {
            val product = Catalog.byId[call.productId]
            if (product == null) failure("UNKNOWN_PRODUCT ${call.productId}") else success(product.text())
        }
        is ToolCall.CartQuote -> quote(call.lines)
    }

    private fun parseQuote(arguments: JsonObject): ToolCall.CartQuote {
        if (arguments.fields.keys != setOf("lines")) invalidParams()
        val lines = (arguments["lines"] as? JsonArray)?.values ?: invalidParams()
        if (lines.size !in 1..20) invalidParams()
        val parsed = lines.map { value ->
            val line = value.requireObject()
            if (line.fields.keys != setOf("productId", "quantity")) invalidParams()
            val id = line["productId"].requireString()
            val quantity = (line["quantity"] as? JsonNumber)?.integerOrNull() ?: invalidParams()
            if (quantity !in 1..1000) invalidParams()
            CartLine(id, quantity.toInt())
        }
        return ToolCall.CartQuote(parsed)
    }

    private fun quote(lines: List<CartLine>): JsonObject {
        val quantities = sortedMapOf<String, Int>()
        for (line in lines) quantities[line.productId] = quantities.getOrDefault(line.productId, 0) + line.quantity
        val unknown = quantities.keys.firstOrNull { it !in Catalog.byId }
        if (unknown != null) return failure("UNKNOWN_PRODUCT $unknown")
        val unavailable = quantities.keys.firstOrNull { quantities.getValue(it) > Catalog.byId.getValue(it).stock }
        if (unavailable != null) return failure("OUT_OF_STOCK $unavailable")
        val total = quantities.entries.sumOf { (id, quantity) -> Catalog.byId.getValue(id).price * quantity }
        return success("total=$total\nitems=${quantities.size}")
    }

    private fun success(text: String): JsonObject = result(text, false)
    private fun failure(text: String): JsonObject = result(text, true)
    private fun result(text: String, isError: Boolean): JsonObject = obj(
        "resultType" to str("complete"), "content" to arr(obj("type" to str("text"), "text" to str(text))),
        "isError" to JsonBoolean(isError),
    )

    private fun descriptor(name: String, description: String, schema: JsonObject): JsonObject = obj(
        "name" to str(name), "description" to str(description), "inputSchema" to schema,
    )

    private fun objectSchema(properties: JsonObject, vararg required: String): JsonObject = obj(
        "type" to str("object"), "additionalProperties" to JsonBoolean(false),
        "properties" to properties, "required" to JsonArray(required.map(::str)),
    )

    private fun quoteSchema(): JsonObject = objectSchema(obj("lines" to obj(
        "type" to str("array"), "minItems" to num(1), "maxItems" to num(20),
        "items" to objectSchema(obj(
            "productId" to obj("type" to str("string")),
            "quantity" to obj("type" to str("integer"), "minimum" to num(1), "maximum" to num(1000)),
        ), "productId", "quantity"),
    )), "lines")
}
