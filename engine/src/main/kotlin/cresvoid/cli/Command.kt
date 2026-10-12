package cresvoid.cli

interface Command {
    val name: String
    val usage: String

    fun run(args: List<String>)
}
