import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class Json(private val text: String) {
    private var position = 0

    fun parse(): Any? = value().also { skipWhitespace() }

    private fun value(): Any? {
        skipWhitespace()
        return when (text[position]) {
            '{' -> members()
            '[' -> items()
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> number()
        }
    }

    private fun members(): Map<String, Any?> {
        position++
        val members = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (text[position] == '}') return members.also { position++ }
        while (true) {
            skipWhitespace()
            val key = string()
            skipWhitespace()
            position++
            members[key] = value()
            skipWhitespace()
            if (text[position++] == '}') return members
        }
    }

    private fun items(): List<Any?> {
        position++
        val items = mutableListOf<Any?>()
        skipWhitespace()
        if (text[position] == ']') return items.also { position++ }
        while (true) {
            items += value()
            skipWhitespace()
            if (text[position++] == ']') return items
        }
    }

    private fun string(): String {
        position++
        val builder = StringBuilder()
        while (text[position] != '"') {
            val char = text[position++]
            if (char != '\\') {
                builder.append(char)
                continue
            }
            when (val escape = text[position++]) {
                'n' -> builder.append('\n')
                't' -> builder.append('\t')
                'r' -> builder.append('\r')
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'u' -> builder.append(text.substring(position, position + 4).toInt(16).toChar()).also { position += 4 }
                else -> builder.append(escape)
            }
        }
        position++
        return builder.toString()
    }

    private fun number(): Number {
        val start = position
        while (position < text.length && text[position] in "+-0123456789.eE") position++
        val literal = text.substring(start, position)
        return literal.toLongOrNull() ?: literal.toDouble()
    }

    private fun literal(word: String, result: Any?): Any? {
        position += word.length
        return result
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position].isWhitespace()) position++
    }
}

fun File.readJsonObject(): Map<*, *> = Json(readText()).parse() as Map<*, *>

fun Map<*, *>.objects(name: String): List<Map<*, *>> = (this[name] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

class Run(val exitCode: Int, val output: String)

fun ProcessBuilder.withoutKotlinRunner(): ProcessBuilder = apply { environment().remove("KOTLIN_RUNNER") }

val scriptFile: File = ProcessHandle.current().info().arguments().orElse(emptyArray())
    .lastOrNull { it.endsWith(".main.kts") }
    ?.let { File(it).absoluteFile }
    ?: __FILE__.absoluteFile
val projectRoot: File = (args.firstOrNull()?.let(::File) ?: scriptFile.parentFile.parentFile).canonicalFile
val launcher = File(projectRoot, "engine/bin/cresvoid")
val workspace: File = Files.createTempDirectory("cresvoid-e2e-").toFile()
val problems = mutableListOf<String>()

fun log(line: String = "") = println(line)

fun expect(condition: Boolean, problem: String) {
    if (condition) return
    problems += problem
    log("!! $problem")
}

fun cresvoid(vararg arguments: String, input: String = ""): Run {
    val command = "cresvoid ${arguments.joinToString(" ")}"
    log("$ $command")
    val outputFile = Files.createTempFile("cresvoid-e2e-", ".out").toFile()
    try {
        val process = ProcessBuilder(listOf(launcher.path) + arguments)
            .directory(workspace)
            .redirectErrorStream(true)
            .redirectOutput(outputFile)
            .withoutKotlinRunner()
            .apply { environment()["PWD"] = workspace.path }
            .start()
        process.outputStream.use { it.write(input.toByteArray()) }
        val finished = process.waitFor(30, TimeUnit.MINUTES)
        if (!finished) process.destroyForcibly().waitFor()
        val output = outputFile.readText()
        output.lineSequence().forEach { log("  | $it") }
        expect(finished, "$command 가 30분 안에 끝나지 않아 멈췄습니다")
        log("  exit ${process.exitValue()}")
        return Run(process.exitValue(), output)
    } finally {
        outputFile.delete()
    }
}

fun Run.expectSuccess(part: String, what: String): Run {
    expect(exitCode == 0 && part in output, "$what: 종료 코드 0 과 <$part> 를 기대했습니다 (종료 코드 $exitCode)")
    return this
}

fun copyContent(source: Path, target: Path) {
    Files.walk(source).use { paths ->
        paths.forEach { path ->
            val destination = target.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) Files.createDirectories(destination)
            else Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES)
        }
    }
}

