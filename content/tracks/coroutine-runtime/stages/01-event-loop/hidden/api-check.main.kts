import java.io.File
import java.nio.file.Files

val program = File(args.single()).canonicalFile
val source = File(__FILE__.parentFile, "ApiProbe.kt")
val app = File(program, "build/app.jar")
check(app.isFile) { "빌드된 Kotlin jar가 없다" }

fun runCommand(command: List<String>): Int {
    val builder = ProcessBuilder(command).redirectErrorStream(true)
    builder.environment().remove("KOTLIN_RUNNER")
    val process = builder.start()
    val output = process.inputStream.bufferedReader().readText()
    val code = process.waitFor()
    if (code != 0) System.err.print(output)
    return code
}

val work = Files.createTempDirectory("cresvoid-api-").toFile()
try {
    val probe = File(work, "probe.jar")
    check(runCommand(listOf("kotlinc", source.path, "-classpath", app.path, "-include-runtime", "-d", probe.path)) == 0)
    check(runCommand(listOf("java", "-cp", "${probe.path}${File.pathSeparator}${app.path}", "ApiProbeKt")) == 0)
} finally {
    work.deleteRecursively()
}
