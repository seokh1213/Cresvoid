package cresvoid.content

import cresvoid.CresvoidError
import cresvoid.json.JsonFields
import cresvoid.project.Project
import cresvoid.terminal.quoted
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class ContentLibrary(private val project: Project) {
    fun trackIds(): List<String> =
        try {
            Files.list(project.tracksDir).use { entries ->
                entries.filter { it.resolve("track.json").isRegularFile() }.map { it.name }.toList()
            }.sorted()
        } catch (_: IOException) {
            emptyList()
        }

    fun track(id: String): Track {
        val ids = trackIds()
        if (id !in ids) throw CresvoidError("없는 트랙입니다: $id. 있는 트랙: ${ids.joinToString(", ")}")
        return load(id)
    }

    fun tracks(): List<Track> = trackIds().map(::load)

    fun starterLanguages(track: Track): List<String> =
        try {
            Files.list(track.startersDir).use { entries -> entries.filter { it.isDirectory() }.map { it.name }.toList() }.sorted()
        } catch (_: IOException) {
            emptyList()
        }

    private fun load(id: String): Track {
        val path = project.tracksDir.resolve(id)
        if (project.practiceDir.resolve(id) == project.progressDir) {
            throw CresvoidError("${project.rel(path)}: 트랙 id 로 $id 는 쓸 수 없습니다. ${project.rel(project.progressDir)} 는 진행 기록 디렉터리입니다")
        }
        val definitionPath = path.resolve("track.json")
        val definition = readContentJson(definitionPath, ::decodeTrackDefinition)
        val kind = definition.validatedKind(where = project.rel(definitionPath))
        val stages = definition.stages.mapIndexed { index, stage ->
            val stagePath = path.resolve("stages").resolve(stage.dir)
            Stage(
                number = index + 1,
                dir = stage.dir,
                title = stage.title,
                targetMinutes = stage.targetMinutes,
                path = stagePath,
                documentPath = stagePath.resolve(kind.documentName),
            )
        }
        return Track(id, path, definition.title, kind, definition.summary, stages)
    }
}

private class TrackDefinition(
    val title: String,
    val kind: String,
    val summary: String,
    val stages: List<StageDefinition>,
)

private class StageDefinition(
    val dir: String,
    val title: String,
    val targetMinutes: Int,
)

private fun decodeTrackDefinition(fields: JsonFields) = TrackDefinition(
    title = fields.string("title"),
    kind = fields.string("kind"),
    summary = fields.string("summary"),
    stages = fields.objectList("stages") { stage ->
        StageDefinition(
            dir = stage.string("dir"),
            title = stage.string("title"),
            targetMinutes = stage.optionalInt("target_minutes") ?: 0,
        )
    },
)

private fun TrackDefinition.validatedKind(where: String): TrackKind {
    fun reject(problem: String): Nothing = throw CresvoidError("$where: $problem")
    if (title.isBlank()) reject("title 이 비어 있습니다")
    val trackKind = TrackKind.byId(kind) ?: reject("kind 는 ${TrackKind.all.joinToString(", ") { it.id }} 가운데 하나여야 합니다")
    if (summary.isBlank()) reject("summary 가 비어 있습니다")
    if (stages.isEmpty()) reject("stages 가 비어 있습니다")
    val seen = mutableSetOf<String>()
    stages.forEachIndexed { index, stage ->
        if (stage.dir.isEmpty() || '/' in stage.dir || stage.dir == "." || stage.dir == "..") {
            reject("${index + 1}번째 단계의 dir 은 디렉터리 이름 하나여야 합니다: ${quoted(stage.dir)}")
        }
        if (!seen.add(stage.dir)) reject("단계 dir 이 겹칩니다: ${stage.dir}")
        if (stage.title.isBlank()) reject("단계 title 이 비어 있습니다: ${stage.dir}")
        if (stage.targetMinutes < 0) reject("target_minutes 는 음수일 수 없습니다: ${stage.dir}")
    }
    return trackKind
}
