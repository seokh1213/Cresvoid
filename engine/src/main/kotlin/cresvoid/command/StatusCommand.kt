package cresvoid.command

import cresvoid.cli.Command
import cresvoid.content.ContentLibrary
import cresvoid.progress.ProgressRepository
import cresvoid.study.TrackStates
import cresvoid.study.nextStep
import cresvoid.terminal.Console
import cresvoid.terminal.bold
import cresvoid.terminal.dim
import cresvoid.terminal.displayWidth
import cresvoid.terminal.padRight
import java.time.Clock
import java.time.OffsetDateTime

class StatusCommand(
    private val library: ContentLibrary,
    private val states: TrackStates,
    private val progress: ProgressRepository,
    private val console: Console,
    private val clock: Clock,
) : Command {
    override val name = "status"
    override val usage = "cresvoid"

    override fun run(args: List<String>) {
        val tracks = library.tracks()
        val latest = progress.load()
        if (tracks.isEmpty()) {
            console.println("content/tracks 에 트랙이 없습니다.")
            return
        }
        val now = OffsetDateTime.now(clock)
        tracks.forEachIndexed { index, track ->
            if (index > 0) console.println()
            val state = states.of(track, latest)
            val view = StatusView(state, now)
            console.println("${bold(track.id)} ${track.title} ${dim("(${track.kind.label})")}")
            view.workspaceLines().forEach { console.println("  $it") }
            val dirWidth = track.stages.maxOf { displayWidth(it.dir) }
            val titleWidth = track.stages.maxOf { displayWidth(it.title) }
            for (stage in track.stages) {
                val marks = view.stageMarks(stage).joinToString("  ")
                console.println("  ${padRight(stage.dir, dirWidth)}  ${padRight(stage.title, titleWidth)}  $marks")
            }
            view.reviewLines().forEach { console.println("  $it") }
            console.println("  ${dim("다음:")} ${state.nextStep(now).describe(track)}")
        }
    }
}
