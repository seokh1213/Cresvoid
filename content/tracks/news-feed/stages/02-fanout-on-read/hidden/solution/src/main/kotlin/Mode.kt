sealed interface Mode {
    data object Write : Mode
    data object Read : Mode
}

fun parseMode(name: String): Mode = when (name) {
    "WRITE" -> Mode.Write
    "READ" -> Mode.Read
    else -> error("Unsupported mode")
}
