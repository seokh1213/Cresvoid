package cresvoid.validation

import cresvoid.content.TrackKind
import cresvoid.runner.codeProgram
import cresvoid.runner.referenceProgram

fun Validation.validate() {
    when (track.kind) {
        TrackKind.Build -> validateBuild()
        TrackKind.Reading -> validateReading()
        TrackKind.Code -> validateCode()
        TrackKind.Fix -> validateFix()
        TrackKind.Lab -> validateLab()
    }
    report()
}

private fun Validation.validateLab() {
    val contents = loadStages { stage -> listOf(stage.starterDir, stage.solutionDir) }
    checkDocuments(contents)
    checkQuizzes(contents)
    val lab = LabValidation(track, runner, console)
    lab.checkContent(contents).forEach(::recordIssue)
    if (loadFailed || lab.hasContentProblems) {
        skipProgramChecks()
        return
    }
    checkShortAnswers(contents, ::referenceProgram)
    lab.checkPrograms().forEach(::recordIssue)
}

private fun Validation.validateBuild() {
    val contents = loadStages { stage -> listOf(stage.solutionDir) }
    checkDocuments(contents)
    checkQuizzes(contents)
    checkSuites(contents)
    if (loadFailed) {
        skipProgramChecks()
        return
    }
    val stages = contents.map { RunnableStage(it.stage, checkNotNull(it.suite)) }
    checkReferences(stages)
    checkApiContracts(stages)
    checkShortAnswers(contents, ::referenceProgram)
    checkStageProgression(stages)
    checkStarters(stages)
}

private fun Validation.validateReading() {
    val contents = loadStages { emptyList() }
    checkDocuments(contents)
    checkQuizzes(contents)
}

private fun Validation.validateCode() {
    val contents = loadStages { stage -> listOf(stage.codeDir) }
    checkDocuments(contents)
    checkQuizzes(contents)
    if (loadFailed) {
        skipProgramChecks()
        return
    }
    checkCodePrograms(contents)
    checkShortAnswers(contents, ::codeProgram)
}

private fun Validation.validateFix() {
    val contents = loadStages { stage -> listOf(stage.starterDir, stage.solutionDir) }
    checkDocuments(contents)
    checkSuites(contents)
    if (loadFailed) {
        skipProgramChecks()
        return
    }
    val stages = contents.map { RunnableStage(it.stage, checkNotNull(it.suite)) }
    checkReferences(stages)
    checkStageStarters(stages)
}
