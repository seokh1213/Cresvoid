fun main() {
    val parser = CommandParser()
    val hotel = Hotel()
    System.out.bufferedWriter().use { output ->
        System.`in`.bufferedReader().forEachLine { line ->
            val response = when (val parsed = parser.parse(line)) {
                ParsedLine.Empty -> null
                is ParsedLine.Rejected -> parsed.response
                is ParsedLine.Accepted -> hotel.execute(parsed.command)
            }
            if (response != null) {
                output.write(response)
                output.newLine()
            }
        }
    }
}
