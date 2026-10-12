class EvaluationSchedule(start: Long, end: Long, interval: Long) {
    private val firstTime = (start + interval - 1) / interval * interval

    val times: LongProgression = firstTime..end step interval
    val count: Long = if (firstTime > end) 0 else (end - firstTime) / interval + 1
}
