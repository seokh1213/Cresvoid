class Record(val offset: Long, val message: Message, val timestamp: Long)

class RecordLocation(val partition: Int, val offset: Long)

class OffsetRange(val first: Long, val last: Long)
