package cresvoid.command

import cresvoid.CresvoidError
import cresvoid.FailureShown
import cresvoid.cli.Command
import cresvoid.cli.IntegerFlag
import cresvoid.cli.TextFlag
import cresvoid.cli.parseTrackArguments
import cresvoid.cli.stageFlag
import cresvoid.content.Stage
import cresvoid.content.TestCase
import cresvoid.content.TestScope
import cresvoid.content.Track
import cresvoid.content.WorkspaceScope
import cresvoid.content.loadTestSuite
import cresvoid.progress.Progress
import cresvoid.progress.ProgressRepository
import cresvoid.progress.Updated
import cresvoid.project.Project
import cresvoid.project.isExecutableFile
import cresvoid.project.isSameDirectory
import cresvoid.project.resolveFromWorkingDirectory
import cresvoid.runner.CaseInput
import cresvoid.runner.Harness
import cresvoid.runner.ProcessRunner
import cresvoid.runner.Verdict
import cresvoid.runner.judge
import cresvoid.runner.referenceProgram
import cresvoid.study.TestGate
import cresvoid.study.TrackState
import cresvoid.study.TrackStates
import cresvoid.study.stagesToRun
import cresvoid.study.testGate
import cresvoid.terminal.Console
import cresvoid.terminal.bold
import cresvoid.terminal.dim
import cresvoid.terminal.formatElapsed
import cresvoid.terminal.formatRefs
import cresvoid.terminal.green
import cresvoid.terminal.red
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.io.path.isRegularFile
import kotlin.random.Random

