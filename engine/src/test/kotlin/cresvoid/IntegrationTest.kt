package cresvoid

import cresvoid.progress.Progress
import cresvoid.progress.StageProgress
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class IntegrationTest : FunSpec({
    test("구현 트랙: 시작, 관문, 통과 전 참조 풀이, 읽기, 문항, 숨은 케이스를 보여 주지 않는 실패 보고, 통과") { Sandbox(engineJar).use { buildFlow(it) } }
    test("구현 트랙: 참조 풀이나 입력 생성기의 표준 오류는 테스트에서 숨기고 검증에서만 보여 준다") { Sandbox(engineJar).use { failingContentPrograms(it) } }
    test("구현 트랙: 모든 단계 관문, 복습, 작업 공간 다시 고르기") { Sandbox(engineJar).use { gatesAndReview(it) } }
    test("독해 트랙: 답 없는 첫 시도, 첫 시도 중단, 두 번째 시도 중단, 공개, 완료, 시간 측정") { Sandbox(engineJar).use { readingFlow(it) } }
    test("검증: 좋은 트랙은 통과하고 망가진 트랙의 문제를 모두 찾는다") { Sandbox(engineJar).use { validation(it) } }
    test("여러 프로세스가 동시에 고쳐도 기록을 잃지 않는다") { Sandbox(engineJar).use { concurrentProcesses(it) } }
    test("문항 풀이 도중 Ctrl-C 를 받으면 첫 시도를 채점한다") { Sandbox(engineJar).use { quizInterrupt(it) } }
    test("테스트 도중 Ctrl-C 를 받으면 자식과 자손을 끝내고 130 으로 끝낸다") { Sandbox(engineJar).use { testInterrupt(it) } }
    test("터미널에서 테스트 도중 누른 Ctrl-\\ 와 Ctrl-C 는 풀이 프로그램이 아니라 엔진이 받는다") { Sandbox(engineJar).use { terminalSignals(it) } }
    test("명령 처리: 도움말, 모르는 명령, 없앤 명령, 권한 오류, 루트 찾기") { Sandbox(engineJar).use { commandHandling(it) } }
    test("코드 읽기 트랙: 코드와 줄 번호 표시, 코드 위치 힌트, 완료, 다른 명령 거절") { Sandbox(engineJar).use { codeFlow(it) } }
    test("버그 수정 트랙: 단계별 작업 공간, 대상 단계 테스트, 통과 전 참조 풀이 차이, 복습") { Sandbox(engineJar).use { fixFlow(it) } }
    test("검증: 코드 읽기와 버그 수정 트랙의 문제를 찾고 바뀐 줄 수를 보여 준다") { Sandbox(engineJar).use { newKindValidation(it) } }
    test("초기화: 지울 내용, 비터미널 거절, 사본, 범위, 다음 할 일, 읽을 수 없는 기록") { Sandbox(engineJar).use { resetFlow(it) } }
    test("초기화: 터미널에서는 확인을 받고 거절하면 지우지 않는다") { Sandbox(engineJar).use { resetInTerminal(it) } }
})

private fun buildFlow(sandbox: Sandbox) {
    sandbox.addBuildTrack()
    sandbox.cresvoid("test", "mini").expectExit(1, "작업 공간 전 테스트")
        .expectOutput("풀이 작업 공간이 없습니다. 먼저 만드세요: cresvoid start mini", "작업 공간 안내")
    sandbox.cresvoid("start", "mini").expectExit(0, "시작")
        .expectOutput("작업 공간을 만들었습니다: practice/mini", "시작 문구")
        .expectOutput("다음: 01-upper 문서 읽기. cresvoid read mini", "다음 할 일")
    val workspace = sandbox.root.resolve("practice/mini")
    withClue("복사한 파일") { (workspace.listDirectoryEntries().map { it.name }.sorted()) shouldBe listOf("build.sh", "notes.txt", "run.sh") }
    withClue("run.sh 실행 권한") { workspace.resolve("run.sh").isExecutable() shouldBe true }
    sandbox.cresvoid("test", "mini").expectExit(1, "문항 전 테스트")
        .expectOutput("01-upper 확인 문항을 먼저 마치세요: cresvoid quiz mini -stage 1", "관문 문구")

    sandbox.cresvoid("solution", "mini").expectExit(0, "통과 전 참조 풀이")
        .expectOutput("mini 01-upper · 대문자 참조 풀이\ncontent/tracks/mini/stages/01-upper/hidden/solution\n", "마치지 않은 첫 단계의 머리말과 경로")
        .expectOutput("==> Upper.kt <==\n1  fun upper(line: String): String = line.uppercase()\n", "줄 번호를 붙인 소스")
        .expectNoOutput("run.sh", "소스가 아닌 파일")
    sandbox.cresvoid("solution", "mini", "-stage", "2").expectExit(0, "통과 전 다른 단계의 참조 풀이")
        .expectOutput("mini 02-reverse · 뒤집기 참조 풀이\n", "고른 단계의 머리말")
        .expectOutput("==> Reverse.kt <==\n", "고른 단계의 소스")
    sandbox.cresvoid("read", "mini").expectExit(0, "읽기")
        .expectOutput("[1] 프로그램은 표준 입력의 줄을 읽는다.", "문서 내용")
        .expectOutput("다 읽었으면 문항을 푸세요: cresvoid quiz mini\n", "다음 안내")
    withClue("읽기 시작 시각") { (sandbox.stage("mini", "01-upper").openedAt != null) shouldBe true }
    sandbox.cresvoid("quiz", "mini", input = "2\nABC\n").expectExit(0, "문항")
        .expectOutput("채점: 2문항 중 2문항 정답", "채점")
        .expectOutput("이 단계 문항을 모두 마쳤습니다.", "완료")
        .expectOutput("다음: 01-upper 테스트 통과하기. cresvoid test mini", "다음 할 일")

    val progressFile = sandbox.root.resolve("practice/progress/progress.json")
    sandbox.cresvoid("test", "mini", "-seed", "7").expectExit(1, "시작 코드 테스트")
        .expectOutput(
            "  ✗ basic\n" +
                "    출력이 기대와 다릅니다\n" +
                "    다시 읽을 곳: content/tracks/mini/stages/01-upper/spec.md 의 [1.1] 문단\n" +
                "    핵심 문장: 각 줄의 영문 소문자를 대문자로 바꿔 한 줄씩 출력한다.\n" +
                "    참조 풀이를 보려면: cresvoid solution mini -stage 1\n",
            "실패 보고",
        )
        .expectNoOutput("hello", "입력과 표준 출력")
        .expectNoOutput("HELLO", "기대 출력")
    withClue("실패는 진행 기록에 남기지 않는다") { ("test_failures" in Files.readString(progressFile)) shouldBe false }
    withClue("실패 파일을 남기지 않는다") { (sandbox.exists("practice/progress/last-failure") || sandbox.exists("state")) shouldBe false }

    sandbox.file("practice/mini/run.sh", "#!/bin/sh\necho partial-stdout\necho boom-stderr >&2\nexit 3\n", executable = true)
    sandbox.cresvoid("test", "mini", "-seed", "7").expectExit(1, "비정상 종료")
        .expectOutput("  ✗ basic\n    프로그램이 종료 코드 3 로 끝났습니다\n", "종료 코드 문구")
        .expectNoOutput("partial-stdout", "표준 출력")
        .expectNoOutput("boom-stderr", "표준 오류")
        .expectNoOutput("hello", "입력")
        .expectNoOutput("HELLO", "기대 출력")

    sandbox.file("practice/mini/run.sh", "#!/bin/sh\nsleep 5\n", executable = true)
    val started = System.nanoTime()
    sandbox.cresvoid("test", "mini", "-seed", "7").expectExit(1, "시간 초과")
        .expectOutput("제한 시간 2.0초를 넘었습니다", "시간 초과 문구")
    withClue("제한 시간에 끝나야 한다") { (Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(4)) shouldBe true }

    sandbox.file("practice/mini/run.sh", "#!/bin/sh\ntr a-z A-Z | sed s/LINE1/BROKEN/\n", executable = true)
    sandbox.cresvoid("test", "mini", "-seed", "7").expectExit(1, "무작위 입력 실패")
        .expectOutput("  ✗ gen-lines\n", "무작위 테스트 실패")
        .expectOutput("    같은 입력으로 다시 실행: cresvoid test mini -stage 1 -seed 7\n    참조 풀이를 보려면: cresvoid solution mini -stage 1\n", "seed 와 참조 풀이 안내")
        .expectNoOutput("line1-7", "생성한 입력")
        .expectNoOutput("LINE1-7", "기대 출력")
        .expectNoOutput("BROKEN", "표준 출력")
    withClue("여러 번 실패해도 진행 기록에 남기지 않는다") { ("test_failures" in Files.readString(progressFile)) shouldBe false }
    withClue("여러 번 실패해도 실패 파일을 남기지 않는다") { (sandbox.exists("practice/progress/last-failure") || sandbox.exists("state")) shouldBe false }

    sandbox.file("practice/mini/run.sh", UPPERCASE_RUN, executable = true)
    sandbox.cresvoid("test", "mini", "-seed", "7").expectExit(0, "통과")
        .expectOutput("  · gen-lines 입력 생성 중…\n  ✓ gen-lines (무작위 2회", "입력 생성 안내와 무작위 테스트 통과")
        .expectOutput("✓ 01-upper 까지 모두 통과했습니다. 테스트 3개", "통과 문구")
        .expectOutput("참조 풀이를 보려면: cresvoid solution mini -stage 1", "참조 풀이 안내")
        .expectOutput("다음: 02-reverse 문서 읽기. cresvoid read mini", "다음 할 일")
    withClue("통과한 작업 공간") { sandbox.stage("mini", "01-upper").passedDir shouldBe "practice/mini" }
    sandbox.cresvoid("solution", "mini").expectExit(0, "통과 뒤 참조 풀이")
        .expectOutput("mini 02-reverse · 뒤집기 참조 풀이\n", "마치지 않은 첫 단계")
}

