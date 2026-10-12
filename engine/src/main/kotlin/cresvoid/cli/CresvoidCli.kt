package cresvoid.cli

import cresvoid.CresvoidError
import cresvoid.FailureShown
import cresvoid.describeIoError
import cresvoid.project.Project
import cresvoid.terminal.Console
import cresvoid.terminal.red
import java.io.IOException
import java.io.UncheckedIOException

private val usage = """
    cresvoid: 로컬 구현·독해·코드 읽기·버그 수정 훈련 도구

    사용법
      cresvoid                                   진행 상황과 다음 할 일
      cresvoid start <트랙> [-stage N] [-lang 언어] [-dir 경로]
                                             풀이 작업 공간을 만들거나 고른다
      cresvoid read <트랙> [-stage N]            단계 문서를 읽는다
      cresvoid quiz <트랙> [-stage N]            확인 문항을 푼다
      cresvoid test <트랙> [-stage N] [-dir 경로] [-seed S]
                                             테스트를 실행한다
      cresvoid solution <트랙> [-stage N]        참조 풀이를 본다
      cresvoid reset [트랙] [-stage N] [-yes]    진행 기록을 지운다
      cresvoid validate <트랙>                   컨텐츠를 검증한다
      cresvoid help                              이 도움말을 본다

    트랙 종류와 단계마다 할 일
      build    구현       start, read, quiz, test
      reading  독해       read, quiz
      code     코드 읽기  read, quiz. read 가 코드를 줄 번호와 함께 보여 준다
      fix      버그 수정  start, read, test. 작업 공간은 단계마다 따로 만든다
    인자 없이 cresvoid 를 실행하면 다음에 할 명령을 알려 준다.

    -stage 에는 단계 번호(1, 2, ...)나 단계 디렉터리 이름을 쓴다.
    -stage 를 생략하면 아직 마치지 않은 첫 단계를, 모두 마쳤으면 마지막 단계를 고른다.
    start 의 -stage 는 fix 트랙에만 쓴다. build 트랙은 모든 단계가 작업 공간 하나를 쓴다.
    작업 공간 기본 경로는 build 트랙이 practice/<트랙>, fix 트랙이 practice/<트랙>/<단계 디렉터리> 다. -dir 로 다른 경로를 고른다.
    reset 은 트랙을 생략하면 모든 트랙의 기록을, -stage 를 붙이면 그 단계의 기록만 지운다.
    지우기 전에 practice/progress/backups 에 사본을 남기고 작업 공간은 지우지 않는다. 터미널이 아니면 -yes 가 있어야 지운다.
""".trimIndent()

class CresvoidCli(
    private val console: Console,
    private val locateProject: () -> Project,
    private val commandsFor: (Project) -> List<Command>,
) {
    fun run(args: List<String>): Int {
        if (args.firstOrNull() in setOf("help", "-h", "--help")) {
            console.println(usage)
            return 0
        }
        return reportingErrors {
            val commands = commandsFor(locateProject())
            val name = args.firstOrNull() ?: "status"
            val command = commands.firstOrNull { it.name == name }
            if (command == null) {
                console.error("모르는 명령입니다: $name\n\n$usage")
                return 2
            }
            command.run(args.drop(1))
        }
    }

    private inline fun reportingErrors(command: () -> Unit): Int =
        try {
            command()
            0
        } catch (help: HelpRequested) {
            console.println("사용법: ${help.usage}")
            0
        } catch (error: CresvoidError) {
            console.error(red("오류: ") + error.message)
            1
        } catch (error: IOException) {
            console.error(red("오류: ") + describeIoError(error))
            1
        } catch (error: UncheckedIOException) {
            console.error(red("오류: ") + (error.cause?.let(::describeIoError) ?: error.toString()))
            1
        } catch (_: FailureShown) {
            1
        }
}