class TestCommand(
    private val project: Project,
    private val states: TrackStates,
    private val progress: ProgressRepository,
    private val runner: ProcessRunner,
    private val console: Console,
    private val clock: Clock,
) : Command {
    override val name = "test"
    override val usage = "cresvoid test <트랙> [-stage N] [-dir 경로] [-seed S]"

    override fun run(args: List<String>) {
        val arguments = parseTrackArguments(this, args, listOf(stageFlag, TextFlag("dir"), IntegerFlag("seed")))
        val state = states.load(arguments.track)
        val target = state.selectStage(arguments.stage)
        val track = state.track
        val scope = track.kind.testScope
            ?: throw CresvoidError("${track.kind.label} 트랙에는 테스트가 없습니다. 문항을 푸세요: cresvoid quiz ${track.id}")
        val solutionDir = solutionDirFor(state, target, arguments.dir)
        checkGate(state, target)
        val seed = arguments.seed?.takeIf { it >= 0 } ?: Random.nextLong(1, 1_000_000)
        runner.stopOnInterrupt()
        TestSession(state, scope, target, solutionDir, seed).run()
    }

    private fun solutionDirFor(state: TrackState, target: Stage, override: String?): Path {
        val dir = if (override != null) {
            resolveFromWorkingDirectory(override)
        } else {
            val workspace = state.workspace(target)
                ?: throw CresvoidError("풀이 작업 공간이 없습니다. 먼저 만드세요: ${startCommand(state.track, target)}")
            project.abs(workspace)
        }
        if (!dir.resolve("run.sh").isExecutableFile()) {
            throw CresvoidError("실행할 수 있는 run.sh 가 없습니다: ${project.rel(dir)}")
        }
        return dir
    }

    private fun checkGate(state: TrackState, target: Stage) {
        when (val gate = state.testGate(target)) {
            TestGate.Open -> Unit
            is TestGate.QuizPending ->
                throw CresvoidError("${gate.stage.dir} 확인 문항을 먼저 마치세요: cresvoid quiz ${state.track.id} -stage ${gate.stage.number}")
        }
    }

    private inner class TestSession(
        private val state: TrackState,
        private val scope: TestScope,
        private val target: Stage,
        private val solutionDir: Path,
        private val seed: Long,
    ) {
        private val track: Track = state.track
        private val harness = Harness(runner)
        private val stages = track.stagesToRun(target)
        private val preparedReferences = mutableSetOf<Stage>()

        fun run() {
            console.println("${dim("풀이:")} ${project.rel(solutionDir)}")
            if (solutionDir.resolve("build.sh").isRegularFile()) console.println(dim("빌드 중…"))
            harness.buildFailure(solutionDir)?.let { failure ->
                console.println(red("✗ 빌드 실패"))
                console.println(failure)
                throw FailureShown()
            }
            val started = System.nanoTime()
            val total = stages.sumOf { runStage(it) }
            val now = OffsetDateTime.now(clock)
            val update = progress.updateWith { latest -> recordPass(latest, now) }
            val elapsed = formatElapsed(Duration.ofNanos(System.nanoTime() - started))
            console.println("\n${green("✓")} ${passedMessage()}. 테스트 ${total}개, $elapsed")
            printReviewed(update.progress, update.result)
            solutionLine(target)?.let(console::println)
            console.println(state.withProgress(update.progress).nextLine(now))
        }

        private fun passedMessage(): String = when (scope) {
            TestScope.CUMULATIVE -> "${target.dir} 까지 모두 통과했습니다"
            TestScope.TARGET_STAGE -> "${target.dir} 테스트를 모두 통과했습니다"
        }

        private fun runStage(stage: Stage): Int {
            val suite = loadTestSuite(stage)
            console.println("\n${bold(stage.dir)} ${stage.title}")
            var count = 0
            for (test in suite.tests) {
                if (test.gen != null) console.println(dim("  · ${test.name} 입력 생성 중…"))
                val cases = harness.inputs(stage, test, seedsFor(test))
                if (test.expected == null) prepareReference(stage)
                val timeout = suite.timeoutFor(test)
                var slowest = Duration.ZERO
                for (current in cases) {
                    val expected = harness.expected(stage, test, current.input)
                    val result = harness.run(solutionDir, current.input, timeout)
                    val verdict = judge(result, expected, timeout)
                    count++
                    slowest = maxOf(slowest, result.duration)
                    if (verdict is Verdict.Failed) {
                        reportFailure(stage, test, current, verdict)
                        throw FailureShown()
                    }
                }
                val suffix = if (test.gen == null) "" else {
                    dim(" (무작위 ${cases.size}회, 최대 ${formatElapsed(slowest)} / 제한 ${formatElapsed(timeout)})")
                }
                console.println("  ${green("✓")} ${test.name}$suffix")
            }
            return count
        }

        private fun prepareReference(stage: Stage) {
            if (!preparedReferences.add(stage)) return
            val reference = referenceProgram(stage)
            if (reference.dir.resolve("build.sh").isRegularFile()) console.println(dim("  · 참조 풀이 빌드 중…"))
            harness.buildFailure(reference.dir)?.let { throw CresvoidError(it) }
        }

        private fun seedsFor(test: TestCase): List<Long> = List(test.repeatCount) { index -> seed + index }

        private fun reportFailure(stage: Stage, test: TestCase, current: CaseInput, verdict: Verdict.Failed) {
            console.println("  ${red("✗")} ${test.name}")
            console.println("    ${verdict.reason}")
            console.println("    다시 읽을 곳: ${project.rel(stage.documentPath)} 의 ${formatRefs(test.refs)} 문단")
            console.println("    핵심 문장: ${test.quote}")
            if (current.generated) {
                console.println("    같은 입력으로 다시 실행: cresvoid test ${track.id} -stage ${target.number} -seed $seed")
            }
            solutionLine(stage)?.let { console.println("    $it") }
        }

        private fun solutionLine(stage: Stage): String? =
            if (track.kind.solutionView == null) null else "참조 풀이를 보려면: cresvoid solution ${track.id} -stage ${stage.number}"

        private fun recordPass(latest: Progress, now: OffsetDateTime): Updated<List<Stage>> {
            val passedDir = project.rel(solutionDir)
            val reviewed = stages.filter { stage ->
                val before = latest.stageOrEmpty(track.id, stage.dir)
                before.isReviewDue(now) && !isSameDirectory(project.abs(before.passedDir.orEmpty()), solutionDir)
            }
            val passed = stages.fold(latest) { current, stage ->
                current.updateStage(track.id, stage.dir) { before ->
                    when {
                        before.passedAt == null -> before.copy(passedAt = now, passedDir = passedDir)
                        stage in reviewed -> before.copy(reviewedAt = now)
                        else -> before
                    }
                }
            }
            return Updated(passed, reviewed)
        }

        private fun printReviewed(fresh: Progress, reviewed: List<Stage>) {
            val first = reviewed.firstOrNull() ?: return
            console.println("복습으로 기록했습니다: ${reviewed.joinToString(", ") { it.dir }}")
            val original = project.abs(fresh.stageOrEmpty(track.id, first.dir).passedDir.orEmpty())
            if (track.kind.workspaceScope == WorkspaceScope.TRACK && state.withProgress(fresh).currentStage != null) {
                console.println("처음 쓰던 작업 공간으로 돌아가려면: ${startCommand(track, null)} -dir $original")
            }
        }
    }
}
