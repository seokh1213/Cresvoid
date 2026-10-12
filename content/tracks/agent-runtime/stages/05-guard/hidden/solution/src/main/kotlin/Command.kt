sealed interface Command {
    data class DefineTool(
        val name: String,
        val schema: JsonObject?,
        val sideEffect: Boolean,
        val level: PermissionLevel,
        val arguments: JsonObject,
    ) : Command

    data class SetFake(val tool: String, val input: JsonObject, val attempts: List<Attempt>) : Command

    data class AddReply(val reply: Reply) : Command

    data class Configure(val change: SettingsChange) : Command

    data object Reset : Command

    data class Ask(val text: String) : Command

    data class Confirm(val confirmation: Confirmation) : Command

    data class Audit(val tool: String?, val last: Int?) : Command
}

enum class CommandError(val code: String) {
    UNKNOWN_COMMAND("unknown_command"),
    BAD_JSON("bad_json"),
    BAD_FIELD("bad_field"),
}

sealed interface ParsedLine {
    data object Blank : ParsedLine

    data class Invalid(val error: CommandError) : ParsedLine

    data class Valid(val command: Command) : ParsedLine
}
