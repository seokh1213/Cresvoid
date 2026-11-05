import java.io.PrintWriter

fun main() {
    val output = PrintWriter(System.out.bufferedWriter())
    val registry = Registry(output)
    System.`in`.bufferedReader().forEachLine { line ->
        val command = parseCommand(line)
        if (command != null) registry.execute(command)
    }
    output.flush()
}
