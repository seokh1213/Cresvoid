class Record(val offset: Long, val message: Message, val timestamp: Long)

class RecordLocation(val partition: Int, val offset: Long)
