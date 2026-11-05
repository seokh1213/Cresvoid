data class UserContext(val user: String, val payload: ByteArray)

class BusinessFailure(val principal: String) : RuntimeException()

class RequestScope {
    private val current = ThreadLocal<UserContext>()

    fun handle(request: UserRequest): String {
        if (request.user != null) {
            current.set(UserContext(request.user, ByteArray(request.bytes)))
        }
        val principal = current.get()?.user ?: "anonymous"
        if (request.fail) throw BusinessFailure(principal)
        return principal
    }

    fun retainedBytes(): Int = current.get()?.payload?.size ?: 0
}
