package cresvoid.command

import cresvoid.CresvoidError
import cresvoid.cli.Command
import cresvoid.cli.parseTrackArguments
import cresvoid.cli.stageFlag
import cresvoid.content.SolutionView
import cresvoid.content.numberedSources
import cresvoid.project.Project
import cresvoid.runner.ProcessRunner
import cresvoid.runner.starterDiff
import cresvoid.study.TrackStates
import cresvoid.terminal.Console
import cresvoid.terminal.Terminal
import cresvoid.terminal.bold
import cresvoid.terminal.dim

class SolutionCommand(
    private val project: Project,
    private val states: TrackStates,
    private val runner: ProcessRunner,
    private val console: Console,
    private val terminal: Terminal,
) : Command {
    override val name = "solution"
    override val usage = "cresvoid solution <트랙> [-stage N]"

    override fun run(args: List<String>) {
        val arguments = parseTrackArguments(this, args, listOf(stageFlag))
        val state = states.load(arguments.track)
        val track = state.track
        val view = track.kind.solutionView ?: throw CresvoidError("${track.kind.label} 트랙에는 참조 풀이가 없습니다")
        val stage = state.selectStage(arguments.stage)
        val text = when (view) {
            SolutionView.SOURCE -> numberedSources(stage.solutionDir).ifEmpty { "참조 풀이에 Kotlin 소스가 없습니다.\n" }
            SolutionView.STARTER_DIFF -> starterDiff(stage, runner).ifEmpty { "시작 코드와 참조 풀이가 같습니다.\n" }
        }
        console.println(bold("${track.id} ${stage.dir} · ${stage.title} 참조 풀이"))
        console.println(dim(project.rel(stage.solutionDir)))
        showText(console, terminal, text)
    }
}
