object Tuning {
    val order = listOf("tenant", "event_time", "event_type")
    const val partition = "toDate(event_time)"
}
