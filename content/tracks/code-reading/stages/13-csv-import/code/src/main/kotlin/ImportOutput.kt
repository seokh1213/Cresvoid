fun ImportResult.render(): String = when (this) {
    is ImportResult.Invalid -> "ERROR $reason"
    is ImportResult.Completed -> {
        val successes = accepted.joinToString(";") { it.render() }
        val failures = rejected.joinToString(";") { "${it.line}:${it.reason}" }
        "OK=${accepted.size}[$successes] ERR=${rejected.size}[$failures]"
    }
}

private fun ImportedProduct.render(): String =
    "$line:${sku.quoted()}:${name.quoted()}:$price:$stock"

private fun String.quoted(): String = buildString {
    append('"')
    for (character in this@quoted) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}
