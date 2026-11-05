data class PagePosition(val kind: String, val offset: Int, val size: Int, val prefix: String) {
    fun cursor(): String = if (size == 2 && prefix.isEmpty()) "$kind:$offset"
        else "$kind:$offset:$size:$prefix"
}

fun paginatedList(kind: String, params: JsonObject, descriptors: List<JsonObject>): JsonObject {
    val requestedSize = if (params.contains("pageSize")) pageSize(params["pageSize"]) else null
    val requestedPrefix = if (params.contains("namePrefix")) namePrefix(params["namePrefix"]) else null
    val page = if (params.contains("cursor")) {
        val cursor = params["cursor"].requireString()
        val restored = restoreCursor(kind, cursor)
        if (requestedSize != null && requestedSize != restored.size) invalidParams()
        if (requestedPrefix != null && requestedPrefix != restored.prefix) invalidParams()
        restored
    } else PagePosition(kind, 0, requestedSize ?: 2, requestedPrefix ?: "")
    val matching = descriptors.filter { it["name"].requireString().startsWith(page.prefix) }
    if (params.contains("cursor") && (page.offset >= matching.size || page.offset % page.size != 0)) invalidParams()
    val entries = matching.drop(page.offset).take(page.size)
    val fields = mutableListOf<Pair<String, JsonValue>>(kind to JsonArray(entries))
    val nextOffset = page.offset + entries.size
    if (nextOffset < matching.size) fields += "nextCursor" to str(page.copy(offset = nextOffset).cursor())
    return cacheableResult(*fields.toTypedArray())
}

private fun restoreCursor(kind: String, cursor: String): PagePosition {
    val parts = cursor.split(':')
    if (parts.size != 2 && parts.size != 4) invalidParams()
    if (parts[0] != kind) invalidParams()
    val offset = parts[1].toIntOrNull() ?: invalidParams()
    if (offset <= 0) invalidParams()
    val size = if (parts.size == 2) 2 else parts[2].toIntOrNull() ?: invalidParams()
    if (size !in 1..5) invalidParams()
    val prefix = if (parts.size == 2) "" else namePrefix(str(parts[3]))
    val page = PagePosition(kind, offset, size, prefix)
    if (page.cursor() != cursor) invalidParams()
    return page
}

private fun pageSize(value: JsonValue?): Int {
    val size = (value as? JsonNumber)?.integerOrNull() ?: invalidParams()
    if (size !in 1..5) invalidParams()
    return size.toInt()
}

private fun namePrefix(value: JsonValue?): String {
    val prefix = value.requireString()
    if (prefix.length > 32 || prefix.any { it !in 'a'..'z' && it !in '0'..'9' && it != '_' && it != '-' }) invalidParams()
    return prefix
}
