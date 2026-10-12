import java.io.File
import kotlin.system.exitProcess

val scriptFile: File = ProcessHandle.current().info().arguments().orElse(emptyArray())
    .lastOrNull { it.endsWith(".main.kts") }
    ?.let { File(it).absoluteFile }
    ?: __FILE__.absoluteFile
val checkOnly = "--check" in args
val root: File = (args.firstOrNull { !it.startsWith("--") }?.let(::File) ?: scriptFile.parentFile.parentFile.parentFile).canonicalFile
val templateDir = File(root, "content/templates/kotlin")
val templates: List<File> = templateDir.listFiles().orEmpty().filter { it.isFile && it.name != ".DS_Store" }.sortedBy { it.name }
val libTemplates: List<File> = File(templateDir, "lib").listFiles().orEmpty().filter { it.isFile && it.extension == "jar" }.sortedBy { it.name }

if (templates.isEmpty()) {
    System.err.println("템플릿이 없습니다: ${templateDir.relativeTo(root)}")
    exitProcess(1)
}

fun isKotlinProject(dir: File): Boolean = File(dir, "src/main/kotlin").isDirectory

fun matches(copy: File, template: File): Boolean =
    copy.isFile && copy.readBytes().contentEquals(template.readBytes()) && copy.canExecute() == template.canExecute()

val projects: List<File> = File(root, "content/tracks").walkTopDown()
    .onEnter { it.name != "build" && it.name != "src" }
    .filter { it.isDirectory && isKotlinProject(it) }
    .sortedBy { it.path }
    .toList()

val libProjects: List<File> = projects.filter { File(it, "lib").isDirectory }

val outdated = projects.flatMap { project ->
    templates.mapNotNull { template -> File(project, template.name).takeUnless { matches(it, template) }?.let { it to template } }
} + libProjects.flatMap { project ->
    libTemplates.mapNotNull { template -> File(project, "lib/${template.name}").takeUnless { matches(it, template) }?.let { it to template } }
}

val strayJars: List<File> = libProjects.flatMap { project ->
    File(project, "lib").listFiles().orEmpty().filter { jar -> jar.isFile && jar.extension == "jar" && libTemplates.none { it.name == jar.name } }
}

val projectSummary = "Kotlin 프로젝트 ${projects.size}개(lib 를 쓰는 프로젝트 ${libProjects.size}개)"

if (checkOnly) {
    outdated.forEach { (copy, template) -> println("템플릿과 다릅니다: ${copy.relativeTo(root)} (원본 ${template.relativeTo(root)})") }
    strayJars.forEach { println("템플릿에 없는 jar 입니다: ${it.relativeTo(root)}") }
    println("$projectSummary, 템플릿과 다른 파일 ${outdated.size + strayJars.size}개")
    exitProcess(if (outdated.isEmpty() && strayJars.isEmpty()) 0 else 1)
}

for ((copy, template) in outdated) {
    template.copyTo(copy, overwrite = true)
    copy.setExecutable(template.canExecute(), false)
    println("복사했습니다: ${copy.relativeTo(root)}")
}
for (jar in strayJars) {
    jar.delete()
    println("지웠습니다: ${jar.relativeTo(root)}")
}
println("$projectSummary, 복사한 파일 ${outdated.size}개, 지운 jar ${strayJars.size}개")
