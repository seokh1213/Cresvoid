package cresvoid.command

import cresvoid.CresvoidError
import cresvoid.cli.Command
import cresvoid.cli.TextFlag
import cresvoid.cli.TrackArguments
import cresvoid.cli.parseTrackArguments
import cresvoid.cli.stageFlag
import cresvoid.content.ContentLibrary
import cresvoid.content.Track
import cresvoid.content.WorkspaceScope
import cresvoid.progress.ProgressRepository
import cresvoid.project.Project
import cresvoid.project.copyTree
import cresvoid.project.hasEntries
import cresvoid.project.isExecutableFile
import cresvoid.project.resolveFromWorkingDirectory
import cresvoid.study.TrackState
import cresvoid.study.TrackStates
import cresvoid.terminal.Console
import java.nio.file.Path
import java.time.Clock
import java.time.OffsetDateTime
import kotlin.io.path.isDirectory

private enum class WorkspaceChoice(val message: String) {
    CREATED("작업 공간을 만들었습니다"),
    SELECTED("이미 있는 작업 공간을 고릅니다"),
    REOPENED("이전에 쓰던 작업 공간을 다시 고릅니다"),
}

class StartCommand(
    private val project: Project,
    private val library: ContentLibrary,
    private val states: TrackStates,
    private val progress: ProgressRepository,
    private val console: Console,
    private val clock: Clock,
) : Command {
    override val name = "start"
    override val usage = "cresvoid start <트랙> [-stage N] [-lang 언어] [-dir 경로]"

    override fun run(args: List<String>) {
        val arguments = parseTrackArguments(this, args, listOf(stageFlag, TextFlag("lang"), TextFlag("dir")))
        val state = states.load(arguments.track)
        val track = state.track
        when (track.kind.workspaceScope) {
            null -> throw CresvoidError("${track.kind.label} 트랙에는 작업 공간이 필요 없습니다. 문서를 읽으세요: cresvoid read ${track.id}")
            WorkspaceScope.TRACK -> startTrackWorkspace(state, arguments)
            WorkspaceScope.STAGE -> startStageWorkspace(state, arguments)
        }
    }

    private fun startTrackWorkspace(state: TrackState, arguments: TrackArguments) {
        val track = state.track
        if (arguments.stage != null) {
            throw CresvoidError("${track.kind.label} 트랙은 모든 단계가 작업 공간 하나를 씁니다. -stage 없이 실행하세요: cresvoid start ${track.id}")
        }
        val requestedLanguage = arguments.lang?.let { starterLanguage(track, it) }
        val target = arguments.dir?.let(::resolveFromWorkingDirectory) ?: project.practiceDir.resolve(track.id)
        val choice = prepareWorkspace(target, recorded = state.solutionDir, isDefaultPath = arguments.dir == null) {
            track.startersDir.resolve(requestedLanguage ?: starterLanguage(track, requested = null))
        }
        val workspace = project.rel(target)
        val latest = progress.update { it.updateTrack(track.id) { trackProgress -> trackProgress.copy(solutionDir = workspace) } }
        announceWorkspace(choice, workspace, startCommand(track, null))
        console.println("이제부터 테스트는 이 작업 공간에서 실행합니다.")
        console.println(state.withProgress(latest).nextLine(OffsetDateTime.now(clock)))
    }

    private fun startStageWorkspace(state: TrackState, arguments: TrackArguments) {
        val track = state.track
        if (arguments.lang != null) {
            throw CresvoidError("${track.kind.label} 트랙은 단계마다 시작 코드가 하나라 -lang 을 쓰지 않습니다")
        }
        val stage = state.selectStage(arguments.stage)
        val target = arguments.dir?.let(::resolveFromWorkingDirectory)
            ?: project.practiceDir.resolve(track.id).resolve(stage.dir)
        val recorded = state.stageProgress(stage).solutionDir
        val choice = prepareWorkspace(target, recorded, isDefaultPath = arguments.dir == null) {
            stage.starterDir.takeIf { it.isDirectory() }
                ?: throw CresvoidError("이 단계에는 시작 코드가 없습니다: ${project.rel(stage.starterDir)}")
        }
        val workspace = project.rel(target)
        val latest = progress.update { it.updateStage(track.id, stage.dir) { stageProgress -> stageProgress.copy(solutionDir = workspace) } }
        announceWorkspace(choice, workspace, startCommand(track, stage))
        console.println("이제부터 ${stage.dir} 테스트는 이 작업 공간에서 실행합니다.")
        console.println(state.withProgress(latest).nextLine(OffsetDateTime.now(clock)))
    }

    private fun prepareWorkspace(target: Path, recorded: String?, isDefaultPath: Boolean, starter: () -> Path): WorkspaceChoice {
        if (target.resolve("run.sh").isExecutableFile()) {
            val reopened = isDefaultPath && recorded != project.rel(target)
            return if (reopened) WorkspaceChoice.REOPENED else WorkspaceChoice.SELECTED
        }
        if (target.hasEntries()) {
            throw CresvoidError("비어 있지 않은 디렉터리라 작업 공간을 만들 수 없습니다. 다른 경로를 고르세요: ${project.rel(target)}")
        }
        copyTree(starter(), target)
        return WorkspaceChoice.CREATED
    }

    private fun announceWorkspace(choice: WorkspaceChoice, workspace: String, startCommand: String) {
        console.println("${choice.message}: $workspace")
        if (choice == WorkspaceChoice.REOPENED) console.println("처음부터 다시 풀려면: $startCommand -dir <새 경로>")
    }

    private fun starterLanguage(track: Track, requested: String?): String {
        val languages = library.starterLanguages(track)
        return when {
            requested != null && requested in languages -> requested
            requested != null -> throw CresvoidError("이 언어의 시작 코드가 없습니다: $requested. 있는 언어: ${languages.joinToString(", ")}")
            languages.size == 1 -> languages.single()
            languages.isEmpty() -> throw CresvoidError("이 트랙에는 시작 코드가 없습니다: ${track.id}")
            else -> throw CresvoidError("시작 코드 언어를 -lang 으로 고르세요. 있는 언어: ${languages.joinToString(", ")}")
        }
    }
}
