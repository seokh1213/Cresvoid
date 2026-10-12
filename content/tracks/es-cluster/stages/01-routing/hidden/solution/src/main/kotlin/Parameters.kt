fun parseParameters(arguments: List<String>, allowedNames: Set<String>): Map<String, String> {
    val parameters = HashMap<String, String>()
    for (argument in arguments) {
        val name = argument.substringBefore('=', missingDelimiterValue = "")
        if (name !in allowedNames || name in parameters) reject(ErrorCode.BAD_ARGUMENT)
        parameters[name] = argument.substringAfter('=')
    }
    return parameters
}

fun parseBoundedInt(text: String, format: Regex, max: Int): Int {
    if (!format.matches(text)) reject(ErrorCode.BAD_ARGUMENT)
    return text.toIntOrNull()?.takeIf { it <= max } ?: reject(ErrorCode.BAD_ARGUMENT)
}
