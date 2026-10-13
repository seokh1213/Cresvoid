package cresvoid.validation

import cresvoid.CresvoidError
import cresvoid.FailureShown
import cresvoid.content.CodeLocation
import cresvoid.content.CodeSources
import cresvoid.content.ContentLibrary
import cresvoid.content.Document
import cresvoid.content.Question
import cresvoid.content.QuestionType
import cresvoid.content.Quiz
import cresvoid.content.QuizRules
import cresvoid.content.ShortQuestions
import cresvoid.content.Stage
import cresvoid.content.TestCase
import cresvoid.content.TestScope
import cresvoid.content.TestSuite
import cresvoid.content.Track
import cresvoid.content.TrackKind
import cresvoid.content.collapseSpaces
import cresvoid.content.containsQuote
import cresvoid.content.isCodeLocation
import cresvoid.content.loadDocument
import cresvoid.content.loadQuiz
import cresvoid.content.loadTestSuite
import cresvoid.describeIoError
import cresvoid.project.Project
import cresvoid.project.copyTree
import cresvoid.project.isExecutableFile
import cresvoid.runner.CaseInput
import cresvoid.runner.FailureKind
import cresvoid.runner.Harness
import cresvoid.runner.ProcessResult
import cresvoid.runner.ProcessRunner
import cresvoid.runner.Program
import cresvoid.runner.ProgramFailure
import cresvoid.runner.Verdict
import cresvoid.runner.checkEditScope
import cresvoid.runner.countChangedLines
import cresvoid.runner.firstDifference
import cresvoid.runner.joinInput
import cresvoid.runner.judge
import cresvoid.runner.normalizeOutput
import cresvoid.runner.starterDiff
import cresvoid.runner.tailLines
import cresvoid.study.stagesToRun
import cresvoid.terminal.Console
import cresvoid.terminal.dim
import cresvoid.terminal.formatElapsed
import cresvoid.terminal.green
import cresvoid.terminal.quoted
import cresvoid.terminal.red
import cresvoid.terminal.truncateDisplay
import cresvoid.terminal.yellow
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val testNamePattern = Regex("[a-z0-9]+(-[a-z0-9]+)*")
private val validationSeeds = listOf(1L, 2L)
private val codeRunTimeout: Duration = Duration.ofSeconds(10)

class StageContent(val stage: Stage, val document: Document?, val quiz: Quiz?, val suite: TestSuite?)

class RunnableStage(val stage: Stage, val suite: TestSuite)

