data class Confirmation(val tool: String, val input: JsonObject)

class ConfirmationLedger {
    private var nextSerial = 0
    private val unused = LinkedHashMap<Int, Confirmation>()
    private val unusedSerialsByKey = HashMap<ToolInputKey, ArrayDeque<Int>>()

    fun add(confirmation: Confirmation) {
        val serial = nextSerial++
        unused[serial] = confirmation
        unusedSerialsByKey.getOrPut(ToolInputKey(confirmation.tool, confirmation.input)) { ArrayDeque() }.addLast(serial)
    }

    fun useMatching(use: ToolUseBlock): Boolean {
        val serial = unusedSerialsByKey[ToolInputKey(use.name, use.input)]?.removeFirstOrNull() ?: return false
        unused.remove(serial)
        return true
    }

    fun discardUnused(): List<Confirmation> {
        val discarded = unused.values.toList()
        unused.clear()
        unusedSerialsByKey.clear()
        return discarded
    }
}
