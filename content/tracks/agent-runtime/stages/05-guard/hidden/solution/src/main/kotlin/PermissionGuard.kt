enum class PermissionLevel(val keyword: String) {
    READ("read"),
    WRITE("write"),
    IRREVERSIBLE("irreversible"),
    ;

    val needsPermission: Boolean get() = this != READ

    companion object {
        fun fromKeyword(keyword: String): PermissionLevel? = entries.firstOrNull { it.keyword == keyword }
    }
}

enum class DenialReason(val keyword: String, private val contentPrefix: String) {
    NOT_ALLOWED("not_allowed", "not allowed: "),
    UNCONFIRMED("unconfirmed", "confirmation required: "),
    ;

    fun resultContent(toolName: String): String = contentPrefix + toolName
}

sealed interface PermissionVerdict {
    data object Granted : PermissionVerdict

    data class Denied(val reason: DenialReason) : PermissionVerdict
}

class PermissionGuard(private val auditLog: AuditLog) {
    private val confirmations = ConfirmationLedger()

    fun confirm(confirmation: Confirmation) {
        confirmations.add(confirmation)
        auditLog.append(AuditEntry.Confirmed(confirmation))
    }

    fun admit(call: ToolCall, allowed: Set<String>): ToolCall {
        if (call !is ToolCall.Runnable) return call
        return when (val verdict = judge(call, allowed)) {
            PermissionVerdict.Granted -> {
                auditLog.append(AuditEntry.Ran(call.use))
                call
            }
            is PermissionVerdict.Denied -> {
                auditLog.append(AuditEntry.Denied(call.use, verdict.reason))
                ToolCall.Skipped(ToolResultBlock(call.use.id, isError = true, content = verdict.reason.resultContent(call.use.name)))
            }
        }
    }

    fun discardUnusedConfirmations() {
        confirmations.discardUnused().forEach { auditLog.append(AuditEntry.Expired(it)) }
    }

    private fun judge(call: ToolCall.Runnable, allowed: Set<String>): PermissionVerdict = when (call.level) {
        PermissionLevel.READ -> PermissionVerdict.Granted
        PermissionLevel.WRITE ->
            if (call.use.name in allowed) PermissionVerdict.Granted else PermissionVerdict.Denied(DenialReason.NOT_ALLOWED)
        PermissionLevel.IRREVERSIBLE -> when {
            call.use.name !in allowed -> PermissionVerdict.Denied(DenialReason.NOT_ALLOWED)
            confirmations.useMatching(call.use) -> PermissionVerdict.Granted
            else -> PermissionVerdict.Denied(DenialReason.UNCONFIRMED)
        }
    }
}
