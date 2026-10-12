package cresvoid.terminal

private val colorEnabled: Boolean by lazy {
    TerminalProbe.stdoutIsTerminal && System.getenv("NO_COLOR").isNullOrEmpty()
}

private val escape = Char(27)

private fun paint(code: String, text: String): String =
    if (colorEnabled) "$escape[${code}m$text$escape[0m" else text

fun green(text: String): String = paint("32", text)

fun red(text: String): String = paint("31", text)

fun yellow(text: String): String = paint("33", text)

fun dim(text: String): String = paint("2", text)

fun bold(text: String): String = paint("1", text)
