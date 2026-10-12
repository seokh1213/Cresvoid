fun main() {
    val commands = ListingCommands(ProductCatalog())
    val output = StringBuilder()
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        commands.execute(line)?.let { output.append(it).append('\n') }
    }
    print(output)
}