private fun failingContentPrograms(sandbox: Sandbox) {
    sandbox.addBuildTrack()
    sandbox.cresvoid("start", "mini").expectExit(0, "시작")
    sandbox.cresvoid("quiz", "mini", input = "2\nABC\n").expectExit(0, "문항")
    sandbox.file("practice/mini/run.sh", UPPERCASE_RUN, executable = true)
    val hidden = "content/tracks/mini/stages/01-upper/hidden"
    sandbox.file("$hidden/solution/run.sh", "#!/bin/sh\ncat >&2\nexit 3\n", executable = true)
    sandbox.cresvoid("test", "mini", "-seed", "7").expectExit(1, "참조 풀이 실패")
        .expectOutput("오류: 참조 풀이가 실패했습니다 (종료 코드 3): 01-upper\n", "참조 풀이 실패 문구")
        .expectNoOutput("line1-7", "참조 풀이 표준 오류에 섞인 생성 입력")
    sandbox.cresvoid("validate", "mini").expectExit(1, "참조 풀이 실패 검증")
        .expectOutput("참조 풀이가 실패했습니다 (종료 코드 3): 01-upper\nline1-1\n", "검증이 보여 주는 참조 풀이 표준 오류")
    sandbox.file("$hidden/gen.sh", "echo gen-stderr >&2\nexit 2\n")
    sandbox.cresvoid("test", "mini", "-seed", "7").expectExit(1, "입력 생성기 실패")
        .expectOutput("오류: 입력 생성기가 실패했습니다 (종료 코드 2): 01-upper/gen-lines\n", "입력 생성기 실패 문구")
        .expectNoOutput("gen-stderr", "입력 생성기 표준 오류")
    sandbox.cresvoid("validate", "mini").expectExit(1, "입력 생성기 실패 검증")
        .expectOutput("입력 생성기가 실패했습니다 (종료 코드 2): 01-upper/gen-lines\ngen-stderr\n", "검증이 보여 주는 입력 생성기 표준 오류")
}

private fun gatesAndReview(sandbox: Sandbox) {
    sandbox.addBuildTrack()
    sandbox.cresvoid("start", "mini").expectExit(0, "시작")
    sandbox.cresvoid("start", "mini", "-lang", "java").expectExit(1, "작업 공간이 있을 때 없는 언어")
        .expectOutput("이 언어의 시작 코드가 없습니다: java. 있는 언어: sh", "언어 오류")
    sandbox.file("practice/mini/run.sh", REVERSE_RUN, executable = true)
    sandbox.cresvoid("quiz", "mini", input = "2\nABC\n").expectExit(0, "1단계 문항")
    sandbox.cresvoid("test", "mini", "-stage", "2").expectExit(1, "2단계 문항 전")
        .expectOutput("02-reverse 확인 문항을 먼저 마치세요: cresvoid quiz mini -stage 2", "2단계 문항 관문")
    sandbox.cresvoid("quiz", "mini", "-stage", "2", input = "CBA\n").expectExit(0, "2단계 문항")
    sandbox.cresvoid("test", "mini", "-stage", "2", "-seed", "3").expectExit(0, "두 단계 통과")
        .expectOutput("✓ 02-reverse 까지 모두 통과했습니다. 테스트 4개", "통과 문구")

    val longAgo = OffsetDateTime.now().minusDays(20)
    sandbox.repository().update { progress ->
        listOf("01-upper", "02-reverse").fold(progress) { current, dir -> current.updateStage("mini", dir) { it.copy(passedAt = longAgo) } }
    }
    sandbox.cresvoid().expectExit(0, "상태")
        .expectOutput("복습할 차례: 01-upper. 빈 작업 공간에서 다시 구현하고 1단계 테스트를 통과하세요.", "복습 안내")
        .expectOutput("다음: 01-upper 복습. cresvoid start mini -dir <새 경로>", "같은 작업 공간이면 새 경로 안내")
    sandbox.cresvoid("test", "mini", "-stage", "1").expectExit(0, "같은 작업 공간에서 통과")
    withClue("같은 작업 공간은 복습이 아니다") { sandbox.stage("mini", "01-upper").reviewedAt shouldBe null }

    sandbox.cresvoid("start", "mini", "-dir", "review").expectExit(0, "새 작업 공간")
        .expectOutput("작업 공간을 만들었습니다: review", "새 작업 공간 문구")
        .expectOutput("다음: 01-upper 복습. cresvoid test mini -stage 1", "다음 할 일")
    sandbox.file("review/run.sh", REVERSE_RUN, executable = true)
    sandbox.cresvoid("test", "mini", "-stage", "1").expectExit(0, "복습 통과")
        .expectOutput("복습으로 기록했습니다: 01-upper", "복습 기록")
    withClue("01-upper 복습 시각") { (sandbox.stage("mini", "01-upper").reviewedAt != null) shouldBe true }
    withClue("02-reverse 는 아직") { sandbox.stage("mini", "02-reverse").reviewedAt shouldBe null }

    sandbox.cresvoid("start", "mini", "-dir", "practice/mini").expectExit(0, "작업 공간 다시 고르기")
        .expectOutput("이미 있는 작업 공간을 고릅니다: practice/mini", "다시 고르기 문구")
    withClue("고른 작업 공간") { sandbox.progress().tracks["mini"]?.solutionDir shouldBe "practice/mini" }
    sandbox.cresvoid("start", "mini", "-stage", "2").expectExit(1, "구현 트랙의 단계 작업 공간")
        .expectOutput("구현 트랙은 모든 단계가 작업 공간 하나를 씁니다. -stage 없이 실행하세요: cresvoid start mini", "거절 문구")
    sandbox.cresvoid("start", "mini", "-dir", "content").expectExit(1, "비어 있지 않은 디렉터리")
        .expectOutput("비어 있지 않은 디렉터리라 작업 공간을 만들 수 없습니다. 다른 경로를 고르세요: content", "거절 문구")
}

