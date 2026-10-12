package cresvoid.progress

import cresvoid.json.JsonArray
import cresvoid.json.JsonFields
import cresvoid.json.JsonNumber
import cresvoid.json.JsonObject
import cresvoid.json.JsonString
import cresvoid.json.JsonValue
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal fun decodeProgress(fields: JsonFields) = Progress(
    tracks = fields.optionalObjectMap("tracks") { track ->
        TrackProgress(
            solutionDir = track.optionalString("solution_dir")?.ifEmpty { null },
            stages = track.optionalObjectMap("stages", ::decodeStageProgress).orEmpty(),
        )
    }.orEmpty(),
)

private fun decodeStageProgress(fields: JsonFields) = StageProgress(
    solutionDir = fields.optionalString("solution_dir")?.ifEmpty { null },
    openedAt = fields.optionalTime("opened_at"),
    lastReadAt = fields.optionalTime("last_read_at"),
    quiz = fields.optionalObject("quiz", ::decodeQuizProgress) ?: QuizProgress(),
    passedAt = fields.optionalTime("passed_at"),
    passedDir = fields.optionalString("passed_dir")?.ifEmpty { null },
    reviewedAt = fields.optionalTime("reviewed_at"),
)

private fun decodeQuizProgress(fields: JsonFields) = QuizProgress(
    resolved = fields.optionalStringList("resolved").orEmpty(),
    revealed = fields.optionalStringList("revealed").orEmpty(),
    wrong = fields.optionalIntMap("wrong").orEmpty(),
    firstCorrect = fields.optionalInt("first_correct"),
    firstTotal = fields.optionalInt("first_total") ?: 0,
    firstSeconds = fields.optionalInt("first_seconds"),
    completedAt = fields.optionalTime("completed_at"),
)

internal fun encodeProgress(progress: Progress) = jsonObject(
    "tracks" to sortedJsonObject(progress.tracks) { track ->
        jsonObject(
            "solution_dir" to track.solutionDir?.let(::JsonString),
            "stages" to track.stages.takeIf { it.isNotEmpty() }?.let { stages -> sortedJsonObject(stages, ::encodeStageProgress) },
        )
    },
)

private fun encodeStageProgress(stage: StageProgress) = jsonObject(
    "solution_dir" to stage.solutionDir?.let(::JsonString),
    "opened_at" to stage.openedAt?.let(::encodeTime),
    "last_read_at" to stage.lastReadAt?.let(::encodeTime),
    "quiz" to encodeQuizProgress(stage.quiz),
    "passed_at" to stage.passedAt?.let(::encodeTime),
    "passed_dir" to stage.passedDir?.let(::JsonString),
    "reviewed_at" to stage.reviewedAt?.let(::encodeTime),
)

private fun encodeQuizProgress(quiz: QuizProgress) = jsonObject(
    "resolved" to quiz.resolved.takeIf { it.isNotEmpty() }?.let(::encodeStrings),
    "revealed" to quiz.revealed.takeIf { it.isNotEmpty() }?.let(::encodeStrings),
    "wrong" to quiz.wrong.takeIf { it.isNotEmpty() }?.let { wrong -> sortedJsonObject(wrong) { JsonNumber(it.toString()) } },
    "first_correct" to quiz.firstCorrect?.let { JsonNumber(it.toString()) },
    "first_total" to quiz.firstTotal.takeIf { it != 0 }?.let { JsonNumber(it.toString()) },
    "first_seconds" to quiz.firstSeconds?.let { JsonNumber(it.toString()) },
    "completed_at" to quiz.completedAt?.let(::encodeTime),
)

private fun jsonObject(vararg fields: Pair<String, JsonValue?>): JsonObject =
    JsonObject(fields.mapNotNull { (name, value) -> value?.let { name to it } }.toMap())

private fun <T> sortedJsonObject(entries: Map<String, T>, encode: (T) -> JsonValue): JsonObject =
    JsonObject(entries.toSortedMap().mapValues { (_, value) -> encode(value) })

private fun encodeStrings(values: List<String>) = JsonArray(values.map(::JsonString))

private fun encodeTime(time: OffsetDateTime) = JsonString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time))
