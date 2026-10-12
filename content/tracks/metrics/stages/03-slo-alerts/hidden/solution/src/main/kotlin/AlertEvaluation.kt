class AlertTransition(val time: Long, val alert: String, val firing: Boolean) {
    fun line(): String = "$time $alert ${if (firing) "FIRING" else "RESOLVED"}"
}

fun alertTransitions(
    alertsByName: List<BurnRateAlert>,
    seriesBySlo: Map<Slo, SloSeries>,
    schedule: EvaluationSchedule,
): List<AlertTransition> {
    if (alertsByName.isEmpty()) return emptyList()
    val firing = BooleanArray(alertsByName.size)
    return buildList {
        for (time in schedule.times) {
            for ((index, alert) in alertsByName.withIndex()) {
                val conditionMet = alert.conditionMet(seriesBySlo.getValue(alert.slo), time)
                if (conditionMet == firing[index]) continue
                firing[index] = conditionMet
                add(AlertTransition(time, alert.name, conditionMet))
            }
        }
    }
}