private fun readingFlow(sandbox: Sandbox) {
    sandbox.addReadingTrack()
    val progressFile = sandbox.root.resolve("practice/progress/progress.json")
    sandbox.cresvoid("quiz", "story").expectExit(0, "답 없는 첫 시도")
        .expectOutput("중단했습니다. 이번 답은 저장하지 않았습니다.", "저장하지 않음")
        .expectNoOutput("첫 시도라서", "채점하지 않음")
    withClue("답 없는 첫 시도는 기록하지 않는다") { Files.exists(progressFile) shouldBe false }

    sandbox.cresvoid("quiz", "story", input = "1\n").expectExit(0, "첫 시도 중단")
        .expectOutput("읽기 기록이 없어 이번 풀이 시간은 재지 않습니다.", "시간 안내")
        .expectOutput("    <보기>\n    첫 줄", "보기")
        .expectOutput("중단했습니다. 첫 시도라서 답하지 않은 문항은 틀린 것으로 채점했습니다.", "중단 문구")
        .expectOutput("첫 시도 0/2, 시간은 재지 않음", "첫 시도 요약")
    val afterFirst = sandbox.stage("story", "01-tide").quiz
    withClue("답하지 않은 문항도 오답") { afterFirst.wrong shouldBe mapOf("q1" to 1, "q2" to 1) }

    val saved = Files.readString(progressFile)
    sandbox.cresvoid("quiz", "story", input = "x\n9\n3\n").expectExit(0, "두 번째 시도 중단")
        .expectOutput("다시 입력하세요.", "잘못된 답")
        .expectOutput("중단했습니다. 이번 답은 저장하지 않았습니다.", "저장하지 않음")
    withClue("두 번째 시도 중단은 기록을 바꾸지 않는다") { Files.readString(progressFile) shouldBe saved }

    sandbox.cresvoid("quiz", "story", input = "1\n1\n").expectExit(0, "두 번째 오답")
        .expectOutput("핵심 문장: 달이 더 가까워서 영향이 크다.", "핵심 문장")
        .expectOutput("해당 문단을 다시 읽고 틀린 문항을 다시 푸세요: cresvoid quiz story", "다시 풀기 안내")
    sandbox.cresvoid("quiz", "story", input = "1\n2\n").expectExit(0, "세 번째 오답과 완료")
        .expectOutput("정답: 3번. 다", "정답 공개")
        .expectOutput("세 번 틀려서 정답을 공개하고 이 문항을 닫았습니다.", "공개 안내")
        .expectOutput("이 단계 문항을 모두 마쳤습니다.", "완료")
        .expectOutput("다음: 모든 단계를 마쳤습니다.", "다음 할 일")
    sandbox.cresvoid().expectOutput("01-tide  조석 1  읽기 ·  문항 ✓ 0/2 공개 1\n", "상태 줄")
    sandbox.cresvoid("quiz", "story").expectExit(0, "닫힌 문항")
        .expectOutput("이 단계 문항은 모두 마쳤습니다.", "닫힌 문항 문구")
    sandbox.cresvoid("start", "story").expectExit(1, "독해 트랙 작업 공간")
        .expectOutput("독해 트랙에는 작업 공간이 필요 없습니다. 문서를 읽으세요: cresvoid read story", "거절 문구")
    sandbox.cresvoid("test", "story").expectExit(1, "독해 트랙 테스트")
        .expectOutput("독해 트랙에는 테스트가 없습니다. 문항을 푸세요: cresvoid quiz story", "거절 문구")
    sandbox.cresvoid("solution", "story").expectExit(1, "독해 트랙 참조 풀이")
        .expectOutput("독해 트랙에는 참조 풀이가 없습니다", "거절 문구")

    sandbox.addReadingTrack(stageCount = 2)
    sandbox.cresvoid("read", "story", "-stage", "2").expectExit(0, "읽기")
        .expectOutput("story 02-tide · 조석 2 · 목표 5분", "머리말")
    sandbox.cresvoid("quiz", "story", "-stage", "2", input = "3\n2\n").expectExit(0, "읽은 뒤 문항")
        .expectOutput("첫 시도 2/2, 걸린 시간 ", "걸린 시간")
        .expectOutput("초 (목표 5분)", "목표 시간")
    withClue("기록한 시간") { (sandbox.stage("story", "02-tide").quiz.firstSeconds != null) shouldBe true }
}

private fun validation(sandbox: Sandbox) {
    sandbox.addBuildTrack()
    sandbox.addReadingTrack()
    sandbox.addBrokenTracks()
    sandbox.cresvoid("validate", "mini").expectExit(0, "구현 트랙 검증")
        .expectOutput("· 01-upper 참조 풀이로 1~1단계 테스트를 실행합니다", "참조 풀이 실행")
        .expectOutput("· sh 시작 코드로 01-upper 테스트를 실행합니다 (실패해야 정상)", "시작 코드 실행")
        .expectOutput("검증 통과", "통과")
    sandbox.cresvoid("validate", "story").expectExit(0, "독해 트랙 검증").expectOutput("검증 통과", "통과")
    val broken = sandbox.cresvoid("validate", "broken").expectExit(1, "망가진 트랙")
    for (problem in listOf(
        "파일을 읽지 못한 단계가 있어 실행 검증을 건너뜁니다.",
        "content/tracks/broken/stages/01-bad/spec.md: 문단 번호 [1] 가 겹칩니다",
        "01-bad/hidden/quiz.json q1: answer 4 가 선지 범위를 벗어납니다",
        "01-bad/hidden/quiz.json q1: 문단 [7] 이 문서에 없습니다",
        "01-bad/hidden/quiz.json q1: quote 가 refs 문단에 없습니다: \"없는 문장\"",
        "01-bad/hidden/tests.json Bad_Name: 이름은 영문 kebab-case 여야 합니다",
        "01-bad/hidden/tests.json Bad_Name: input 과 gen 중 하나만 둡니다",
        "content/tracks/broken/stages/02-missing/gen.main.kts: 채점용 파일은 hidden/ 으로 옮기세요",
        "content/tracks/broken/stages/02-missing/quiz.json: 채점용 파일은 hidden/ 으로 옮기세요",
        "content/tracks/broken/stages/02-missing/solution: 채점용 파일은 hidden/ 으로 옮기세요",
        "content/tracks/broken/stages/02-missing/tests.json: 채점용 파일은 hidden/ 으로 옮기세요",
        "파일이 없습니다: ${sandbox.root}/content/tracks/broken/stages/02-missing/spec.md",
        "파일이 없습니다: ${sandbox.root}/content/tracks/broken/stages/02-missing/hidden/quiz.json",
        "content/tracks/broken/stages/02-missing/hidden/solution/run.sh 가 없거나 실행 권한이 없습니다",
    )) {
        broken.expectOutput(problem, "망가진 트랙 문제")
    }
    val wrong = sandbox.cresvoid("validate", "wrong").expectExit(1, "틀린 풀이 트랙")
    for (problem in listOf(
        "01-echo 참조 풀이가 01-echo/echo 에서 실패합니다: 1번째 줄 기대 \"y\", 실제 \"x\"",
        "01-echo/hidden/quiz.json q1: 참조 풀이 출력 \"a\" 와 answers[0] \"b\" 가 다릅니다",
        "01-echo 참조 풀이가 02-same 테스트를 모두 통과합니다. 새 단계가 새 동작을 확인하지 않습니다",
        "sh 시작 코드가 01-echo 테스트를 모두 통과합니다",
    )) {
        wrong.expectOutput(problem, "틀린 풀이 문제")
    }
    sandbox.file("content/tracks/mini/stages/01-upper/hidden/quiz.json", """{"questions": [], "Extra": 1}""")
    sandbox.cresvoid("quiz", "mini").expectExit(1, "엄격한 JSON")
        .expectOutput("키는 영문 소문자 snake_case 여야 합니다: \"Extra\"", "키 오류")
}

