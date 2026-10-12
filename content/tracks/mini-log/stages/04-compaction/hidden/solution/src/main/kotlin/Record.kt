class Record(val offset: Long, val message: Message, val timestamp: Long, val deleteHorizon: Long? = null) {
    fun withDeleteHorizon(horizon: Long): Record = Record(offset, message, timestamp, horizon)
}

class RecordLocation(val partition: Int, val offset: Long)

class OffsetRange(val first: Long, val last: Long)
