object Tuning {
    val order = listOf("tenant", "event_time", "event_type")
    const val partition = "toYYYYMM(event_time)"
}
