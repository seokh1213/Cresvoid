package cresvoid.command

import cresvoid.CresvoidError
import cresvoid.cli.Command
import cresvoid.cli.SwitchFlag
import cresvoid.cli.parseFlags
import cresvoid.cli.stageFlag
import cresvoid.content.ContentLibrary
import cresvoid.progress.Progress
import cresvoid.progress.ProgressRepository
import cresvoid.progress.ResetScope
import cresvoid.progress.StageProgress
import cresvoid.progress.StoredProgress
import cresvoid.progress.selectedBy
import cresvoid.progress.without
import cresvoid.progress.workspaceSelections
import cresvoid.project.Project
import cresvoid.project.isExecutableFile
import cresvoid.study.TrackStates
import cresvoid.terminal.Console
import cresvoid.terminal.Terminal
import cresvoid.terminal.dim
import java.time.Clock
import java.time.OffsetDateTime

private class ResetRequest(val trackId: String?, val stage: String?, val confirmed: Boolean) {
    val commandLine: String get() = listOfNotNull("cresvoid reset", trackId, stage?.let { "-stage $it" }).joinToString(" ")
}

private class RecordsToDelete(val lines: List<String>, val workspaces: List<String>)

class ResetCommand(
    private val project: Project,
    private val library: ContentLibrary,
    private val states: TrackStates,
    private val progress: ProgressRepository,
    private val console: Console,
    private val terminal: Terminal,
    private val clock: Clock,
) : Command {
    override val name = "reset"
    override val usage = "cresvoid reset [트랙] [-stage N] [-yes]"

    override fun run(args: List<String>) {
        val request = parseRequest(args)
        val scope = resetScope(request)
        val records = recordsToDelete(scope)
        if (records == null) {
            console.println("지울 진행 기록이 없습니다.")
            return
        }
        console.println("지울 진행 기록:")
        records.lines.forEach { console.println("  $it") }
        console.println(dim("작업 공간 파일은 지우지 않습니다."))
        if (!request.confirmed && !askConfirmation(request)) {
            console.println("지우지 않았습니다.")
            return
        }
        val backup = when (scope) {
            ResetScope.AllTracks -> progress.clearKeepingBackup()
            else -> progress.replaceKeepingBackup { it.without(scope) }
        }
        if (backup == null) {
            console.println("지울 진행 기록이 없습니다.")
            return
        }
        console.println("지웠습니다. 지우기 전 사본: ${project.rel(backup)}")
        printNextSteps(scope, records.workspaces)
    }

    private fun parseRequest(args: List<String>): ResetRequest {
        val trackId = args.firstOrNull()?.takeUnless { it.startsWith("-") }
        val flags = parseFlags(this, if (trackId == null) args else args.drop(1), listOf(stageFlag, SwitchFlag("yes")))
        return ResetRequest(trackId, flags.text(stageFlag.name), flags.isSet("yes"))
    }

    private fun resetScope(request: ResetRequest): ResetScope {
        val trackId = request.trackId
        if (trackId == null) {
            if (request.stage != null) throw CresvoidError("-stage 는 트랙과 함께 씁니다. 예: cresvoid reset search-engine -stage 1")
            return ResetScope.AllTracks
        }
        val track = library.track(trackId)
        val selector = request.stage ?: return ResetScope.WholeTrack(track.id)
        return ResetScope.OneStage(track.id, track.stageBySelector(selector).dir)
    }

    private fun recordsToDelete(scope: ResetScope): RecordsToDelete? {
        val stored = if (scope == ResetScope.AllTracks) progress.read() else StoredProgress.Readable(progress.load())
        val latest = when (stored) {
            is StoredProgress.Unreadable ->
                return RecordsToDelete(listOf("읽을 수 없는 진행 기록 전체 (${stored.problem})"), workspaces = emptyList())
            is StoredProgress.Readable -> stored.progress
        }
        if (latest.without(scope) == latest) return null
        val selected = latest.selectedBy(scope)
        return RecordsToDelete(describeRecords(selected), selected.workspaceSelections())
    }

    private fun askConfirmation(request: ResetRequest): Boolean {
        if (!terminal.stdinIsTerminal) {
            throw CresvoidError("표준 입력이 터미널이 아니라 확인을 받을 수 없습니다. 지우려면 -yes 를 붙이세요: ${request.commandLine} -yes")
        }
        console.print("지울까요? [y/N] ")
        val answer = terminal.readLine()?.trim()?.lowercase()
        return answer == "y" || answer == "yes"
    }

    private fun printNextSteps(scope: ResetScope, removedWorkspaces: List<String>) {
        val workspacesLeft = removedWorkspaces.any { project.abs(it).resolve("run.sh").isExecutableFile() }
        val trackId = scope.trackId
        if (trackId == null) {
            if (workspacesLeft) {
                console.println("작업 공간 파일은 그대로 있어 cresvoid start 는 그 작업 공간을 다시 고릅니다. 처음부터 다시 풀려면 -dir 로 새 경로를 주세요.")
            }
            return
        }
        val state = states.load(trackId)
        if (workspacesLeft) {
            val stage = state.selectStage((scope as? ResetScope.OneStage)?.stageDir)
            console.println("작업 공간 파일은 그대로 있어 cresvoid start 는 그 작업 공간을 다시 고릅니다. 처음부터 다시 풀려면: ${startCommand(state.track, stage)} -dir <새 경로>")
        }
        console.println(state.nextLine(OffsetDateTime.now(clock)))
    }
}

fun describeRecords(progress: Progress): List<String> =
    progress.tracks.toSortedMap().flatMap { (trackId, track) ->
        listOfNotNull(track.solutionDir?.let { "$trackId: 작업 공간 선택($it)" }) +
            track.stages.toSortedMap().map { (stageDir, stage) -> "$trackId $stageDir: ${describeStage(stage)}" }
    }

private fun describeStage(stage: StageProgress): String {
    val quiz = stage.quiz
    val records = listOfNotNull(
        stage.solutionDir?.let { "작업 공간 선택($it)" },
        "읽기".takeIf { stage.openedAt != null },
        quiz.firstCorrect?.let { "문항 첫 시도 $it/${quiz.firstTotal}" },
        "문항 완료".takeIf { quiz.completedAt != null },
        stage.passedAt?.let { "통과 ${it.toLocalDate()}" },
        stage.reviewedAt?.let { "복습 ${it.toLocalDate()}" },
    )
    return records.joinToString(", ").ifEmpty { "빈 기록" }
}
