package cresvoid

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.NotLinkException

class IoErrorsTest : FunSpec({
    test("입출력 오류는 경로와 원인을 함께 알린다") {
        withClue("없는 파일") { describeIoError(NoSuchFileException("a")) shouldBe "파일이 없습니다: a" }
        withClue("권한") { describeIoError(AccessDeniedException("a")) shouldBe "권한이 없습니다: a" }
        withClue("이미 있는 파일") { describeIoError(FileAlreadyExistsException("a")) shouldBe "이미 있는 파일입니다: a" }
        withClue("디렉터리가 아님") { describeIoError(NotDirectoryException("a")) shouldBe "디렉터리가 아닙니다: a" }
        withClue("비어 있지 않은 디렉터리") { describeIoError(DirectoryNotEmptyException("a")) shouldBe "비어 있지 않은 디렉터리입니다: a" }
        withClue("원인이 있는 오류") { describeIoError(FileSystemException("a", null, "Not a directory")) shouldBe "a: Not a directory" }
        withClue("원인이 없는 오류") { describeIoError(NotLinkException("a")) shouldBe "a: NotLinkException" }
    }
})
