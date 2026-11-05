data class Repair(val key: String, val live: List<String>, val copy: List<String>, val missing: Int)

fun List<String>.asNodeList(): String = if (isEmpty()) "-" else joinToString(",")
