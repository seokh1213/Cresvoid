import java.io.File
import java.nio.file.Files
import java.nio.file.Path

check(args.size == 1) { "Expected solution project path" }
val appJar = Path.of(args.single(), "build", "app.jar").toAbsolutePath()
val probeSource = Path.of("ApiProbe.kt").toAbsolutePath()
check(Files.isRegularFile(appJar)) { "Solution jar is missing" }
check(Files.isRegularFile(probeSource)) { "API probe source is missing" }

fun run(command: List<String>) {
    val builder = ProcessBuilder(command).inheritIO()
    builder.environment().remove("KOTLIN_RUNNER")
    val exitCode = builder.start().waitFor()
    check(exitCode == 0) { "${command.first()} exited with $exitCode" }
}

val temporaryDir = Files.createTempDirectory("cresvoid-api-probe-")
val probeJar = temporaryDir.resolve("probe.jar")
try {
    run(listOf("kotlinc", probeSource.toString(), "-classpath", appJar.toString(), "-include-runtime", "-d", probeJar.toString()))
    run(listOf("java", "-cp", "${probeJar}${File.pathSeparator}${appJar}", "ApiProbeKt"))
} finally {
    Files.deleteIfExists(probeJar)
    Files.deleteIfExists(temporaryDir)
}
