package cresvoid.runner

import cresvoid.CresvoidError
import cresvoid.content.Stage
import cresvoid.content.TestCase
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import kotlin.io.path.isRegularFile

private val generatorTimeout: Duration = Duration.ofSeconds(60)
private val referenceTimeout: Duration = Duration.ofSeconds(120)
private val buildTimeout: Duration = Duration.ofMinutes(5)

class Program(val dir: Path, val name: String)

class ProgramFailure(message: String, val stderrTail: List<String>) : CresvoidError(message)

fun referenceProgram(stage: Stage) = Program(stage.solutionDir, "참조 풀이")

fun codeProgram(stage: Stage) = Program(stage.codeDir, "코드")

private data class GeneratorKey(val hiddenDir: Path, val testName: String, val seed: Long)

private data class OracleKey(val programDir: Path, val inputDigest: String)

class Harness(private val runner: ProcessRunner) {
    private val builds = mutableMapOf<Path, String?>()
    private val generated = mutableMapOf<GeneratorKey, ByteArray>()
    private val generatorFailures = mutableMapOf<GeneratorKey, CresvoidError>()
    private val oracles = mutableMapOf<OracleKey, List<String>>()

    fun buildFailure(dir: Path): String? {
        if (dir in builds) return builds[dir]
        val failure = runBuildScript(dir)
        builds[dir] = failure
        return failure
    }

    fun inputs(stage: Stage, test: TestCase, seeds: List<Long>): List<CaseInput> {
        val command = test.gen ?: return listOf(CaseInput(joinInput(test.input.orEmpty()), seed = 0, generated = false))
        return seeds.map { seed -> CaseInput(generate(stage, test.name, command, seed), seed, generated = true) }
    }

    fun expected(stage: Stage, test: TestCase, input: ByteArray): List<String> =
        test.expected?.let(::normalizeLines) ?: oracle(stage, input)

    fun oracle(stage: Stage, input: ByteArray): List<String> = output(referenceProgram(stage), stage, input)

    fun output(program: Program, stage: Stage, input: ByteArray): List<String> {
        val key = OracleKey(program.dir, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)))
        oracles[key]?.let { return it }
        buildFailure(program.dir)?.let { throw CresvoidError(it) }
        val result = runner.run(program.dir, listOf(program.dir.resolve("run.sh").toString()), input, referenceTimeout)
        if (!result.succeeded || result.truncated) {
            throw ProgramFailure("${program.name}가 실패했습니다 (${result.describeExit()}): ${stage.dir}", tailLines(result.stderr, 20))
        }
        return normalizeOutput(result.stdout).also { oracles[key] = it }
    }

    fun run(dir: Path, input: ByteArray, timeout: Duration): ProcessResult =
        runner.run(dir, listOf(dir.resolve("run.sh").toString()), input, timeout)

    private fun runBuildScript(dir: Path): String? {
        val script = dir.resolve("build.sh")
        if (!script.isRegularFile()) return null
        val result = runner.run(dir, listOf(script.toString()), ByteArray(0), buildTimeout)
        if (result.succeeded) return null
        val output = tailLines(result.stdout + result.stderr, 30).joinToString("\n")
        return "빌드 스크립트가 실패했습니다 (${result.describeExit()}): $script\n$output"
    }

    private fun generate(stage: Stage, testName: String, command: List<String>, seed: Long): ByteArray {
        val key = GeneratorKey(stage.hiddenDir, testName, seed)
        generated[key]?.let { return it }
        generatorFailures[key]?.let { throw it }
        val result = runner.run(stage.hiddenDir, command + listOf("--seed", seed.toString()), ByteArray(0), generatorTimeout)
        val failure = when {
            !result.succeeded ->
                ProgramFailure("입력 생성기가 실패했습니다 (${result.describeExit()}): ${stage.dir}/$testName", tailLines(result.stderr, 20))
            result.truncated -> CresvoidError("입력 생성기 출력이 64MB 를 넘었습니다: ${stage.dir}/$testName")
            else -> null
        }
        if (failure != null) {
            generatorFailures[key] = failure
            throw failure
        }
        val output = result.stdout
        val input = if (output.isNotEmpty() && output.last() != '\n'.code.toByte()) output + '\n'.code.toByte() else output
        generated[key] = input
        return input
    }
}
