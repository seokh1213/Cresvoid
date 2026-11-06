package lab

fun recordException(action: () -> Unit): String = try {
    action()
    "none"
} catch (_: Exception) {
    "none"
}
