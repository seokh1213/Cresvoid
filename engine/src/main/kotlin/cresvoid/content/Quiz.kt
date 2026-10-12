package cresvoid.content

enum class QuestionType(val id: String) {
    CHOICE("choice"),
    SHORT("short"),
    ;

    companion object {
        fun byId(id: String): QuestionType? = entries.firstOrNull { it.id == id }
    }
}

data class Quiz(val questions: List<Question>)

data class Question(
    val id: String,
    val type: QuestionType?,
    val prompt: String,
    val context: String,
    val choices: List<String>,
    val answer: Int,
    val input: List<String>,
    val answers: List<String>,
    val refs: List<String>,
    val quote: String,
    val explanation: String,
) {
    fun accepts(response: String): Boolean = when (type) {
        QuestionType.CHOICE -> response.toIntOrNull()?.let { it in 1..choices.size } == true
        QuestionType.SHORT, null -> response.isNotEmpty()
    }

    fun isCorrect(response: String): Boolean = when (type) {
        QuestionType.CHOICE -> response.trim().toIntOrNull() == answer
        QuestionType.SHORT -> answers.any { collapseSpaces(it) == collapseSpaces(response) }
        null -> false
    }

    fun correctAnswerText(): String = when {
        type == QuestionType.CHOICE && answer in 1..choices.size -> "${answer}번. ${choices[answer - 1]}"
        else -> answers.firstOrNull().orEmpty()
    }
}

fun loadQuiz(stage: Stage): Quiz = readContentJson(stage.quizPath) { fields ->
    Quiz(
        fields.objectList("questions") { question ->
            Question(
                id = question.string("id"),
                type = QuestionType.byId(question.string("type")),
                prompt = question.string("prompt"),
                context = question.optionalString("context").orEmpty(),
                choices = question.optionalStringList("choices").orEmpty(),
                answer = question.optionalInt("answer") ?: 0,
                input = question.optionalStringList("input").orEmpty(),
                answers = question.optionalStringList("answers").orEmpty(),
                refs = question.stringList("refs"),
                quote = question.string("quote"),
                explanation = question.string("explanation"),
            )
        },
    )
}