fun quizAnswers(stageDir: File): String =
    File(stageDir, "hidden/quiz.json").readJsonObject().objects("questions").joinToString("") { question ->
        val answer = if (question["type"] == "choice") question["answer"].toString() else (question["answers"] as List<*>).first().toString()
        "$answer\n"
    }

fun solveQuiz(track: String, stage: Map<*, *>, number: Int, stagesDir: File) {
    val dir = stage["dir"] as String
    cresvoid("read", track, "-stage", "$number").expectSuccess("다 읽었으면 문항을 푸세요", "$track $dir 읽기")
    cresvoid("quiz", track, "-stage", "$number", input = quizAnswers(File(stagesDir, dir)))
        .expectSuccess("이 단계 문항을 모두 마쳤습니다.", "$track $dir 문항")
}

fun trackProgress(track: String): Map<*, *>? {
    val progress = File(workspace, "practice/progress/progress.json").takeIf { it.isFile }?.readJsonObject()
    return (progress?.get("tracks") as? Map<*, *>)?.get(track) as? Map<*, *>
}

val failedTestLine = Regex("""^  ✗ (\S+)$""", RegexOption.MULTILINE)

fun expectHiddenCaseNotShown(what: String, stageDir: File, run: Run) {
    val failedName = failedTestLine.find(run.output)?.groupValues?.get(1)
    if (failedName == null) {
        expect(false, "$what 실패 출력에서 실패한 테스트 이름을 찾지 못했습니다")
        return
    }
    val test = File(stageDir, "hidden/tests.json").readJsonObject().objects("tests").firstOrNull { it["name"] == failedName }
    if (test == null) {
        expect(false, "$what 실패한 테스트 $failedName 가 hidden/tests.json 에 없습니다")
        return
    }
    val hiddenLines = listOf("input", "expected").flatMap { field -> (test[field] as? List<*>).orEmpty().map { it.toString() } }
    val shown = hiddenLines.filter { it.length >= 8 && it in run.output }
    expect(shown.isEmpty(), "$what 실패 출력에 $failedName 의 숨은 입력이나 기대 출력이 보입니다: ${shown.joinToString(" | ")}")
}

fun fileHashesOutsideBuildDirs(): Map<String, Int> =
    workspace.walkTopDown().onEnter { it.name != "build" }.filter { it.isFile }
        .associate { it.relativeTo(workspace).path to it.readBytes().contentHashCode() }

fun checkStarterFailure(track: String, number: Int, stageDir: File) {
    val what = "$track ${stageDir.name} 시작 코드"
    val hashesBefore = fileHashesOutsideBuildDirs()
    val run = cresvoid("test", track, "-stage", "$number")
    expect(run.exitCode == 1 && "✗" in run.output, "${what}가 테스트에서 실패해야 합니다")
    expect("참조 풀이를 보려면: cresvoid solution $track -stage $number" in run.output, "$what 실패 출력에 참조 풀이 보기 안내가 없습니다")
    expectHiddenCaseNotShown(what, stageDir, run)
    val hashesAfter = fileHashesOutsideBuildDirs()
    val changed = (hashesBefore.keys + hashesAfter.keys).filter { hashesBefore[it] != hashesAfter[it] }.sorted()
    expect(changed.isEmpty(), "$what 실패 뒤 build/ 밖에서 생기거나 바뀌거나 사라진 파일이 있습니다: ${changed.joinToString(", ")}")
}