private fun concurrentProcesses(sandbox: Sandbox) {
    sandbox.addReadingTrack(stageCount = 4)
    val processes = (1..4).map { number ->
        sandbox.command("quiz", "story", "-stage", "$number")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .also { process -> process.outputStream.use { it.write("1\n1\n".toByteArray()) } }
    }
    processes.forEach { withClue("동시 실행한 문항") { (it.waitFor(60, TimeUnit.SECONDS) && it.exitValue() == 0) shouldBe true } }
    val stages = sandbox.progress().tracks["story"]?.stages.orEmpty()
    withClue("기록된 단계") { stages.keys shouldBe ((1..4).map { "0$it-tide" }.toSet()) }
    withClue("모든 단계의 첫 시도 기록") { (stages.values.all { it.quiz.firstCorrect == 0 }) shouldBe true }
}

private fun quizInterrupt(sandbox: Sandbox) {
    sandbox.addReadingTrack()
    val process = sandbox.command("quiz", "story").start()
    val output = StringBuilder()
    val reader = Thread { process.inputStream.bufferedReader().forEachLine { synchronized(output) { output.appendLine(it) } } }.apply { start() }
    process.outputStream.write("3\n".toByteArray())
    process.outputStream.flush()
    waitUntil("두 번째 문항 질문") { synchronized(output) { "[2/2]" in output } }
    Thread.sleep(300)
    sendSignal("INT", process)
    withClue("중단 뒤 끝나야 한다") { process.waitFor(20, TimeUnit.SECONDS) shouldBe true }
    reader.join(5000)
    withClue("종료 코드") { process.exitValue() shouldBe 0 }
    withClue("중단 문구") { (synchronized(output) { output.toString() }) shouldContain "중단했습니다. 첫 시도라서 답하지 않은 문항은 틀린 것으로 채점했습니다." }
    val quiz = sandbox.stage("story", "01-tide").quiz
    withClue("답한 문항은 채점") { quiz.resolved shouldBe listOf("q1") }
    withClue("답하지 않은 문항은 오답") { quiz.wrong shouldBe mapOf("q2" to 1) }
}

private fun testInterrupt(sandbox: Sandbox) {
    sandbox.addBuildTrack()
    sandbox.cresvoid("start", "mini").expectExit(0, "시작")
    sandbox.cresvoid("quiz", "mini", input = "2\nABC\n").expectExit(0, "문항")
    val marker = "43.5${System.nanoTime() % 1000}"
    sandbox.file("practice/mini/run.sh", "#!/bin/sh\nsleep $marker &\nsleep $marker\n", executable = true)
    val process = sandbox.command("test", "mini").redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    waitUntil("테스트가 띄운 sleep") { processWithArgument(marker) }
    sendSignal("INT", process)
    withClue("중단 뒤 끝나야 한다") { process.waitFor(20, TimeUnit.SECONDS) shouldBe true }
    withClue("종료 코드") { process.exitValue() shouldBe 130 }
    withClue("중단 문구") { process.errorStream.bufferedReader().readText() shouldContain "중단했습니다." }
    waitUntil("sleep 종료") { !processWithArgument(marker) }
}

private fun commandHandling(sandbox: Sandbox) {
    sandbox.addReadingTrack()
    sandbox.cresvoid("help").expectExit(0, "도움말")
        .expectOutput("cresvoid: 로컬 구현·독해·코드 읽기·버그 수정 훈련 도구", "도움말 문구")
        .expectOutput("cresvoid reset [트랙] [-stage N] [-yes]    진행 기록을 지운다", "초기화 사용법")
        .expectOutput("cresvoid solution <트랙> [-stage N]        참조 풀이를 본다", "참조 풀이 사용법")
        .expectOutput("지우기 전에 practice/progress/backups 에 사본을 남기고", "사본 위치")
        .expectNoOutput("memo", "없어진 메모 명령")
        .expectNoOutput("reflect", "없어진 회고 명령")
    sandbox.cresvoid("nope").expectExit(2, "모르는 명령").expectOutput("모르는 명령입니다: nope", "모르는 명령 문구")
    for (removed in listOf("memo", "reflect")) {
        sandbox.cresvoid(removed, "story").expectExit(2, "없어진 명령 $removed").expectOutput("모르는 명령입니다: $removed", "없어진 명령 문구")
    }
    sandbox.cresvoid("quiz", "story", "-x").expectExit(1, "모르는 플래그")
        .expectOutput("모르는 옵션입니다: -x\n사용법: cresvoid quiz <트랙> [-stage N]", "옵션 오류")
    sandbox.file("practice/progress/progress.json", "{}")
    val progressFile = sandbox.root.resolve("practice/progress/progress.json")
    Files.setPosixFilePermissions(progressFile, PosixFilePermissions.fromString("---------"))
    try {
        sandbox.cresvoid().expectExit(1, "읽을 권한이 없는 기록").expectOutput("오류: 권한이 없습니다: $progressFile\n", "권한 오류 문구")
    } finally {
        Files.setPosixFilePermissions(progressFile, PosixFilePermissions.fromString("rw-r--r--"))
    }
    val outside = Files.createTempDirectory(Path("/tmp"), "cresvoid-it-outside-")
    try {
        sandbox.cresvoid(workingDir = outside).expectExit(1, "루트 없음")
            .expectOutput("content/tracks 디렉터리를 찾지 못했습니다. cresvoid 프로젝트 안에서 실행하세요", "루트 없음 문구")
        sandbox.cresvoid(workingDir = outside, cresvoidRoot = sandbox.root).expectExit(0, "CRESVOID_ROOT")
            .expectOutput("story 미니 독해 (독해)", "CRESVOID_ROOT 로 찾은 트랙")
        Files.createDirectories(sandbox.root.resolve("content/tracks/story/stages/01-tide/deeper"))
        sandbox.cresvoid(workingDir = sandbox.root.resolve("content/tracks/story/stages/01-tide/deeper"), cresvoidRoot = outside)
            .expectExit(0, "조상 디렉터리").expectOutput("story 미니 독해 (독해)", "조상에서 찾은 트랙")
    } finally {
        outside.toFile().deleteRecursively()
    }
}

