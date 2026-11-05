enum class Priority(private val word: String, val busyCapPercent: Long) {
    CRITICAL("critical", 100),
    POST("post", 80),
    GET("get", 60),
    TEST("test", 40),
    ;

    companion object {
        fun ofWord(word: String): Priority? = entries.firstOrNull { it.word == word }
    }
}
