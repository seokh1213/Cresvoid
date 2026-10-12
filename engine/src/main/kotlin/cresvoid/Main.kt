package cresvoid

import cresvoid.cli.Command
import cresvoid.cli.CresvoidCli
import cresvoid.command.QuizCommand
import cresvoid.command.ReadCommand
import cresvoid.command.ResetCommand
import cresvoid.command.SolutionCommand
import cresvoid.command.StartCommand
import cresvoid.command.StatusCommand
import cresvoid.command.TestCommand
import cresvoid.command.ValidateCommand
import cresvoid.content.ContentLibrary
import cresvoid.progress.FileProgressRepository
import cresvoid.project.Project
import cresvoid.runner.SystemProcessRunner
import cresvoid.study.TrackStates
import cresvoid.terminal.Console
import cresvoid.terminal.SystemConsole
import cresvoid.terminal.SystemTerminal
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.Clock
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8))
    val console = SystemConsole()
    val cli = CresvoidCli(
        console = console,
        locateProject = { Project.locate(System.getenv("CRESVOID_ROOT")) },
        commandsFor = { project -> commands(project, console) },
    )
    exitProcess(cli.run(args.asList()))
}

private fun commands(project: Project, console: Console): List<Command> {
    val clock = Clock.systemDefaultZone()
    val terminal = SystemTerminal()
    val runner = SystemProcessRunner()
    val library = ContentLibrary(project)
    val progress = FileProgressRepository(project.progressDir, clock)
    val states = TrackStates(project, library, progress)
    return listOf(
        StatusCommand(library, states, progress, console, clock),
        StartCommand(project, library, states, progress, console, clock),
        ReadCommand(project, states, progress, console, terminal, clock),
        QuizCommand(project, states, progress, console, terminal, clock),
        TestCommand(project, states, progress, runner, console, clock),
        SolutionCommand(project, states, runner, console, terminal),
        ResetCommand(project, library, states, progress, console, terminal, clock),
        ValidateCommand(project, library, runner, console),
    )
}
