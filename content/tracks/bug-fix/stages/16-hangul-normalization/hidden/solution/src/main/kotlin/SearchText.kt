import java.text.Normalizer

object SearchText {
    fun canonical(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)

    fun terms(text: String): Set<String> =
        asciiLowercase(text)
            .split(' ')
            .filter { it.isNotEmpty() }
            .toSet()

    fun hasContent(text: String): Boolean =
        text.any { it != ' ' }

    private fun asciiLowercase(text: String): String = buildString(text.length) {
        for (character in text) {
            append(
                if (character in 'A'..'Z') character + ('a' - 'A')
                else character
            )
        }
    }
}

data class SearchQuery(val terms: Set<String>) {
    companion object {
        fun from(text: String): SearchQuery =
            SearchQuery(SearchText.terms(SearchText.canonical(text)))
    }
}
