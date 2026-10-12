package cresvoid.command

import cresvoid.cli.Command
import cresvoid.cli.parseTrackArguments
import cresvoid.content.ContentLibrary
import cresvoid.project.Project
import cresvoid.runner.ProcessRunner
import cresvoid.terminal.Console
import cresvoid.validation.Validation
import cresvoid.validation.validate

class ValidateCommand(
    private val project: Project,
    private val library: ContentLibrary,
    private val runner: ProcessRunner,
    private val console: Console,
) : Command {
    override val name = "validate"
    override val usage = "cresvoid validate <트랙>"

    override fun run(args: List<String>) {
        val arguments = parseTrackArguments(this, args, emptyList())
        val track = library.track(arguments.track)
        runner.stopOnInterrupt()
        Validation(project, library, track, runner, console).validate()
    }
}
