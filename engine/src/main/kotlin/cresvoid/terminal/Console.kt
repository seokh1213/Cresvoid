package cresvoid.terminal

interface Console {
    fun print(text: String)

    fun println(text: String = "")

    fun write(bytes: ByteArray)

    fun error(text: String)
}

class SystemConsole : Console {
    override fun print(text: String) = kotlin.io.print(text)

    override fun println(text: String) = kotlin.io.println(text)

    override fun write(bytes: ByteArray) {
        System.out.write(bytes)
        System.out.flush()
    }

    override fun error(text: String) = System.err.println(text)
}
