package lab

data class Event(val id: String, val entity: String, val version: Int, val value: Int, val delta: Int) {
    fun encode() = "$id|$entity|$version|$value|$delta"

    companion object {
        fun parse(text: String): Event {
            val fields = text.split('|')
            require(fields.size == 5)
            sqlId(fields[0])
            sqlId(fields[1])
            val version = fields[2].toInt()
            val value = fields[3].toInt()
            val delta = fields[4].toInt()
            require(version in 1..1_000 && value in -100_000..100_000 && delta in -1_000..1_000)
            return Event(fields[0], fields[1], version, value, delta)
        }
    }
}
