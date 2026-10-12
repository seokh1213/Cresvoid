package cresvoid.content

import cresvoid.CresvoidError
import cresvoid.json.JsonError
import cresvoid.json.JsonFields
import cresvoid.json.KeyRule
import cresvoid.json.decodeJsonObject
import cresvoid.json.parseJson
import cresvoid.project.readUtf8
import java.nio.file.Path

fun <T> readContentJson(path: Path, decode: (JsonFields) -> T): T {
    val text = path.readUtf8()
    try {
        val parsed = parseJson(text, KeyRule.SNAKE_CASE_UNIQUE)
        val value = decodeJsonObject(parsed.value, location = "", strict = true, decode)
        if (parsed.hasTrailingContent) throw CresvoidError("JSON 값 뒤에 다른 내용이 있습니다: $path")
        return value
    } catch (error: JsonError) {
        throw CresvoidError("JSON 을 읽지 못했습니다: $path: ${error.message}")
    }
}