private fun codeFlow(sandbox: Sandbox) {
    sandbox.addCodeTrack()
    sandbox.cresvoid().expectExit(0, "상태")
        .expectOutput("lens 미니 코드 읽기 (코드 읽기)", "트랙 줄")
        .expectOutput("01-cart  장바구니 합계  읽기 ·  문항 ·\n", "단계 줄")
        .expectOutput("다음: 01-cart 문서 읽기. cresvoid read lens", "다음 할 일")
    sandbox.cresvoid("read", "lens").expectExit(0, "읽기")
        .expectOutput("lens 01-cart · 장바구니 합계 · 목표 10분", "머리말")
        .expectOutput("코드: content/tracks/lens/stages/01-cart/code\n", "코드 경로")
        .expectOutput("[2] 입력이 끝나면 담긴 수량의 합을 한 줄로 출력한다.\n\n==> Cart.kt <==\n 1  package lens\n 2\n 3  class Cart {\n", "안내 문서 뒤의 코드")
        .expectOutput(" 7          quantities[item] = (quantities[item] ?: 0) + quantity\n", "들여쓰기를 지킨 줄")
        .expectOutput("10      fun total(): Int = quantities.values.sum()\n11  }\n\n==> Main.kt <==\n1  package lens\n", "파일마다 다시 세는 줄 번호")
        .expectOutput("다 읽었으면 문항을 푸세요: cresvoid quiz lens", "다음 안내")
    sandbox.cresvoid("quiz", "lens", input = "1\n4\n").expectExit(0, "첫 시도")
        .expectOutput("✗ q1: Cart.kt 6~8행을 다시 읽으세요.", "코드 위치 힌트")
        .expectOutput("✗ q2: content/tracks/lens/stages/01-cart/guide.md 의 [2] 문단과 Cart.kt 10행을 다시 읽으세요.", "문단과 코드 위치 힌트")
        .expectOutput("    입력:\n      ADD apple 2\n      ADD pear 3", "입력 표시")
    sandbox.cresvoid("quiz", "lens", input = "3\n6\n").expectExit(0, "두 번째 오답")
        .expectOutput("핵심 문장: quantities[item] = (quantities[item] ?: 0) + quantity", "핵심 문장")
    sandbox.cresvoid("quiz", "lens", input = "2\n5\n").expectExit(0, "정답")
        .expectOutput("이 단계 문항을 모두 마쳤습니다.", "완료")
        .expectOutput("다음: 모든 단계를 마쳤습니다.", "다음 할 일")
    val quiz = sandbox.stage("lens", "01-cart").quiz
    withClue("첫 시도 점수") { (quiz.firstCorrect to quiz.firstTotal) shouldBe (0 to 2) }
    withClue("읽은 뒤 푼 첫 시도는 시간을 잰다") { (quiz.firstSeconds != null) shouldBe true }
    sandbox.cresvoid().expectOutput("01-cart  장바구니 합계  읽기 ✓  문항 ✓ 0/2 ", "완료한 단계 줄")
    sandbox.cresvoid("start", "lens").expectExit(1, "작업 공간")
        .expectOutput("코드 읽기 트랙에는 작업 공간이 필요 없습니다. 문서를 읽으세요: cresvoid read lens", "거절 문구")
    sandbox.cresvoid("test", "lens").expectExit(1, "테스트")
        .expectOutput("코드 읽기 트랙에는 테스트가 없습니다. 문항을 푸세요: cresvoid quiz lens", "거절 문구")
    sandbox.cresvoid("solution", "lens").expectExit(1, "참조 풀이").expectOutput("코드 읽기 트랙에는 참조 풀이가 없습니다", "거절 문구")
}