fun runBuildTrack(track: String, stages: List<Map<*, *>>, trackDir: File) {
    val stagesDir = File(trackDir, "stages")
    cresvoid("start", track).expectSuccess("작업 공간을 만들었습니다", "$track 작업 공간")
    val solutionDir = trackProgress(track)?.get("solution_dir") as? String
    if (solutionDir == null) {
        expect(false, "$track 작업 공간이 진행 기록에 없어 나머지 흐름을 건너뜁니다")
        return
    }
    val firstDir = stages.first()["dir"] as String
    val early = cresvoid("solution", track).expectSuccess("content/tracks/$track/stages/$firstDir/hidden/solution", "$track 통과 전 참조 풀이")
    if (File(stagesDir, "$firstDir/hidden/solution/src/main/kotlin").isDirectory) early.expectSuccess("==> ", "$track 통과 전 참조 풀이 소스")
    val referenceSolution = File(stagesDir, "${stages.last()["dir"]}/hidden/solution")
    stages.forEachIndexed { index, stage ->
        val number = index + 1
        val dir = stage["dir"] as String
        solveQuiz(track, stage, number, stagesDir)
        if (index == 0) {
            checkStarterFailure(track, number, File(stagesDir, dir))
            File(referenceSolution, "build.sh").takeIf { it.isFile }?.let { build ->
                val built = ProcessBuilder(build.path).inheritIO().withoutKotlinRunner().start().waitFor()
                expect(built == 0, "$track 마지막 단계 참조 풀이 빌드 실패")
            }
            File(workspace, "$solutionDir/run.sh").writeText("#!/bin/sh\nexec '${File(referenceSolution, "run.sh").path}'\n")
        }
        cresvoid("test", track, "-stage", "$number").expectSuccess("$dir 까지 모두 통과했습니다", "$track $dir 테스트")
    }
    val lastDir = stages.last()["dir"] as String
    val shown = cresvoid("solution", track).expectSuccess("content/tracks/$track/stages/$lastDir/hidden/solution", "$track 마지막 단계 참조 풀이")
    if (File(referenceSolution, "src/main/kotlin").isDirectory) shown.expectSuccess("==> ", "$track 마지막 단계 참조 풀이 소스")
}

fun runCodeTrack(track: String, stages: List<Map<*, *>>, trackDir: File) {
    val stagesDir = File(trackDir, "stages")
    stages.forEachIndexed { index, stage ->
        val number = index + 1
        val dir = stage["dir"] as String
        cresvoid("read", track, "-stage", "$number")
            .expectSuccess("코드: content/tracks/$track/stages/$dir/code", "$track $dir 코드 디렉터리 안내")
            .expectSuccess("==> ", "$track $dir 줄 번호를 붙인 코드")
        cresvoid("quiz", track, "-stage", "$number", input = quizAnswers(File(stagesDir, dir)))
            .expectSuccess("이 단계 문항을 모두 마쳤습니다.", "$track $dir 문항")
    }
}

fun replaceSources(solution: File, stageWorkspace: File) {
    val target = File(stageWorkspace, "src")
    target.deleteRecursively()
    File(solution, "src").copyRecursively(target)
}

fun runFixTrack(track: String, stages: List<Map<*, *>>, trackDir: File) {
    val stagesDir = File(trackDir, "stages")
    stages.forEachIndexed { index, stage ->
        val number = index + 1
        val dir = stage["dir"] as String
        cresvoid("start", track, "-stage", "$number").expectSuccess("작업 공간을 만들었습니다", "$track $dir 작업 공간")
        val stageRecords = trackProgress(track)?.get("stages") as? Map<*, *>
        val solutionDir = (stageRecords?.get(dir) as? Map<*, *>)?.get("solution_dir") as? String
        if (solutionDir == null) {
            expect(false, "$track $dir 작업 공간이 진행 기록에 없어 이 단계를 건너뜁니다")
            return@forEachIndexed
        }
        cresvoid("read", track, "-stage", "$number").expectSuccess("다음:", "$track $dir 읽기")
        checkStarterFailure(track, number, File(stagesDir, dir))
        replaceSources(File(stagesDir, "$dir/hidden/solution"), File(workspace, solutionDir))
        cresvoid("test", track, "-stage", "$number").expectSuccess("$dir 테스트를 모두 통과했습니다", "$track $dir 테스트")
        cresvoid("solution", track, "-stage", "$number")
            .expectSuccess("--- starter/", "$track $dir 참조 풀이 차이의 시작 코드 쪽")
            .expectSuccess("+++ hidden/solution/", "$track $dir 참조 풀이 차이의 참조 풀이 쪽")
    }
}

