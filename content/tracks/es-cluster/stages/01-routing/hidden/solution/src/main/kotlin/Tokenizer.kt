private val TOKEN = Regex("[A-Za-z0-9]+")

fun tokenize(text: String): List<String> = TOKEN.findAll(text).map { it.value.lowercase() }.toList()
