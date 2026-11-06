package lab

fun recordException(action: () -> Unit): String = try {
    action()
    "none"
} catch (exception: Exception) {
    exception.javaClass.simpleName
}
