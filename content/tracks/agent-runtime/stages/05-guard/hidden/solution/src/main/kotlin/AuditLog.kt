val AUDIT_LAST_RANGE = 1..1_000_000

sealed interface AuditEntry {
    val toolName: String

    data class Confirmed(val confirmation: Confirmation) : AuditEntry {
        override val toolName: String get() = confirmation.tool
    }

    data class Ran(val use: ToolUseBlock) : AuditEntry {
        override val toolName: String get() = use.name
    }

    data class Denied(val use: ToolUseBlock, val reason: DenialReason) : AuditEntry {
        override val toolName: String get() = use.name
    }

    data class Expired(val confirmation: Confirmation) : AuditEntry {
        override val toolName: String get() = confirmation.tool
    }
}

class NumberedEntry(val number: Int, val entry: AuditEntry)

class AuditPage(val matchingCount: Int, val entries: List<NumberedEntry>)

class AuditLog {
    private val entries = mutableListOf<NumberedEntry>()
    private val entriesByTool = HashMap<String, MutableList<NumberedEntry>>()

    fun append(entry: AuditEntry) {
        val numbered = NumberedEntry(entries.size + 1, entry)
        entries += numbered
        entriesByTool.getOrPut(entry.toolName) { mutableListOf() } += numbered
    }

    fun query(tool: String?, last: Int?): AuditPage {
        val matching = if (tool == null) entries else entriesByTool[tool].orEmpty()
        return AuditPage(matching.size, if (last == null) matching else matching.takeLast(last))
    }
}