private fun fixFlow(sandbox: Sandbox) {
    sandbox.addFixTrack()
    sandbox.cresvoid().expectExit(0, "상태")
        .expectOutput("patch 미니 버그 수정 (버그 수정)", "트랙 줄")
        .expectOutput("  01-total  합계    읽기 ·  테스트 ·\n", "1단계 줄")
        .expectOutput("  02-max    최댓값  읽기 ·  테스트 ·\n", "2단계 줄")
        .expectOutput("다음: 01-total 작업 공간 만들기. cresvoid start patch -stage 1\n", "작업 공간 안내")
    sandbox.cresvoid("test", "patch").expectExit(1, "작업 공간 전 테스트")
        .expectOutput("풀이 작업 공간이 없습니다. 먼저 만드세요: cresvoid start patch -stage 1\n", "안내 문구")
    sandbox.cresvoid("test", "patch", "-stage", "2").expectExit(1, "작업 공간 전 2단계 테스트")
        .expectOutput("풀이 작업 공간이 없습니다. 먼저 만드세요: cresvoid start patch -stage 2\n", "2단계 안내 문구")
    sandbox.cresvoid("quiz", "patch").expectExit(1, "문항")
        .expectOutput("버그 수정 트랙에는 확인 문항이 없습니다. 다음: 01-total 작업 공간 만들기. cresvoid start patch -stage 1\n", "거절 문구")
    sandbox.cresvoid("start", "patch", "-lang", "sh").expectExit(1, "언어 지정")
        .expectOutput("버그 수정 트랙은 단계마다 시작 코드가 하나라 -lang 을 쓰지 않습니다", "거절 문구")
    sandbox.cresvoid("solution", "patch", "-stage", "1").expectExit(0, "통과 전 참조 풀이")
        .expectOutput("patch 01-total · 합계 참조 풀이\ncontent/tracks/patch/stages/01-total/hidden/solution\n", "머리말과 경로")
        .expectOutput("--- starter/run.sh", "시작 코드 쪽")
        .expectOutput("+++ hidden/solution/run.sh", "참조 풀이 쪽")
        .expectOutput("-awk 'NR > 1 { total += \$1 } END { print total + 0 }'\n+awk '{ total += \$1 } END { print total + 0 }'\n", "바뀐 줄")
        .expectOutput("-fun total(numbers: List<Int>): Int = numbers.drop(1).sum()\n+fun total(numbers: List<Int>): Int = numbers.sum()\n", "소스 차이")
        .expectNoOutput("Only in", "build 디렉터리와 .DS_Store")
    sandbox.cresvoid("start", "patch").expectExit(0, "1단계 작업 공간")
        .expectOutput("작업 공간을 만들었습니다: practice/patch/01-total\n이제부터 01-total 테스트는 이 작업 공간에서 실행합니다.", "만든 작업 공간")
        .expectOutput("다음: 01-total 문서 읽기. cresvoid read patch", "다음 할 일")
    val firstWorkspace = sandbox.root.resolve("practice/patch/01-total")
    withClue("복사한 파일") { (firstWorkspace.listDirectoryEntries().map { it.name }.sorted()) shouldBe listOf("build.sh", "run.sh", "src") }
    withClue("run.sh 실행 권한") { firstWorkspace.resolve("run.sh").isExecutable() shouldBe true }
    withClue("단계 작업 공간 기록") { sandbox.stage("patch", "01-total").solutionDir shouldBe "practice/patch/01-total" }
    withClue("트랙 작업 공간은 쓰지 않는다") { sandbox.progress().tracks["patch"]?.solutionDir shouldBe null }
    sandbox.cresvoid().expectOutput("  풀이: practice/patch/01-total\n", "상태 화면의 작업 공간")
    sandbox.cresvoid("read", "patch").expectExit(0, "읽기")
        .expectOutput("[3] 숫자 하나만 넣으면 0 이 나온다.", "버그 신고")
        .expectOutput("다음: 01-total 테스트 통과하기. cresvoid test patch", "다음 할 일")
    sandbox.cresvoid("test", "patch").expectExit(1, "시작 코드")
        .expectOutput("  ✗ single\n    출력이 기대와 다릅니다\n", "첫 실패")
        .expectOutput("    참조 풀이를 보려면: cresvoid solution patch -stage 1\n", "참조 풀이 안내")
        .expectNoOutput("02-max", "다른 단계 테스트")

    sandbox.file("practice/patch/01-total/run.sh", SUM_RUN, executable = true)
    sandbox.cresvoid("test", "patch").expectExit(0, "고친 뒤")
        .expectOutput("✓ empty", "마지막 테스트")
        .expectOutput("✓ 01-total 테스트를 모두 통과했습니다. 테스트 3개", "통과 문구")
        .expectOutput("참조 풀이를 보려면: cresvoid solution patch -stage 1", "참조 풀이 안내")
        .expectOutput("다음: 02-max 작업 공간 만들기. cresvoid start patch -stage 2\n", "다음 할 일")
        .expectNoOutput("02-max 최댓값", "다른 단계 테스트")
    withClue("통과한 작업 공간") { sandbox.stage("patch", "01-total").passedDir shouldBe "practice/patch/01-total" }
    sandbox.cresvoid("solution", "patch").expectExit(0, "통과 뒤 참조 풀이").expectOutput("patch 02-max · 최댓값 참조 풀이", "마치지 않은 첫 단계")

    sandbox.cresvoid("start", "patch").expectExit(0, "2단계 작업 공간")
        .expectOutput("작업 공간을 만들었습니다: practice/patch/02-max", "2단계 경로")
    sandbox.cresvoid().expectOutput("  풀이: practice/patch/02-max\n", "상태 화면의 2단계 작업 공간")
        .expectOutput("다음: 02-max 문서 읽기. cresvoid read patch", "읽기 안내")
    sandbox.cresvoid("test", "patch").expectExit(1, "2단계 시작 코드")
        .expectOutput("  ✗ negative\n", "음수 실패")
        .expectOutput("    참조 풀이를 보려면: cresvoid solution patch -stage 2\n", "2단계 참조 풀이 안내")
        .expectNoOutput("01-total", "1단계 테스트")
    sandbox.cresvoid().expectOutput("다음: 02-max 문서 읽기. cresvoid read patch", "실패한 뒤에도 읽지 않았으면 읽기를 권한다")
    sandbox.file("practice/patch/02-max/run.sh", MAX_RUN, executable = true)
    sandbox.cresvoid("test", "patch").expectExit(0, "2단계 통과")
        .expectOutput("✓ 02-max 테스트를 모두 통과했습니다. 테스트 2개", "통과 문구")
    withClue("1단계 작업 공간은 그대로") { sandbox.stage("patch", "01-total").solutionDir shouldBe "practice/patch/01-total" }
    sandbox.cresvoid("solution", "patch").expectExit(0, "모두 마친 뒤 참조 풀이").expectOutput("patch 02-max · 최댓값 참조 풀이", "마지막 단계")

    val longAgo = OffsetDateTime.now().minusDays(20)
    sandbox.repository().update { progress ->
        listOf("01-total", "02-max").fold(progress) { current, dir -> current.updateStage("patch", dir) { it.copy(passedAt = longAgo) } }
    }
    sandbox.cresvoid().expectExit(0, "복습 상태")
        .expectOutput("복습할 차례: 01-total. 새 작업 공간에서 다시 고치고 1단계 테스트를 통과하세요.", "복습 안내")
        .expectOutput("다음: 01-total 복습. cresvoid start patch -stage 1 -dir <새 경로>", "새 경로 안내")
    sandbox.cresvoid("test", "patch", "-stage", "1").expectExit(0, "같은 작업 공간에서 통과")
    withClue("같은 작업 공간은 복습이 아니다") { sandbox.stage("patch", "01-total").reviewedAt shouldBe null }
    sandbox.cresvoid("start", "patch", "-stage", "1", "-dir", "review/total").expectExit(0, "복습 작업 공간")
        .expectOutput("작업 공간을 만들었습니다: review/total", "복습 작업 공간 경로")
        .expectOutput("다음: 01-total 복습. cresvoid test patch -stage 1", "다음 할 일")
    sandbox.cresvoid("test", "patch", "-stage", "1").expectExit(1, "새로 받은 시작 코드").expectOutput("✗ single", "실패")
    sandbox.file("review/total/run.sh", SUM_RUN, executable = true)
    sandbox.cresvoid("test", "patch", "-stage", "1").expectExit(0, "복습 통과")
        .expectOutput("복습으로 기록했습니다: 01-total", "복습 기록")
        .expectNoOutput("처음 쓰던 작업 공간으로 돌아가려면", "단계마다 작업 공간을 쓰면 돌아갈 필요가 없다")
    withClue("01-total 복습 시각") { (sandbox.stage("patch", "01-total").reviewedAt != null) shouldBe true }
    withClue("02-max 는 아직") { sandbox.stage("patch", "02-max").reviewedAt shouldBe null }
    withClue("다른 단계 작업 공간은 그대로") { sandbox.stage("patch", "02-max").solutionDir shouldBe "practice/patch/02-max" }
    sandbox.cresvoid().expectOutput("다음: 02-max 복습. cresvoid start patch -stage 2 -dir <새 경로>", "다음 복습")
}