class Validation(
    private val project: Project,
    private val library: ContentLibrary,
    val track: Track,
    private val runner: ProcessRunner,
    private val console: Console,
) {
    private val harness = Harness(runner)
    private val problems = mutableListOf<String>()

    var loadFailed = false
        private set

    fun report() {
        if (problems.isEmpty()) {
            console.println(green("\n검증 통과"))
            return
        }
        console.println(red("\n검증 실패: ${problems.size}건"))
        problems.forEach { console.println("  ✗ $it") }
        throw FailureShown()
    }

    fun loadStages(programs: (Stage) -> List<Path>): List<StageContent> = track.stages.map { stage ->
        if (!stage.path.isDirectory()) {
            failLoading("${project.rel(stage.path)} 디렉터리가 없습니다")
            return@map StageContent(stage, document = null, quiz = null, suite = null)
        }
        checkGradingFilesHidden(stage)
        if (track.kind == TrackKind.Build && stage.apiCheckPath.exists() && !stage.apiCheckPath.isRegularFile()) {
            failLoading("${project.rel(stage.apiCheckPath)} 가 일반 파일이 아닙니다")
        }
        val document = loadOrFail { loadDocument(stage.documentPath) }
        val quiz = if (track.kind.quizRules != null) loadOrFail { loadQuiz(stage) } else null
        if (track.kind.quizRules == null && stage.quizPath.exists()) {
            fail("${project.rel(stage.quizPath)}: ${track.kind.id} 트랙 단계에는 quiz.json 을 두지 않습니다")
        }
        val suite = if (track.kind.testScope != null) loadOrFail { loadTestSuite(stage) } else null
        for (program in programs(stage)) {
            val runScript = program.resolve("run.sh")
            if (!runScript.isExecutableFile()) failLoading("${project.rel(runScript)} 가 없거나 실행 권한이 없습니다")
        }
        StageContent(stage, document, quiz, suite)
    }

    fun skipProgramChecks() {
        console.println(yellow("파일을 읽지 못한 단계가 있어 실행 검증을 건너뜁니다."))
    }

    fun checkDocuments(contents: List<StageContent>) {
        for (content in contents) {
            val document = content.document ?: continue
            val where = project.rel(document.path)
            if (document.paragraphs.isEmpty()) fail("$where: 번호 문단이 없습니다")
            document.duplicates.forEach { fail("$where: 문단 번호 [$it] 가 겹칩니다") }
        }
    }

    fun checkQuizzes(contents: List<StageContent>) {
        val rules = track.kind.quizRules ?: return
        for (content in contents) {
            val quiz = content.quiz ?: continue
            val document = content.document ?: continue
            val code = if (track.kind.readsCode) CodeSources(content.stage.codeDir) else null
            val prefix = stageFile(content.stage, content.stage.quizPath)
            if (quiz.questions.isEmpty()) fail("$prefix: 문항이 없습니다")
            val seen = mutableSetOf<String>()
            for (question in quiz.questions) {
                val where = "$prefix ${question.id}"
                if (question.id.isEmpty()) fail("$prefix: id 가 비어 있습니다")
                if (!seen.add(question.id)) fail("$where: id 가 겹칩니다")
                if (question.prompt.isBlank()) fail("$where: prompt 가 비어 있습니다")
                if (question.explanation.isBlank()) fail("$where: explanation 이 비어 있습니다")
                checkQuestionShape(where, question, rules)
                checkRefs(where, document, question.refs, question.quote, code)
            }
        }
    }

    fun checkSuites(contents: List<StageContent>) {
        for (content in contents) {
            val suite = content.suite ?: continue
            val document = content.document ?: continue
            val prefix = stageFile(content.stage, content.stage.testsPath)
            if (suite.tests.isEmpty()) fail("$prefix: 테스트가 없습니다")
            if ((suite.timeoutSec ?: 0.0) < 0) fail("$prefix: timeout_sec 는 음수일 수 없습니다")
            val seen = mutableSetOf<String>()
            for (test in suite.tests) {
                val where = "$prefix ${test.name}"
                if (!testNamePattern.matches(test.name)) fail("$where: 이름은 영문 kebab-case 여야 합니다")
                if (!seen.add(test.name)) fail("$where: 이름이 겹칩니다")
                val hasGen = test.gen != null
                if ((test.input != null) == hasGen) fail("$where: input 과 gen 중 하나만 둡니다")
                if (test.gen?.isEmpty() == true) fail("$where: gen 명령이 비어 있습니다")
                if (hasGen && test.expected != null) fail("$where: gen 테스트에는 expected 를 쓰지 않습니다")
                if (!hasGen && (test.repeat ?: 0) != 0) fail("$where: repeat 는 gen 테스트에만 씁니다")
                if ((test.repeat ?: 0) < 0 || (test.timeoutSec ?: 0.0) < 0) {
                    fail("$where: repeat 와 timeout_sec 는 음수일 수 없습니다")
                }
                checkRefs(where, document, test.refs, test.quote, code = null)
            }
        }
    }

    fun checkReferences(stages: List<RunnableStage>) {
        val runnableByStage = stages.associateBy { it.stage }
        for (content in stages) {
            console.println("· ${content.stage.dir} 참조 풀이로 ${scopeLabel(content.stage)} 테스트를 실행합니다")
            for (earlier in track.stagesToRun(content.stage).mapNotNull(runnableByStage::get)) {
                val sameStage = earlier.stage == content.stage
                for (test in earlier.suite.tests) {
                    val timeout = earlier.suite.timeoutFor(test)
                    val cases = attempt { harness.inputs(earlier.stage, test, validationSeeds) } ?: continue
                    for (current in cases) {
                        if (sameStage && test.expected == null) {
                            checkDeterminism(content, test, current)
                            continue
                        }
                        val expected = attempt { harness.expected(earlier.stage, test, current.input) } ?: continue
                        val verdict = judge(runReference(content.stage, current.input, timeout), expected, timeout)
                        if (verdict is Verdict.Failed) {
                            fail(
                                "${content.stage.dir} 참조 풀이가 ${earlier.stage.dir}/${test.name}${seedLabel(current)} 에서 실패합니다: " +
                                    describeVerdict(verdict, expected),
                            )
                        }
                    }
                }
            }
        }
    }

    fun checkApiContracts(stages: List<RunnableStage>) {
        for (current in stages) {
            for (earlier in track.stagesToRun(current.stage)) {
                val result = harness.checkApi(earlier, current.stage.solutionDir) ?: continue
                console.println("· ${current.stage.dir} 참조 풀이의 ${earlier.dir} 공개 API 동작을 검사합니다")
                if (!result.succeeded || result.truncated) {
                    val summary = "${current.stage.dir} 참조 풀이가 ${earlier.dir} 공개 API 검사에 실패합니다 (${result.describeExit()})"
                    fail((listOf(summary) + tailLines(result.stderr, 10)).joinToString("\n"))
                }
            }
        }
    }

    fun checkShortAnswers(contents: List<StageContent>, programOf: (Stage) -> Program) {
        for (content in contents) {
            val quiz = content.quiz ?: continue
            val program = programOf(content.stage)
            for (question in quiz.questions) {
                if (question.type != QuestionType.SHORT || question.input.isEmpty() || question.answers.isEmpty()) continue
                val where = "${stageFile(content.stage, content.stage.quizPath)} ${question.id}"
                val output = try {
                    harness.output(program, content.stage, joinInput(question.input))
                } catch (error: CresvoidError) {
                    fail("$where: ${describe(error)}")
                    continue
                }
                if (output.size != 1) {
                    fail("$where: ${program.name} 출력이 ${output.size}줄입니다. 정확히 한 줄이어야 합니다")
                    continue
                }
                if (collapseSpaces(output[0]) != collapseSpaces(question.answers[0])) {
                    fail("$where: ${program.name} 출력 ${quoted(output[0])} 와 answers[0] ${quoted(question.answers[0])} 가 다릅니다")
                }
            }
        }
    }

    fun checkCodePrograms(contents: List<StageContent>) {
        for (content in contents) {
            val stage = content.stage
            val dir = stage.codeDir
            console.println("· ${stage.dir} 코드를 빌드하고 빈 입력으로 실행합니다")
            val buildFailure = harness.buildFailure(dir)
            if (buildFailure != null) {
                fail("${stage.dir} 코드 빌드 실패: $buildFailure")
                continue
            }
            val result = harness.run(dir, ByteArray(0), codeRunTimeout)
            if (!result.succeeded) {
                val summary = "${stage.dir} 코드를 빈 입력으로 실행하면 실패합니다 (${result.describeExit()})"
                fail((listOf(summary) + tailLines(result.stderr, 10)).joinToString("\n"))
            }
        }
    }

    fun checkStageProgression(stages: List<RunnableStage>) {
        for ((previous, current) in stages.zipWithNext()) {
            console.println("· ${previous.stage.dir} 참조 풀이로 ${current.stage.dir} 테스트를 실행합니다 (실패해야 정상)")
            val dir = previous.stage.solutionDir
            harness.buildFailure(dir)?.let {
                fail(it)
                continue
            }
            val failed = attempt { failingTests(current, dir).any() } ?: continue
            if (!failed) {
                fail("${previous.stage.dir} 참조 풀이가 ${current.stage.dir} 테스트를 모두 통과합니다. 새 단계가 새 동작을 확인하지 않습니다")
            }
        }
    }

    fun checkStarters(stages: List<RunnableStage>) {
        val languages = library.starterLanguages(track)
        if (languages.isEmpty()) {
            fail("${project.rel(track.startersDir)}: 시작 코드가 없습니다")
            return
        }
        val first = stages.first()
        for (language in languages) {
            console.println("· $language 시작 코드로 ${first.stage.dir} 테스트를 실행합니다 (실패해야 정상)")
            withStarterCopy(track.startersDir.resolve(language), "$language 시작 코드") { workspace ->
                val failed = attempt { failingTests(first, workspace).any() } ?: return@withStarterCopy
                if (!failed) fail("$language 시작 코드가 ${first.stage.dir} 테스트를 모두 통과합니다")
            }
        }
    }

    fun checkStageStarters(stages: List<RunnableStage>) {
        for (content in stages) {
            val stage = content.stage
            console.println("· ${stage.dir} 시작 코드로 ${stage.dir} 테스트를 실행합니다 (하나 이상 실패해야 정상)")
            withStarterCopy(stage.starterDir, "${stage.dir} 시작 코드") { workspace ->
                val failing = attempt { failingTests(content, workspace).map { it.name }.toList() } ?: return@withStarterCopy
                if (failing.isEmpty()) {
                    fail("${stage.dir} 시작 코드가 ${stage.dir} 테스트를 모두 통과합니다")
                } else {
                    console.println(dim("  실패하는 테스트: ${failing.joinToString(", ")}"))
                }
            }
            val changed = attempt { countChangedLines(starterDiff(stage, runner)) } ?: continue
            console.println(dim("  시작 코드와 참조 풀이 사이에 바뀐 줄: ${changed.total}줄 (추가 ${changed.added}, 삭제 ${changed.removed})"))
            attempt { checkEditScope(stage, stage.solutionDir) }
        }
    }

    private fun checkGradingFilesHidden(stage: Stage) {
        val gradingNames = listOf(stage.quizPath, stage.testsPath, stage.solutionDir, stage.apiCheckPath).map { it.name }
        val entries = loadOrFail { stage.path.listDirectoryEntries() } ?: return
        entries.filter { it.name in gradingNames || it.name.endsWith(".main.kts") }
            .sortedBy { it.name }
            .forEach { fail("${project.rel(it)}: 채점용 파일은 ${stage.hiddenDir.name}/ 으로 옮기세요") }
    }

    private fun stageFile(stage: Stage, file: Path): String = "${stage.dir}/${stage.path.relativize(file)}"

    private fun scopeLabel(stage: Stage): String = when (track.kind.testScope) {
        TestScope.CUMULATIVE -> "1~${stage.number}단계"
        TestScope.TARGET_STAGE, null -> stage.dir
    }

    private fun withStarterCopy(source: Path, label: String, check: (Path) -> Unit) {
        val workspace = try {
            Files.createTempDirectory("cresvoid-starter-")
        } catch (error: IOException) {
            fail(describeIoError(error))
            return
        }
        try {
            copyTree(source, workspace)
            if (!workspace.resolve("run.sh").isExecutableFile()) {
                fail("${label}에 실행 가능한 run.sh 가 없습니다")
                return
            }
            harness.buildFailure(workspace)?.let {
                fail("$label 빌드 실패: $it")
                return
            }
            check(workspace)
        } catch (error: IOException) {
            fail(describeIoError(error))
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    private fun checkQuestionShape(where: String, question: Question, rules: QuizRules) {
        when (question.type) {
            QuestionType.CHOICE -> {
                if (question.choices.size !in 2..5) fail("$where: choices 는 2~5개여야 합니다")
                if (rules.exactChoiceCount != null && question.choices.size != rules.exactChoiceCount) {
                    fail("$where: ${track.kind.id} 트랙 문항은 선지가 ${rules.exactChoiceCount}개여야 합니다")
                }
                if (question.answer !in 1..question.choices.size) fail("$where: answer ${question.answer} 가 선지 범위를 벗어납니다")
                if (question.answers.isNotEmpty() || question.input.isNotEmpty()) {
                    fail("$where: choice 문항에는 answers 와 input 을 쓰지 않습니다")
                }
            }
            QuestionType.SHORT -> {
                when (rules.shortQuestions) {
                    ShortQuestions.NOT_ALLOWED -> fail("$where: ${track.kind.id} 트랙 문항은 모두 choice 여야 합니다")
                    ShortQuestions.INPUT_REQUIRED ->
                        if (question.input.isEmpty()) fail("$where: ${track.kind.id} 트랙 short 문항에는 input 이 있어야 합니다")
                    ShortQuestions.INPUT_OPTIONAL -> Unit
                }
                if (question.answers.isEmpty()) fail("$where: answers 가 비어 있습니다")
                question.answers.filter { it.isBlank() }.forEach { fail("$where: 빈 정답이 있습니다") }
                if (question.choices.isNotEmpty() || question.answer != 0) {
                    fail("$where: short 문항에는 choices 와 answer 를 쓰지 않습니다")
                }
            }
            null -> fail("$where: type 은 choice 나 short 여야 합니다")
        }
    }

    private fun checkRefs(where: String, document: Document, refs: List<String>, quote: String, code: CodeSources?) {
        if (refs.isEmpty()) {
            fail("$where: refs 가 비어 있습니다")
            return
        }
        val passages = refs.mapNotNull { ref ->
            if (code != null && isCodeLocation(ref)) codePassage(where, code, ref) else paragraph(where, document, ref)
        }
        if (quote.isBlank()) {
            fail("$where: quote 가 비어 있습니다")
            return
        }
        if (!containsQuote(passages, quote)) {
            val codeRefCount = if (code == null) 0 else refs.count(::isCodeLocation)
            val place = when (codeRefCount) {
                0 -> "refs 문단"
                refs.size -> "refs 코드 범위"
                else -> "refs 문단과 코드 범위"
            }
            fail("$where: quote 가 ${place}에 없습니다: ${quoted(quote)}")
        }
    }

    private fun paragraph(where: String, document: Document, ref: String): String? {
        val paragraph = document.paragraphs[ref]
        if (paragraph == null) fail("$where: 문단 [$ref] 이 문서에 없습니다")
        return paragraph
    }

    private fun codePassage(where: String, code: CodeSources, ref: String): String? {
        val location = CodeLocation.parse(ref)
        if (location == null) {
            fail("$where: 코드 위치는 <파일>:<줄> 이나 <파일>:<시작 줄>-<끝 줄> 꼴이어야 합니다: $ref")
            return null
        }
        val lines = code.lines(location.file)
        if (lines == null) {
            fail("$where: 코드 파일이 없습니다: ${project.rel(code.root.resolve(location.file).normalize())}")
            return null
        }
        if (location.lastLine < location.firstLine) {
            fail("$where: 끝 줄이 시작 줄보다 앞입니다: $ref")
            return null
        }
        if (location.firstLine < 1 || location.lastLine > lines.size) {
            fail("$where: 코드 위치가 파일 범위 1~${lines.size}행을 벗어납니다: $ref")
            return null
        }
        return lines.subList(location.firstLine - 1, location.lastLine).joinToString("\n")
    }

    private fun checkDeterminism(content: RunnableStage, test: TestCase, current: CaseInput) {
        val first = attempt { harness.oracle(content.stage, current.input) } ?: return
        val timeout = content.suite.timeoutFor(test)
        val result = runReference(content.stage, current.input, timeout)
        val label = "${content.stage.dir}/${test.name}${seedLabel(current)}"
        if (!result.succeeded) {
            fail("$label: 참조 풀이가 실패했습니다 (${result.describeExit()})")
            return
        }
        if (firstDifference(first, normalizeOutput(result.stdout)) != null) fail("$label: 참조 풀이 출력이 실행할 때마다 다릅니다")
        val inputKilobytes = current.input.size / 1024
        console.println(dim("  ${test.name}${seedLabel(current)}: 참조 풀이 ${formatElapsed(result.duration)}, 제한 ${formatElapsed(timeout)}, 입력 ${inputKilobytes}KB"))
    }

    private fun failingTests(content: RunnableStage, dir: Path): Sequence<TestCase> =
        content.suite.tests.asSequence().filter { test ->
            harness.inputs(content.stage, test, validationSeeds.take(1)).any { current ->
                val expected = harness.expected(content.stage, test, current.input)
                val timeout = content.suite.timeoutFor(test)
                judge(harness.run(dir, current.input, timeout), expected, timeout) is Verdict.Failed
            }
        }

    private fun runReference(stage: Stage, input: ByteArray, timeout: Duration): ProcessResult {
        val dir = stage.solutionDir
        harness.buildFailure(dir)?.let { return ProcessResult.notStarted(it) }
        return harness.run(dir, input, timeout)
    }

    private fun describeVerdict(verdict: Verdict.Failed, expected: List<String>): String {
        if (verdict.kind != FailureKind.OUTPUT) return verdict.reason
        val expectedLine = expected.getOrNull(verdict.diffIndex) ?: "(줄 없음)"
        val actualLine = verdict.actual.getOrNull(verdict.diffIndex) ?: "(줄 없음)"
        return "${verdict.diffIndex + 1}번째 줄 기대 ${quoted(truncateDisplay(expectedLine, 120))}, " +
            "실제 ${quoted(truncateDisplay(actualLine, 120))}"
    }

    private fun seedLabel(current: CaseInput): String = if (current.generated) " (seed ${current.seed})" else ""

    private fun <T> loadOrFail(load: () -> T): T? =
        try {
            load()
        } catch (error: CresvoidError) {
            failLoading(error.message)
            null
        } catch (error: IOException) {
            failLoading(describeIoError(error))
            null
        }

    private fun <T> attempt(action: () -> T): T? =
        try {
            action()
        } catch (error: CresvoidError) {
            fail(describe(error))
            null
        }

    private fun describe(error: CresvoidError): String = when (error) {
        is ProgramFailure -> (listOf(error.message) + error.stderrTail).joinToString("\n")
        else -> error.message
    }

    private fun failLoading(problem: String) {
        fail(problem)
        loadFailed = true
    }

    private fun fail(problem: String) {
        problems += problem
    }
}
