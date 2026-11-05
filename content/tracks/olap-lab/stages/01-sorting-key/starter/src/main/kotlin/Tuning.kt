object Tuning {
    val order = listOf("event_type", "event_time", "tenant")
    const val partition = "toYYYYMM(event_time)"
}
