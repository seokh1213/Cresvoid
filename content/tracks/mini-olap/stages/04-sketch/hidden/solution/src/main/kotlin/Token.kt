sealed interface Token {
    data class Word(val text: String) : Token

    data class Integer(val text: String) : Token

    data class Text(val value: String) : Token

    data class Symbol(val text: String) : Token
}
