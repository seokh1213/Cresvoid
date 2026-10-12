private val WORD = Regex("[^ ]+")

class RequestLine(private val text: String) {
    private val words: List<MatchResult> = WORD.findAll(text).toList()

    val commandName: String = words.first().value

    val arguments: List<String> = words.drop(1).map { it.value }

    fun textAfterArgument(argumentIndex: Int): String =
        text.substring(words[argumentIndex + 1].range.last + 1).trimStart(' ')
}
