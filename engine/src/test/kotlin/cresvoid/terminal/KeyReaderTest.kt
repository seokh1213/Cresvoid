package cresvoid.terminal

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream

private fun keys(vararg bytes: Int): List<Key> {
    val reader = KeyReader(ByteArrayInputStream(ByteArray(bytes.size) { bytes[it].toByte() }), escapeWaitMillis = 1)
    return generateSequence { reader.next().takeUnless { it == Key.EndOfInput } }.toList()
}

private fun keys(text: String): List<Key> = keys(*text.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }.toIntArray())

class KeyReaderTest : FunSpec({
    test("글자와 한글을 한 키씩 읽는다") {
        keys("a한3") shouldBe listOf(Key.Text("a"), Key.Text("한"), Key.Text("3"))
    }

    test("화살표, 페이지, Shift-Tab 순서열을 읽는다") {
        keys("\u001b[A\u001b[B\u001b[C\u001b[D\u001b[5~\u001b[6~\u001b[Z\u001bOH") shouldBe
            listOf(Key.Up, Key.Down, Key.Right, Key.Left, Key.PageUp, Key.PageDown, Key.BackTab, Key.Home)
    }

    test("제어 바이트를 키로 바꾸고 모르는 것은 건너뛴다") {
        keys(13, 10, 127, 8, 9, 1, 3) shouldBe listOf(Key.Enter, Key.Enter, Key.Backspace, Key.Backspace, Key.Tab, Key.Interrupt)
    }

    test("뒤따르는 바이트가 없는 Esc 는 Esc 키다") {
        keys(27) shouldBe listOf(Key.Escape)
    }

    test("모르는 순서열은 건너뛴다") {
        keys("\u001b[15~x") shouldBe listOf(Key.Text("x"))
    }
})
