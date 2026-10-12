package cresvoid.runner

import cresvoid.CresvoidError
import cresvoid.stageIn
import cresvoid.withTempDir
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class EditScopeTest : FunSpec({
    test("허용한 처리기 수정과 빌드 산출물은 통과한다") {
        withTempDir { root ->
            val stage = stageIn(root.resolve("02-fix"))
            write(stage.hiddenDir, "allowed-edits.txt", "src/main/kotlin/Handler.kt\n")
            write(stage.starterDir, "src/main/kotlin/Handler.kt", "fun handle() = 1\n")
            write(stage.starterDir, "src/main/kotlin/Reporter.kt", "fun report() = 1\n")
            write(stage.starterDir, "run.sh", "#!/bin/sh\n")
            val candidate = root.resolve("candidate")
            write(candidate, "src/main/kotlin/Handler.kt", "fun handle() = 2\n")
            write(candidate, "src/main/kotlin/Reporter.kt", "fun report() = 1\n")
            write(candidate, "run.sh", "#!/bin/sh\n")
            write(candidate, "build/app.jar", "generated\n")

            checkEditScope(stage, candidate)
        }
    }

    test("실행 틀 수정과 새 소스 파일은 거절한다") {
        withTempDir { root ->
            val stage = stageIn(root.resolve("02-fix"))
            write(stage.hiddenDir, "allowed-edits.txt", "src/main/kotlin/Handler.kt\n")
            write(stage.starterDir, "src/main/kotlin/Handler.kt", "fun handle() = 1\n")
            write(stage.starterDir, "src/main/kotlin/Reporter.kt", "fun report() = 1\n")
            write(stage.starterDir, "run.sh", "#!/bin/sh\n")
            val candidate = root.resolve("candidate")
            write(candidate, "src/main/kotlin/Handler.kt", "fun handle() = 2\n")
            write(candidate, "src/main/kotlin/Reporter.kt", "fun report() = 2\n")
            write(candidate, "run.sh", "#!/bin/sh\n")

            shouldThrow<CresvoidError> { checkEditScope(stage, candidate) }.message.orEmpty() shouldContain "Reporter.kt"
            write(candidate, "src/main/kotlin/Reporter.kt", "fun report() = 1\n")
            write(candidate, "src/main/kotlin/NewReporter.kt", "fun hide() = Unit\n")
            shouldThrow<CresvoidError> { checkEditScope(stage, candidate) }.message.orEmpty() shouldContain "NewReporter.kt"
            Files.delete(candidate.resolve("src/main/kotlin/NewReporter.kt"))
            write(candidate, "run.sh", "#!/bin/sh\necho fake\n")
            shouldThrow<CresvoidError> { checkEditScope(stage, candidate) }.message.orEmpty() shouldContain "run.sh"
        }
    }

    test("시작 코드 바깥 경로를 허용하지 않는다") {
        withTempDir { root ->
            val stage = stageIn(root.resolve("02-fix"))
            write(stage.hiddenDir, "allowed-edits.txt", "../Main.kt\n")
            shouldThrow<CresvoidError> { checkEditScope(stage, root.resolve("candidate")) }
                .message.orEmpty() shouldContain "수정 허용 파일 목록"
        }
    }
})

private fun write(project: Path, relative: String, content: String) {
    val file = project.resolve(relative)
    Files.createDirectories(file.parent)
    Files.writeString(file, content)
}
