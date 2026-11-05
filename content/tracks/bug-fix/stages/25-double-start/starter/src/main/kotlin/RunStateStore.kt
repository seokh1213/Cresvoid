interface RunStateStore {
    fun isRunning(): Boolean
    fun begin(): Boolean
    fun end()
}

class MemoryRunStateStore : RunStateStore {
    @Volatile
    private var running = false

    var afterRead: () -> Unit = {}

    override fun isRunning(): Boolean = running

    override fun begin(): Boolean {
        val observed = isRunning()
        afterRead()
        if (observed) return false
        running = true
        return true
    }

    override fun end() {
        running = false
    }
}
