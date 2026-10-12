package cresvoid.command

import cresvoid.CresvoidError
import cresvoid.cli.Command
import cresvoid.cli.parseTrackArguments
import cresvoid.cli.stageFlag
import cresvoid.content.Stage
import cresvoid.content.Track
import cresvoid.content.numberedSources
import cresvoid.progress.ProgressRepository
import cresvoid.project.Project
import cresvoid.study.TrackState
import cresvoid.study.TrackStates
import cresvoid.terminal.Console
import cresvoid.terminal.Terminal
import cresvoid.terminal.bold
import cresvoid.terminal.dim
import java.nio.file.Files
import java.time.Clock
import java.time.OffsetDateTime
import kotlin.io.path.isRegularFile

class ReadCommand(
    private val project: Project,
    private val states: TrackStates,
    private val progress: ProgressRepository,
    private val console: Console,
    private val terminal: Terminal,
    private val clock: Clock,
) : Command {
    override val name = "read"
    override val usage = "cresvoid read <트랙> [-stage N]"

    override fun run(args: List<String>) {
        val arguments = parseTrackArguments(this, args, listOf(stageFlag))
        val state = states.load(arguments.track)
        val stage = state.selectStage(arguments.stage)
        val track = state.track
        val document = stage.documentPath
        if (!document.isRegularFile()) throw CresvoidError("문서가 아직 없습니다: ${project.rel(document)}")
        val code = if (track.kind.readsCode) stage.codeDir else null
        val documentBytes = Files.readAllBytes(document)
        val listing = code?.let(::numberedSources)
        markRead(track, stage)
        val target = if (stage.targetMinutes > 0) " · 목표 ${stage.targetMinutes}분" else ""
        console.println(bold("${track.id} ${stage.dir} · ${stage.title}$target"))
        console.println(dim(project.rel(document)))
        if (code != null) console.println(dim("코드: ${project.rel(code)}"))
        val paged = terminal.stdoutIsTerminal &&
            if (listing == null) terminal.page(document) else terminal.pageText(String(documentBytes, Charsets.UTF_8) + "\n" + listing)
        if (paged) {
            markRead(track, stage)
        } else {
            console.println()
            console.write(documentBytes)
            if (listing != null) {
                console.println()
                console.print(listing)
            }
        }
        console.println("\n" + afterReading(state, stage))
    }

    private fun afterReading(state: TrackState, stage: Stage): String {
        val track = state.track
        if (track.kind.quizRules != null) return "다 읽었으면 문항을 푸세요: cresvoid quiz ${track.id}${state.stageFlag(stage)}"
        return state.withProgress(progress.load()).nextLine(OffsetDateTime.now(clock))
    }

    private fun markRead(track: Track, stage: Stage) {
        val now = OffsetDateTime.now(clock)
        progress.update { latest -> latest.updateStage(track.id, stage.dir) { it.markedRead(now) } }
    }
}