private fun newKindValidation(sandbox: Sandbox) {
    sandbox.addCodeTrack()
    sandbox.addFixTrack()
    sandbox.addBrokenCodeTrack()
    sandbox.addBrokenFixTracks()
    sandbox.cresvoid("validate", "lens").expectExit(0, "코드 읽기 트랙 검증")
        .expectOutput("· 01-cart 코드를 빌드하고 빈 입력으로 실행합니다", "실행 확인")
        .expectOutput("검증 통과", "통과")
    sandbox.cresvoid("validate", "patch").expectExit(0, "버그 수정 트랙 검증")
        .expectOutput("· 01-total 참조 풀이로 01-total 테스트를 실행합니다", "참조 풀이 실행")
        .expectOutput("· 01-total 시작 코드로 01-total 테스트를 실행합니다 (하나 이상 실패해야 정상)\n  실패하는 테스트: single, several\n", "실패하는 테스트")
        .expectOutput("  시작 코드와 참조 풀이 사이에 바뀐 줄: 4줄 (추가 2, 삭제 2)", "바뀐 줄 수")
        .expectOutput("  실패하는 테스트: negative\n  시작 코드와 참조 풀이 사이에 바뀐 줄: 2줄 (추가 1, 삭제 1)", "2단계 바뀐 줄 수")
        .expectOutput("검증 통과", "통과")
        .expectNoOutput("1~", "대상 단계만 실행한다")
    val murky = sandbox.cresvoid("validate", "murky").expectExit(1, "망가진 코드 읽기 트랙")
    for (problem in listOf(
        "01-bad/hidden/quiz.json q1: 코드 위치가 파일 범위 1~3행을 벗어납니다: Cart.kt:2-9",
        "01-bad/hidden/quiz.json q2: 코드 파일이 없습니다: content/tracks/murky/stages/01-bad/code/src/main/kotlin/Nope.kt",
        "01-bad/hidden/quiz.json q3: quote 가 refs 코드 범위에 없습니다: \"없는 줄\"",
        "01-bad/hidden/quiz.json q4: 코드 위치는 <파일>:<줄> 이나 <파일>:<시작 줄>-<끝 줄> 꼴이어야 합니다: Cart.kt:x",
        "01-bad/hidden/quiz.json q5: code 트랙 short 문항에는 input 이 있어야 합니다",
        "01-bad/hidden/quiz.json q6: 코드 출력이 2줄입니다. 정확히 한 줄이어야 합니다",
        "01-bad/hidden/quiz.json q7: 끝 줄이 시작 줄보다 앞입니다: Cart.kt:3-1",
        "02-nobuild 코드 빌드 실패: 빌드 스크립트가 실패했습니다 (종료 코드 1)",
        "error: unresolved reference",
        "03-crash 코드를 빈 입력으로 실행하면 실패합니다 (종료 코드 3)",
    )) {
        murky.expectOutput(problem, "망가진 코드 읽기 트랙 문제")
    }
    murky.expectNoOutput("q6: quote", "공백을 줄여 비교한 quote")
        .expectNoOutput("q8", "문단과 코드 위치를 함께 쓴 문항")
        .expectNoOutput("01-bad 코드를 빈 입력으로 실행하면", "빈 입력 실행은 성공한다")
    val shaky = sandbox.cresvoid("validate", "shaky").expectExit(1, "흔들리는 버그 수정 트랙")
    for (problem in listOf(
        "content/tracks/shaky/stages/01-same/hidden/quiz.json: fix 트랙 단계에는 quiz.json 을 두지 않습니다",
        "01-same 시작 코드가 01-same 테스트를 모두 통과합니다",
        "  시작 코드와 참조 풀이 사이에 바뀐 줄: 0줄 (추가 0, 삭제 0)",
        "02-wrong 참조 풀이가 02-wrong/echo 에서 실패합니다: 1번째 줄 기대 \"y\", 실제 \"x\"",
    )) {
        shaky.expectOutput(problem, "흔들리는 버그 수정 트랙 문제")
    }
    shaky.expectNoOutput("02-wrong 시작 코드가", "실패하는 시작 코드는 정상")
    sandbox.cresvoid("validate", "flimsy").expectExit(1, "시작 코드가 빠진 트랙")
        .expectOutput("content/tracks/flimsy/stages/01-nostarter/starter/run.sh 가 없거나 실행 권한이 없습니다", "빠진 시작 코드")
        .expectOutput("파일을 읽지 못한 단계가 있어 실행 검증을 건너뜁니다.", "실행 검증 건너뜀")
}

private fun resetFlow(sandbox: Sandbox) {
    sandbox.addBuildTrack()
    sandbox.addReadingTrack()
    sandbox.cresvoid("start", "mini").expectExit(0, "시작")
    sandbox.cresvoid("quiz", "mini", input = "2\nABC\n").expectExit(0, "구현 문항")
    sandbox.cresvoid("test", "mini", "-seed", "7").expectExit(1, "실패")
    sandbox.cresvoid("quiz", "story", input = "3\n2\n").expectExit(0, "독해 문항")
    val progressFile = sandbox.root.resolve("practice/progress/progress.json")
    val before = Files.readString(progressFile)

    sandbox.cresvoid("reset", "mini").expectExit(1, "확인할 수 없는 비터미널")
        .expectOutput("지울 진행 기록:\n  mini: 작업 공간 선택(practice/mini)\n  mini 01-upper: 문항 첫 시도 2/2, 문항 완료\n", "지울 내용")
        .expectOutput("작업 공간 파일은 지우지 않습니다.", "남기는 것")
        .expectOutput("표준 입력이 터미널이 아니라 확인을 받을 수 없습니다. 지우려면 -yes 를 붙이세요: cresvoid reset mini -yes", "거절 문구")
    sandbox.cresvoid("reset", "mini", "-stage", "1").expectExit(1, "단계 범위 거절")
        .expectOutput("지우려면 -yes 를 붙이세요: cresvoid reset mini -stage 1 -yes", "단계 범위 명령")
    withClue("거절하면 기록을 바꾸지 않는다") { Files.readString(progressFile) shouldBe before }
    withClue("거절하면 사본을 남기지 않는다") { !sandbox.exists("practice/progress/backups") shouldBe true }

    sandbox.cresvoid("reset", "mini", "-stage", "1", "-yes").expectExit(0, "단계 초기화")
        .expectOutput("지울 진행 기록:\n  mini 01-upper: 문항 첫 시도 2/2, 문항 완료\n", "단계 내용")
        .expectOutput("지웠습니다. 지우기 전 사본: practice/progress/backups/progress-", "사본 경로")
        .expectOutput("다음: 01-upper 문서 읽기. cresvoid read mini\n", "다음 할 일")
        .expectNoOutput("mini: 작업 공간 선택", "트랙 작업 공간은 지울 내용이 아니다")
        .expectNoOutput("처음부터 다시 풀려면", "작업 공간 선택이 남으면 새 경로를 권하지 않는다")
    val backupsDir = sandbox.root.resolve("practice/progress/backups")
    val firstBackup = backupsDir.listDirectoryEntries().single()
    withClue("사본 이름: ${firstBackup.name}") { Regex("progress-[0-9]{8}-[0-9]{6}\\.json").matches(firstBackup.name) shouldBe true }
    withClue("사본은 지우기 전 기록과 같다") { Files.readString(firstBackup) shouldBe before }
    withClue("단계 기록을 지운다") { sandbox.stage("mini", "01-upper") shouldBe StageProgress() }
    withClue("트랙 작업 공간 선택은 남는다") { sandbox.progress().tracks["mini"]?.solutionDir shouldBe "practice/mini" }
    withClue("다른 트랙은 그대로") { (sandbox.stage("story", "01-tide").quiz.completedAt != null) shouldBe true }
    withClue("작업 공간은 남는다") { sandbox.exists("practice/mini/run.sh") shouldBe true }

    sandbox.cresvoid("reset", "mini", "-yes").expectExit(0, "트랙 초기화")
        .expectOutput("지울 진행 기록:\n  mini: 작업 공간 선택(practice/mini)\n", "트랙 내용")
        .expectOutput(
            "작업 공간 파일은 그대로 있어 cresvoid start 는 그 작업 공간을 다시 고릅니다. 처음부터 다시 풀려면: cresvoid start mini -dir <새 경로>\n" +
                "다음: 작업 공간 만들기. cresvoid start mini\n",
            "새 경로 안내와 다음 할 일",
        )
    withClue("트랙 기록을 지운다") { ("mini" !in sandbox.progress().tracks) shouldBe true }
    sandbox.cresvoid("start", "mini").expectExit(0, "초기화 뒤 시작")
        .expectOutput("이전에 쓰던 작업 공간을 다시 고릅니다: practice/mini\n처음부터 다시 풀려면: cresvoid start mini -dir <새 경로>\n", "예전 작업 공간 안내")
    sandbox.cresvoid("start", "mini").expectExit(0, "기록된 작업 공간 다시 고르기")
        .expectOutput("이미 있는 작업 공간을 고릅니다: practice/mini\n", "기록된 작업 공간")
        .expectNoOutput("처음부터 다시 풀려면", "기록된 작업 공간은 새 경로를 권하지 않는다")
    sandbox.cresvoid("reset", "-stage", "1", "-yes").expectExit(1, "트랙 없는 단계")
        .expectOutput("-stage 는 트랙과 함께 씁니다. 예: cresvoid reset search-engine -stage 1", "거절 문구")
    sandbox.cresvoid("reset", "nope", "-yes").expectExit(1, "없는 트랙").expectOutput("없는 트랙입니다: nope", "거절 문구")
    sandbox.cresvoid("reset", "-yes").expectExit(0, "모두 초기화")
        .expectOutput("  mini: 작업 공간 선택(practice/mini)\n  story 01-tide: 문항 첫 시도 2/2, 문항 완료\n", "모든 트랙 내용")
        .expectOutput("작업 공간 파일은 그대로 있어 cresvoid start 는 그 작업 공간을 다시 고릅니다. 처음부터 다시 풀려면 -dir 로 새 경로를 주세요.\n", "새 경로 안내")
    withClue("빈 기록") { sandbox.progress() shouldBe Progress() }
    withClue("초기화마다 사본을 따로 남긴다") { backupsDir.listDirectoryEntries().size shouldBe 3 }
    sandbox.cresvoid("reset", "-yes").expectExit(0, "지울 것이 없음").expectOutput("지울 진행 기록이 없습니다.", "문구")
    withClue("지울 것이 없으면 사본을 남기지 않는다") { backupsDir.listDirectoryEntries().size shouldBe 3 }
    sandbox.cresvoid("reset", "-yes=nope").expectExit(1, "값을 받지 않는 옵션")
        .expectOutput("-yes 옵션은 값을 받지 않습니다", "옵션 오류")

    val broken = "{\"tracks\": {\"story\": "
    Files.writeString(progressFile, broken)
    val recovery = "진행 기록을 읽지 못했습니다: $progressFile: JSON 이 중간에 끝났습니다\n사본을 남기고 모든 기록을 지우려면: cresvoid reset\n"
    sandbox.cresvoid().expectExit(1, "읽을 수 없는 기록").expectOutput(recovery, "복구 안내")
    sandbox.cresvoid("reset", "story", "-yes").expectExit(1, "읽을 수 없는 기록의 트랙 초기화").expectOutput(recovery, "트랙 초기화의 복구 안내")
    sandbox.cresvoid("reset", "-yes").expectExit(0, "읽을 수 없는 기록 초기화")
        .expectOutput("지울 진행 기록:\n  읽을 수 없는 진행 기록 전체 (JSON 이 중간에 끝났습니다)\n", "지울 내용")
        .expectOutput("지웠습니다. 지우기 전 사본: practice/progress/backups/progress-", "사본 경로")
    withClue("읽을 수 없던 원본 사본") { (backupsDir.listDirectoryEntries().count { Files.readString(it) == broken }) shouldBe 1 }
    withClue("비운 기록") { sandbox.progress() shouldBe Progress() }
}

