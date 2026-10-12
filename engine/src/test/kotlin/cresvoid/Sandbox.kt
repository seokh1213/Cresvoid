package cresvoid

import cresvoid.progress.FileProgressRepository
import cresvoid.progress.Progress
import cresvoid.progress.StageProgress
import cresvoid.project.Project
import io.kotest.assertions.withClue
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

val engineJar: Path = Path(System.getProperty("cresvoid.jar"))

class Run(val exitCode: Int, val stdout: String, val stderr: String) {
    val output: String get() = stdout + stderr
}

class Sandbox(private val jar: Path) : AutoCloseable {
    val root: Path = Files.createTempDirectory(Path("/tmp"), "cresvoid-it-")
    private val java = Path(System.getProperty("java.home"), "bin", "java").toString()

    fun file(relative: String, content: String, executable: Boolean = false) {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        if (executable) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    fun exists(relative: String): Boolean = Files.exists(root.resolve(relative))

    fun repository(): FileProgressRepository = FileProgressRepository(Project(root).progressDir, Clock.systemDefaultZone())

    fun progress(): Progress = repository().load()

    fun stage(trackId: String, stageDir: String): StageProgress = progress().stageOrEmpty(trackId, stageDir)

    fun command(vararg args: String, workingDir: Path = root, cresvoidRoot: Path? = null): ProcessBuilder {
        val builder = ProcessBuilder(listOf(java, "-jar", jar.toString()) + args).directory(workingDir.toFile())
        val environment = builder.environment()
        environment["PWD"] = workingDir.toString()
        environment.remove("CRESVOID_ROOT")
        if (cresvoidRoot != null) environment["CRESVOID_ROOT"] = cresvoidRoot.toString()
        return builder
    }

    fun terminalCommand(vararg args: String): ProcessBuilder {
        val inner = command(*args)
        val builder = ProcessBuilder(listOf("script", "-q", "/dev/null") + inner.command())
            .directory(root.toFile())
            .redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(inner.environment())
        builder.environment()["NO_COLOR"] = "1"
        return builder
    }

    fun cresvoid(vararg args: String, input: String = "", workingDir: Path = root, cresvoidRoot: Path? = null): Run {
        val stdout = File.createTempFile("cresvoid-it-", ".out")
        val stderr = File.createTempFile("cresvoid-it-", ".err")
        try {
            val process = command(*args, workingDir = workingDir, cresvoidRoot = cresvoidRoot)
                .redirectOutput(stdout)
                .redirectError(stderr)
                .start()
            process.outputStream.use { it.write(input.toByteArray()) }
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw AssertionError("cresvoid ${args.joinToString(" ")} 가 끝나지 않았습니다")
            }
            return Run(process.exitValue(), stdout.readText(), stderr.readText())
        } finally {
            stdout.delete()
            stderr.delete()
        }
    }

    override fun close() {
        root.toFile().deleteRecursively()
    }
}

fun Run.expectExit(code: Int, what: String): Run {
    if (exitCode != code) throw AssertionError("$what: 종료 코드 기대 $code, 실제 $exitCode\n--- 표준 출력\n$stdout--- 표준 오류\n$stderr")
    return this
}

fun Run.expectOutput(part: String, what: String): Run {
    withClue(what) { output shouldContain part }
    return this
}

fun Run.expectNoOutput(part: String, what: String): Run {
    if (part in output) throw AssertionError("$what: <$part> 가 없어야 합니다:\n$output")
    return this
}
