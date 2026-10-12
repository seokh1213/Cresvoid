package cresvoid.cli

import cresvoid.CresvoidError

sealed interface Flag {
    val name: String
}

class TextFlag(override val name: String) : Flag

class IntegerFlag(override val name: String) : Flag

class SwitchFlag(override val name: String) : Flag

val stageFlag = TextFlag("stage")

class HelpRequested(val usage: String) : RuntimeException()

class ParsedFlags(
    private val texts: Map<String, String>,
    private val integers: Map<String, Long>,
    private val switches: Set<String>,
) {
    fun text(name: String): String? = texts[name]?.ifEmpty { null }

    fun integer(name: String): Long? = integers[name]

    fun isSet(name: String): Boolean = name in switches
}

class TrackArguments(val track: String, private val flags: ParsedFlags) {
    val stage: String? get() = flags.text(stageFlag.name)
    val dir: String? get() = flags.text("dir")
    val lang: String? get() = flags.text("lang")
    val seed: Long? get() = flags.integer("seed")
}

private val helpWords = setOf("-h", "-help", "--help")

fun parseTrackArguments(command: Command, args: List<String>, flags: List<Flag>): TrackArguments {
    val first = args.firstOrNull()
    if (first in helpWords) throw HelpRequested(command.usage)
    val track = first?.takeUnless { it.startsWith("-") }
        ?: throw CresvoidError("트랙 이름이 필요합니다. 예: cresvoid ${command.name} search-engine")
    return TrackArguments(track, parseFlags(command, args.drop(1), flags))
}

fun parseFlags(command: Command, args: List<String>, flags: List<Flag>): ParsedFlags {
    val flagsByName = flags.associateBy { it.name }
    val texts = mutableMapOf<String, String>()
    val integers = mutableMapOf<String, Long>()
    val switches = mutableSetOf<String>()
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        if (argument == "--") {
            index++
            break
        }
        if (argument.length < 2 || !argument.startsWith("-")) break
        if (argument in helpWords) throw HelpRequested(command.usage)
        index++
        val body = argument.removePrefix("-").removePrefix("-")
        val name = body.substringBefore('=')
        val inlineValue = if ('=' in body) body.substringAfter('=') else null
        val flag = flagsByName[name] ?: throw CresvoidError("모르는 옵션입니다: $argument\n사용법: ${command.usage}")
        fun value(): String = inlineValue ?: args.getOrNull(index)?.also { index++ }
            ?: throw CresvoidError("-$name 에 값이 필요합니다\n사용법: ${command.usage}")
        when (flag) {
            is SwitchFlag -> {
                if (inlineValue != null) throw CresvoidError("-$name 옵션은 값을 받지 않습니다")
                switches += name
            }
            is TextFlag -> texts[name] = value()
            is IntegerFlag -> {
                val text = value()
                integers[name] = text.toLongOrNull() ?: throw CresvoidError("-$name 에는 정수를 씁니다: $text")
            }
        }
    }
    val remaining = args.drop(index)
    if (remaining.isNotEmpty()) throw CresvoidError("알 수 없는 인자입니다: ${remaining.joinToString(" ")}")
    return ParsedFlags(texts, integers, switches)
}
