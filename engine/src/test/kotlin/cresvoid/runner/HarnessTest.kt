package cresvoid.runner

import cresvoid.content.TestCase
import cresvoid.stageIn
import cresvoid.withTempDir
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.time.Duration
import kotlin.io.path.Path

private fun result(stdout: String = "", stderr: String = "", exitCode: Int = 0, timedOut: Boolean = false) = ProcessResult(
    stdout = stdout.toByteArray(),
    stderr = stderr.toByteArray(),
    exitCode = exitCode,
    timedOut = timedOut,
    truncated = false,
    duration = Duration.ZERO,
    startError = null,
)

private val runner = SystemProcessRunner()

class HarnessTest : FunSpec({
    test("처음 달라지는 줄을 찾는다") {
        val expected = listOf("1 2.5000", "3")
        withClue("같은 출력") { firstDifference(expected, listOf("1 2.5000", "3")) shouldBe null }
        withClue("점수 자릿수") { firstDifference(expected, listOf("1 2.5001", "3")) shouldBe 0 }
        withClue("두 칸 공백") { firstDifference(expected, listOf("1  2.5000", "3")) shouldBe 0 }
        withClue("빠진 줄") { firstDifference(expected, listOf("1 2.5000")) shouldBe 1 }
    }
    test("출력은 줄 끝 공백과 끝의 빈 줄만 지운다") {
        withClue("정규화") { normalizeOutput("a  \r\nb\t\n\n\n".toByteArray()) shouldBe listOf("a", "b") }
        withClue("앞 공백과 가운데 빈 줄") { normalizeOutput(" a\n\nb".toByteArray()) shouldBe listOf(" a", "", "b") }
    }
    test("입력 줄은 마지막 줄 뒤에도 줄바꿈을 붙인다") {
        withClue("입력") { String(joinInput(listOf("a", "b"))) shouldBe "a\nb\n" }
        withClue("빈 입력") { joinInput(emptyList()).size shouldBe 0 }
    }
    test("판정은 시작 실패, 시간 초과, 종료 코드, 출력 순으로 본다") {
        val timeout = Duration.ofMillis(2500)
        withClue("시간 초과") { (judge(result(timedOut = true, exitCode = 137), listOf(), timeout) as Verdict.Failed).reason shouldBe "제한 시간 2.5초를 넘었습니다" }
        withClue("종료 코드") { (judge(result(exitCode = 3), listOf(), timeout) as Verdict.Failed).kind shouldBe FailureKind.EXIT }
        val output = judge(result(stdout = "A\nC\n"), listOf("A", "B"), timeout) as Verdict.Failed
        withClue("출력 차이") { (output.kind to output.diffIndex) shouldBe (FailureKind.OUTPUT to 1) }
        withClue("통과") { judge(result(stdout = "A\nB  \n\n"), listOf("A", "B"), timeout) shouldBe Verdict.Passed }
    }
    test("프로세스 실행은 입력을 넘기고 출력과 종료 코드를 모은다") {
        val run = runner.run(Path("/"), listOf("sh", "-c", "tr a-z A-Z; echo warn >&2; exit 4"), "abc\n".toByteArray(), Duration.ofSeconds(10))
        withClue("표준 출력") { String(run.stdout) shouldBe "ABC\n" }
        withClue("표준 오류") { String(run.stderr) shouldBe "warn\n" }
        withClue("종료 코드") { run.exitCode shouldBe 4 }
        val killed = runner.run(Path("/"), listOf("sh", "-c", "kill -9 $$"), ByteArray(0), Duration.ofSeconds(10))
        withClue("신호로 끝난 종료 코드") { killed.exitCode shouldBe 137 }
        val missing = runner.run(Path("/"), listOf("/nonexistent/run.sh"), ByteArray(0), Duration.ofSeconds(10))
        withClue("없는 프로그램의 종료 코드") { missing.exitCode shouldBe 127 }
        withClue("없는 프로그램 안내") { String(missing.stderr) shouldContain "/nonexistent/run.sh: No such file or directory" }
    }
    test("자식은 새 프로세스 그룹의 첫 프로세스로 실행한다") {
        val run = runner.run(Path("/"), listOf("sh", "-c", "echo $$; ps -o pgid= -p $$; ps -o pgid= -p \$PPID"), ByteArray(0), Duration.ofSeconds(10))
        val (pid, group, parentGroup) = normalizeOutput(run.stdout).map { it.trim() }
        withClue("자식의 프로세스 그룹") { group shouldBe pid }
        withClue("엔진 쪽 프로세스 그룹과 달라야 한다: $group, $parentGroup") { (group != parentGroup) shouldBe true }
    }
    test("표준 출력이 한도를 넘으면 잘렸다고 표시한다") {
        val run = runner.run(Path("/"), listOf("sh", "-c", "head -c ${STDOUT_LIMIT + 10} /dev/zero"), ByteArray(0), Duration.ofSeconds(30))
        withClue("잘림 표시") { run.truncated shouldBe true }
        withClue("모은 크기") { run.stdout.size shouldBe STDOUT_LIMIT }
        withClue("판정") { (judge(run, listOf(), Duration.ofSeconds(1)) as Verdict.Failed).kind shouldBe FailureKind.TRUNCATED }
    }
    test("제한 시간을 넘기면 자손 프로세스까지 끝낸다") {
        withTempDir { dir ->
            val marker = "41.5${System.nanoTime() % 1000}"
            val run = runner.run(dir, listOf("sh", "-c", "sleep $marker & sleep $marker; echo never"), ByteArray(0), Duration.ofMillis(500))
            withClue("시간 초과") { run.timedOut shouldBe true }
            withClue("바로 끝나야 한다: ${run.duration}") { (run.duration < Duration.ofSeconds(5)) shouldBe true }
            withClue("남은 sleep 이 없어야 한다") { !processExists(marker) shouldBe true }
        }
    }
    test("프로세스가 끝난 뒤 남은 자손도 끝낸다") {
        withTempDir { dir ->
            val lingering = "42.5${System.nanoTime() % 1000}"
            val run = runner.run(dir, listOf("sh", "-c", "sleep $lingering >/dev/null 2>&1 & sleep 0.3; echo done"), ByteArray(0), Duration.ofSeconds(10))
            withClue("표준 출력") { String(run.stdout) shouldBe "done\n" }
            withClue("남은 sleep 이 없어야 한다") { !processExists(lingering) shouldBe true }
            val orphaned = "44.5${System.nanoTime() % 1000}"
            val quick = runner.run(dir, listOf("sh", "-c", "sleep $orphaned >/dev/null 2>&1 & exec echo done"), ByteArray(0), Duration.ofSeconds(10))
            withClue("바로 끝난 부모의 표준 출력") { String(quick.stdout) shouldBe "done\n" }
            withClue("부모가 바로 끝나도 남은 sleep 이 없어야 한다") { !processExists(orphaned) shouldBe true }
        }
    }
    test("생성기 출력 끝에 줄바꿈이 없으면 붙이고 같은 seed 는 한 번만 실행한다") {
        withTempDir { dir ->
            val counter = dir.resolve("count")
            val stage = stageIn(dir.resolve("01"), "hidden/gen.sh" to "echo run >> '$counter'; printf 'seed %s' \"\$2\"")
            val test = TestCase("gen", listOf("1"), "q", input = null, expected = null, gen = listOf("sh", "gen.sh"), repeat = 2, timeoutSec = null)
            val harness = Harness(runner)
            val first = harness.inputs(stage, test, listOf(5L, 6L))
            harness.inputs(stage, test, listOf(5L))
            withClue("생성한 입력") { (first.map { String(it.input) }) shouldBe listOf("seed 5\n", "seed 6\n") }
            withClue("생성기 실행 횟수") { Files.readAllLines(counter).size shouldBe 2 }
        }
    }
    test("생성기는 단계의 hidden 을 작업 디렉터리로 실행한다") {
        withTempDir { dir ->
            val stage = stageIn(dir.resolve("01"), "hidden/gen.sh" to "pwd -P")
            val test = TestCase("gen", listOf("1"), "q", input = null, expected = null, gen = listOf("sh", "gen.sh"), repeat = null, timeoutSec = null)
            val generated = Harness(runner).inputs(stage, test, listOf(1L)).single()
            withClue("작업 디렉터리") { String(generated.input) shouldBe "${stage.hiddenDir.toRealPath()}\n" }
        }
    }
    test("공개 API 검사는 파일이 있을 때만 학습자 작업 공간을 인자로 실행한다") {
        withTempDir { dir ->
            val stage = stageIn(dir.resolve("01"), "hidden/api-check.main.kts" to "check(true)")
            val program = dir.resolve("learner")
            var received: Triple<java.nio.file.Path, List<String>, Duration>? = null
            val probeRunner = object : ProcessRunner {
                override fun run(dir: java.nio.file.Path, command: List<String>, input: ByteArray, timeout: Duration): ProcessResult {
                    received = Triple(dir, command, timeout)
                    input.size shouldBe 0
                    return result()
                }

                override fun stopOnInterrupt() = Unit
            }
            val harness = Harness(probeRunner)
            harness.checkApi(stage, program)?.succeeded shouldBe true
            received shouldBe Triple(
                stage.hiddenDir,
                listOf("kotlin", stage.apiCheckPath.toString(), program.toAbsolutePath().toString()),
                Duration.ofSeconds(60),
            )
            Files.delete(stage.apiCheckPath)
            harness.checkApi(stage, program) shouldBe null
        }
    }
})

private fun processExists(argument: String): Boolean =
    ProcessHandle.allProcesses().anyMatch { handle ->
        handle.info().arguments().map { arguments -> argument in arguments }.orElse(false) ||
            handle.info().commandLine().map { it.contains(argument) }.orElse(false)
    }