fun runReadingTrack(track: String, stages: List<Map<*, *>>, trackDir: File) {
    stages.forEachIndexed { index, stage -> solveQuiz(track, stage, index + 1, File(trackDir, "stages")) }
}

fun finishedTracks(status: Run): Int = Regex("다음: 모든 단계를 마쳤습니다.").findAll(status.output).count()

fun backupCount(): Int = File(workspace, "practice/progress/backups").list().orEmpty().size

fun workspaceFiles(): List<String> {
    val practice = File(workspace, "practice")
    val progress = File(practice, "progress")
    return practice.walkTopDown().onEnter { it != progress }.filter { it.isFile }.map { it.relativeTo(workspace).path }.toList().sorted()
}

fun runResets(tracks: List<String>) {
    val first = tracks.firstOrNull() ?: return
    val keptFiles = workspaceFiles()
    cresvoid("reset", first, "-yes").expectSuccess("지웠습니다. 지우기 전 사본: practice/progress/backups/progress-", "$first 초기화")
    expect(trackProgress(first) == null, "$first 초기화 뒤에도 기록이 남았습니다")
    expect(backupCount() == 1, "$first 초기화 사본은 하나여야 합니다 (${backupCount()}개)")
    expect(finishedTracks(cresvoid()) == tracks.size - 1, "$first 만 처음으로 돌아가야 합니다")
    val remainingRecords = tracks.size > 1
    val all = cresvoid("reset", "-yes")
    if (remainingRecords) {
        all.expectSuccess("지웠습니다. 지우기 전 사본: practice/progress/backups/progress-", "모두 초기화")
    } else {
        all.expectSuccess("지울 진행 기록이 없습니다.", "모두 초기화")
    }
    val expectedBackups = if (remainingRecords) 2 else 1
    expect(backupCount() == expectedBackups, "초기화 사본은 ${expectedBackups}개여야 합니다 (${backupCount()}개)")
    val progressTracks = File(workspace, "practice/progress/progress.json").readJsonObject()["tracks"] as? Map<*, *>
    expect(progressTracks.isNullOrEmpty(), "모두 초기화한 뒤에도 기록이 남았습니다")
    expect(finishedTracks(cresvoid()) == 0, "모든 트랙이 처음으로 돌아가야 합니다")
    expect(workspaceFiles() == keptFiles, "초기화는 practice 아래 작업 공간 파일을 지우지 않아야 합니다")
}

log("프로젝트: $projectRoot")
log("작업 디렉터리: $workspace")
expect(launcher.canExecute(), "실행할 수 있는 ${launcher.path} 가 없습니다")
copyContent(File(projectRoot, "content/tracks").toPath(), File(workspace, "content/tracks").toPath())
val trackDirs = File(workspace, "content/tracks").listFiles { file -> File(file, "track.json").isFile }.orEmpty().sortedBy { it.name }
expect(trackDirs.isNotEmpty(), "content/tracks 에 트랙이 없습니다")

for (trackDir in trackDirs) {
    val track = trackDir.name
    log()
    log("== $track")
    cresvoid("validate", track).expectSuccess("검증 통과", "$track 검증")
    val definition = File(trackDir, "track.json").readJsonObject()
    val stages = definition.objects("stages")
    when (val kind = definition["kind"]) {
        "build" -> runBuildTrack(track, stages, trackDir)
        "reading" -> runReadingTrack(track, stages, trackDir)
        "code" -> runCodeTrack(track, stages, trackDir)
        "fix" -> runFixTrack(track, stages, trackDir)
        else -> expect(false, "e2e 가 모르는 트랙 종류입니다: $track ($kind)")
    }
}

log()
log("== 상태")
val status = cresvoid()
expect(status.exitCode == 0 && finishedTracks(status) == trackDirs.size, "모든 트랙이 끝나야 합니다")

log()
log("== 초기화")
runResets(trackDirs.map { it.name })

log()
if (problems.isEmpty()) {
    workspace.deleteRecursively()
    log("e2e 통과: 트랙 ${trackDirs.size}개")
    exitProcess(0)
}
log("e2e 실패: ${problems.size}건. 작업 디렉터리를 남겼습니다: $workspace")
problems.forEach { log("  ✗ $it") }
exitProcess(1)