private fun resetInTerminal(sandbox: Sandbox) {
    sandbox.addReadingTrack()
    sandbox.cresvoid("quiz", "story", input = "3\n2\n").expectExit(0, "독해 문항")
    val declined = answerInTerminal(sandbox, "n", "reset", "story")
    withClue("지울 내용") { declined shouldContain "  story 01-tide: 문항 첫 시도 2/2, 문항 완료" }
    withClue("확인 질문") { declined shouldContain "지울까요? [y/N]" }
    withClue("거절") { declined shouldContain "지우지 않았습니다." }
    withClue("거절하면 기록이 남는다") { (sandbox.stage("story", "01-tide").quiz.completedAt != null) shouldBe true }
    withClue("거절하면 사본을 남기지 않는다") { !sandbox.exists("practice/progress/backups") shouldBe true }
    val accepted = answerInTerminal(sandbox, "y", "reset", "story")
    withClue("지운 문구") { accepted shouldContain "지웠습니다. 지우기 전 사본: practice/progress/backups/progress-" }
    withClue("확인하면 지운다") { ("story" !in sandbox.progress().tracks) shouldBe true }
}

private fun terminalSignals(sandbox: Sandbox) {
    sandbox.addBuildTrack()
    sandbox.cresvoid("start", "mini").expectExit(0, "시작")
    sandbox.cresvoid("quiz", "mini", input = "2\nABC\n").expectExit(0, "문항")
    val slow = "1.0${System.nanoTime() % 1000}"
    sandbox.file("practice/mini/run.sh", "#!/bin/sh\nsleep $slow\nexec tr a-z A-Z\n", executable = true)
    val quit = typeInTerminal(sandbox, "\u001c", listOf("test", "mini", "-stage", "1", "-seed", "7"), "풀이 프로그램 실행") {
        processWithArgument(slow)
    }
    withClue("Ctrl-\\ 뒤 종료 코드\n${quit.output}") { quit.exitCode shouldBe 0 }
    withClue("Ctrl-\\ 를 눌러도 테스트는 이어진다") { quit.output shouldContain "✓ 01-upper 까지 모두 통과했습니다" }

    val hanging = "45.5${System.nanoTime() % 1000}"
    sandbox.file("practice/mini/run.sh", "#!/bin/sh\nsleep $hanging &\nsleep $hanging\n", executable = true)
    val interrupted = typeInTerminal(sandbox, "\u0003", listOf("test", "mini", "-stage", "1", "-seed", "7"), "풀이 프로그램 실행") {
        processWithArgument(hanging)
    }
    withClue("Ctrl-C 뒤 종료 코드\n${interrupted.output}") { interrupted.exitCode shouldBe 130 }
    withClue("중단 문구") { interrupted.output shouldContain "중단했습니다." }
    waitUntil("sleep 종료") { !processWithArgument(hanging) }
}

private class TerminalRun(val exitCode: Int, val output: String)

private fun answerInTerminal(sandbox: Sandbox, answer: String, vararg args: String): String {
    val run = typeInTerminal(sandbox, "$answer\n", args.toList(), "확인 질문") { output -> "[y/N]" in output }
    withClue("종료 코드") { run.exitCode shouldBe 0 }
    return run.output
}

private fun typeInTerminal(
    sandbox: Sandbox,
    keys: String,
    args: List<String>,
    readyWhat: String,
    isReady: (CharSequence) -> Boolean,
): TerminalRun {
    val process = sandbox.terminalCommand(*args.toTypedArray()).start()
    val output = StringBuffer()
    val reader = Thread {
        process.inputStream.reader(Charsets.UTF_8).use { stream ->
            while (true) {
                val char = stream.read()
                if (char < 0) break
                output.append(char.toChar())
            }
        }
    }.apply { start() }
    try {
        waitUntil(readyWhat) { isReady(output) }
        process.outputStream.write(keys.toByteArray())
        process.outputStream.flush()
        withClue("입력한 뒤 끝나야 한다") { process.waitFor(60, TimeUnit.SECONDS) shouldBe true }
    } finally {
        process.destroyForcibly()
        process.outputStream.close()
    }
    reader.join(5000)
    return TerminalRun(process.exitValue(), output.toString().replace("\r\n", "\n"))
}

private fun sendSignal(name: String, process: Process) {
    ProcessBuilder("kill", "-$name", process.pid().toString()).start().waitFor()
}

private fun processWithArgument(argument: String): Boolean =
    ProcessHandle.allProcesses().anyMatch { handle -> handle.info().arguments().map { argument in it }.orElse(false) }

private fun waitUntil(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
    while (!condition()) {
        if (System.nanoTime() > deadline) throw AssertionError("$what 을 기다리다 시간이 지났습니다")
        Thread.sleep(50)
    }
}
