package cresvoid.content

enum class Completion { TESTS_PASSED, QUIZ_COMPLETED }

enum class WorkspaceScope { TRACK, STAGE }

enum class TestScope { CUMULATIVE, TARGET_STAGE }

enum class SolutionView { SOURCE, STARTER_DIFF }

enum class ShortQuestions { NOT_ALLOWED, INPUT_OPTIONAL, INPUT_REQUIRED }

data class QuizRules(val exactChoiceCount: Int?, val shortQuestions: ShortQuestions)

sealed interface TrackKind {
    val id: String
    val label: String
    val documentName: String
    val completion: Completion
    val workspaceScope: WorkspaceScope?
    val quizRules: QuizRules?
    val testScope: TestScope?
    val solutionView: SolutionView?
    val readsCode: Boolean

    data object Build : TrackKind {
        override val id = "build"
        override val label = "구현"
        override val documentName = "spec.md"
        override val completion = Completion.TESTS_PASSED
        override val workspaceScope = WorkspaceScope.TRACK
        override val quizRules = QuizRules(exactChoiceCount = null, shortQuestions = ShortQuestions.INPUT_OPTIONAL)
        override val testScope = TestScope.CUMULATIVE
        override val solutionView = SolutionView.SOURCE
        override val readsCode = false
    }

    data object Reading : TrackKind {
        override val id = "reading"
        override val label = "독해"
        override val documentName = "passage.md"
        override val completion = Completion.QUIZ_COMPLETED
        override val workspaceScope: WorkspaceScope? = null
        override val quizRules = QuizRules(exactChoiceCount = 5, shortQuestions = ShortQuestions.NOT_ALLOWED)
        override val testScope: TestScope? = null
        override val solutionView: SolutionView? = null
        override val readsCode = false
    }

    data object Code : TrackKind {
        override val id = "code"
        override val label = "코드 읽기"
        override val documentName = "guide.md"
        override val completion = Completion.QUIZ_COMPLETED
        override val workspaceScope: WorkspaceScope? = null
        override val quizRules = QuizRules(exactChoiceCount = null, shortQuestions = ShortQuestions.INPUT_REQUIRED)
        override val testScope: TestScope? = null
        override val solutionView: SolutionView? = null
        override val readsCode = true
    }

    data object Fix : TrackKind {
        override val id = "fix"
        override val label = "버그 수정"
        override val documentName = "spec.md"
        override val completion = Completion.TESTS_PASSED
        override val workspaceScope = WorkspaceScope.STAGE
        override val quizRules: QuizRules? = null
        override val testScope = TestScope.TARGET_STAGE
        override val solutionView = SolutionView.STARTER_DIFF
        override val readsCode = false
    }

    data object Lab : TrackKind {
        override val id = "lab"
        override val label = "실험"
        override val documentName = "spec.md"
        override val completion = Completion.TESTS_PASSED
        override val workspaceScope = WorkspaceScope.STAGE
        override val quizRules = QuizRules(exactChoiceCount = null, shortQuestions = ShortQuestions.INPUT_OPTIONAL)
        override val testScope = TestScope.TARGET_STAGE
        override val solutionView = SolutionView.SOURCE
        override val readsCode = false
    }

    companion object {
        val all: List<TrackKind> by lazy { listOf(Build, Reading, Code, Fix, Lab) }

        fun byId(id: String): TrackKind? = all.firstOrNull { it.id == id }
    }
}
