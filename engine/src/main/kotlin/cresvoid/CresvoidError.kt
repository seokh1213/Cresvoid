package cresvoid

open class CresvoidError(override val message: String) : RuntimeException(message)

class FailureShown : RuntimeException()
