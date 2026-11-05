class RemoteGateway {
    private class RemoteRecord {
        val scripts = ArrayDeque<ScriptResult>()
        var state = RemoteState.NONE
        var calls = 0L
        var queries = 0L
    }

    private val records = mutableMapOf<String, RemoteRecord>()

    fun enqueue(key: String, result: ScriptResult): String {
        records.getOrPut(key, ::RemoteRecord).scripts.addLast(result)
        return "OK PSP $key"
    }

    fun call(key: String): Observation? {
        val record = records[key] ?: return null
        val result = when (record.state) {
            RemoteState.SUCCEEDED -> ScriptResult.OK
            RemoteState.FAILED -> ScriptResult.DECLINE
            RemoteState.NONE -> record.scripts.removeFirstOrNull() ?: return null
        }
        record.calls++
        return when (result) {
            ScriptResult.OK, ScriptResult.TIMEOUT_OK -> {
                record.state = RemoteState.SUCCEEDED
                if (result == ScriptResult.OK) Observation.OK else Observation.UNKNOWN
            }
            ScriptResult.DECLINE -> {
                record.state = RemoteState.FAILED
                Observation.DECLINE
            }
            ScriptResult.RETRY -> Observation.RETRY
            ScriptResult.TIMEOUT_NONE -> Observation.UNKNOWN
        }
    }

    fun query(key: String): Observation {
        val record = records.getOrPut(key, ::RemoteRecord)
        record.queries++
        return when (record.state) {
            RemoteState.SUCCEEDED -> Observation.OK
            RemoteState.FAILED -> Observation.DECLINE
            RemoteState.NONE -> Observation.ABSENT
        }
    }

    fun describe(key: String): String {
        val record = records[key] ?: return "REMOTE $key NONE 0 0 0"
        val effects = if (record.state == RemoteState.SUCCEEDED) 1 else 0
        return "REMOTE $key ${record.state} ${record.calls} ${record.queries} $effects"
    }
}
