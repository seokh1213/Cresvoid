package lab

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

// The isolated lab database uses trust authentication; this is a small text-protocol adapter, not JDBC.
class Postgres : AutoCloseable {
    private val socket = Socket().apply {
        connect(InetSocketAddress(System.getenv("PG_HOST") ?: "postgres", 5432), 5_000)
        soTimeout = 10_000
    }
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    init {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(196608)
            it.write("user\u0000lab\u0000database\u0000lab\u0000client_encoding\u0000UTF8\u0000\u0000".toByteArray())
        }
        output.writeInt(bytes.size() + 4)
        output.write(bytes.toByteArray())
        output.flush()
        receive()
    }

    fun query(sql: String): List<List<String>> {
        val bytes = (sql + '\u0000').toByteArray()
        output.writeByte('Q'.code)
        output.writeInt(bytes.size + 4)
        output.write(bytes)
        output.flush()
        return receive()
    }

    private fun receive(): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var failure: String? = null
        while (true) {
            val type = input.readUnsignedByte().toChar()
            val length = input.readInt() - 4
            check(length in 0..1_048_576) { "Invalid PostgreSQL frame" }
            val payload = ByteArray(length)
            input.readFully(payload)
            when (type) {
                'R' -> check(DataInputStream(ByteArrayInputStream(payload)).readInt() == 0) { "Trust authentication required" }
                'D' -> rows.add(readRow(payload))
                'E' -> failure = payload.toString(Charsets.UTF_8).replace('\u0000', ' ')
                'Z' -> {
                    check(failure == null) { "PostgreSQL: $failure" }
                    return rows
                }
            }
        }
    }

    private fun readRow(payload: ByteArray): List<String> {
        val row = DataInputStream(ByteArrayInputStream(payload))
        return List(row.readUnsignedShort()) {
            val size = row.readInt()
            if (size == -1) "" else {
                check(size in 0..payload.size)
                ByteArray(size).also(row::readFully).toString(Charsets.UTF_8)
            }
        }
    }

    override fun close() = socket.close()
}

// Only validated event identifiers enter SQL; this deliberately rejects arbitrary SQL text values.
fun sqlId(value: String): String {
    require(value.matches(Regex("[a-z][a-z0-9-]{0,39}")))
    return "'$value'"
}

