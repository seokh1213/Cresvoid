package cresvoid



const val UPPERCASE_RUN = "#!/bin/sh\nexec tr a-z A-Z\n"

const val REVERSE_RUN = """#!/bin/sh
while IFS= read -r line; do
  case "${'$'}line" in
    "REV "*) printf '%s\n' "${'$'}{line#REV }" | rev | tr a-z A-Z ;;
    *) printf '%s\n' "${'$'}line" | tr a-z A-Z ;;
  esac
done
"""

fun Sandbox.addBuildTrack() {
    file(
        "content/tracks/mini/track.json",
        """
        {
          "title": "미니 구현",
          "kind": "build",
          "summary": "줄을 대문자로 바꾸고 뒤집는다.",
          "stages": [
            { "dir": "01-upper", "title": "대문자" },
            { "dir": "02-reverse", "title": "뒤집기" }
          ]
        }
        """.trimIndent(),
    )
    file(
        "content/tracks/mini/stages/01-upper/spec.md",
        """
        # 대문자

        [1] 프로그램은 표준 입력의 줄을 읽는다.
        [1.1] 각 줄의 영문 소문자를 `대문자`로 바꿔 한 줄씩 출력한다.

        [2] 빈 입력이면 아무것도 출력하지 않는다.
        """.trimIndent(),
    )
    file(
        "content/tracks/mini/stages/01-upper/hidden/quiz.json",
        """
        {
          "questions": [
            {
              "id": "q1",
              "type": "choice",
              "prompt": "hello 의 출력은?",
              "choices": ["hello", "HELLO", "Hello"],
              "answer": 2,
              "refs": ["1.1"],
              "quote": "각 줄의 영문 소문자를 대문자로 바꿔",
              "explanation": "소문자를 대문자로 바꾼다."
            },
            {
              "id": "q2",
              "type": "short",
              "prompt": "다음 입력의 출력은?",
              "input": ["abc"],
              "answers": ["ABC"],
              "refs": ["1.1"],
              "quote": "한 줄씩 출력한다.",
              "explanation": "abc 는 ABC 가 된다."
            }
          ]
        }
        """.trimIndent(),
    )
    file(
        "content/tracks/mini/stages/01-upper/hidden/tests.json",
        """
        {
          "timeout_sec": 2,
          "tests": [
            {
              "name": "basic",
              "refs": ["1.1"],
              "quote": "각 줄의 영문 소문자를 대문자로 바꿔 한 줄씩 출력한다.",
              "input": ["hello"],
              "expected": ["HELLO"]
            },
            {
              "name": "gen-lines",
              "refs": ["1"],
              "quote": "프로그램은 표준 입력의 줄을 읽는다.",
              "gen": ["sh", "gen.sh", "--count", "3"],
              "repeat": 2
            }
          ]
        }
        """.trimIndent(),
    )
    file(
        "content/tracks/mini/stages/01-upper/hidden/gen.sh",
        """
        count=1
        seed=0
        while [ ${'$'}# -gt 0 ]; do
          case "${'$'}1" in
            --count) count="${'$'}2"; shift 2 ;;
            --seed) seed="${'$'}2"; shift 2 ;;
            *) shift ;;
          esac
        done
        i=1
        while [ "${'$'}i" -le "${'$'}count" ]; do
          echo "line${'$'}i-${'$'}seed"
          i=${'$'}((i + 1))
        done
        """.trimIndent(),
    )
    file("content/tracks/mini/stages/01-upper/hidden/solution/run.sh", UPPERCASE_RUN, executable = true)
    file("content/tracks/mini/stages/01-upper/hidden/solution/src/main/kotlin/Upper.kt", "fun upper(line: String): String = line.uppercase()\n")
    file(
        "content/tracks/mini/stages/02-reverse/spec.md",
        """
        # 뒤집기

        [1] `REV ` 로 시작하는 줄은 나머지 글자를 거꾸로 뒤집은 뒤 대문자로 출력한다.

        [2] 다른 줄은 1단계처럼 대문자로 출력한다.
        """.trimIndent(),
    )
    file(
        "content/tracks/mini/stages/02-reverse/hidden/quiz.json",
        """
        {
          "questions": [
            {
              "id": "q1",
              "type": "short",
              "prompt": "REV abc 의 출력은?",
              "input": ["REV abc"],
              "answers": ["CBA"],
              "refs": ["1"],
              "quote": "나머지 글자를 거꾸로 뒤집은 뒤",
              "explanation": "abc 를 뒤집으면 cba 이고 대문자로 CBA 다."
            }
          ]
        }
        """.trimIndent(),
    )
    file(
        "content/tracks/mini/stages/02-reverse/hidden/tests.json",
        """
        {
          "tests": [
            {
              "name": "reverse",
              "refs": ["1"],
              "quote": "REV 로 시작하는 줄은 나머지 글자를 거꾸로 뒤집은 뒤 대문자로 출력한다.",
              "input": ["REV abc", "xyz"],
              "expected": ["CBA", "XYZ"]
            }
          ]
        }
        """.trimIndent(),
    )
    file("content/tracks/mini/stages/02-reverse/hidden/solution/run.sh", REVERSE_RUN, executable = true)
    file("content/tracks/mini/stages/02-reverse/hidden/solution/src/main/kotlin/Reverse.kt", "fun reverse(line: String): String = line.reversed()\n")
    file("content/tracks/mini/starters/sh/run.sh", "#!/bin/sh\nexec cat\n", executable = true)
    file("content/tracks/mini/starters/sh/build.sh", "#!/bin/sh\nexit 0\n", executable = true)
    file("content/tracks/mini/starters/sh/notes.txt", "시작 코드\n")
    file("content/tracks/mini/starters/sh/.DS_Store", "junk\n")
    file("content/tracks/mini/starters/sh/build/app.jar", "stale\n")
}

fun Sandbox.addReadingTrack(stageCount: Int = 1) {
    val stages = (1..stageCount).joinToString(",\n") { number ->
        """    { "dir": "0$number-tide", "title": "조석 $number", "target_minutes": 5 }"""
    }
    file(
        "content/tracks/story/track.json",
        "{\n  \"title\": \"미니 독해\",\n  \"kind\": \"reading\",\n  \"summary\": \"짧은 지문을 읽는다.\",\n  \"stages\": [\n$stages\n  ]\n}\n",
    )
    for (number in 1..stageCount) {
        file(
            "content/tracks/story/stages/0$number-tide/passage.md",
            """
            [1] 조석은 달과 태양의 인력 차이로 생긴다. ㉠ 달이 더 가까워서 영향이 크다.

            [2] 하루에 두 번 밀물과 썰물이 있다.
            """.trimIndent(),
        )
        file(
            "content/tracks/story/stages/0$number-tide/hidden/quiz.json",
            """
            {
              "questions": [
                {
                  "id": "q1",
                  "type": "choice",
                  "prompt": "윗글의 내용과 일치하는 것은?",
                  "context": "<보기>\n첫 줄",
                  "choices": ["가", "나", "다", "라", "마"],
                  "answer": 3,
                  "refs": ["1"],
                  "quote": "달이 더 가까워서 영향이 크다.",
                  "explanation": "③이 맞다."
                },
                {
                  "id": "q2",
                  "type": "choice",
                  "prompt": "하루 밀물 횟수는?",
                  "choices": ["1", "2", "3", "4", "5"],
                  "answer": 2,
                  "refs": ["2"],
                  "quote": "하루에 두 번",
                  "explanation": "두 번이다."
                }
              ]
            }
            """.trimIndent(),
        )
    }
}

fun Sandbox.addBrokenTracks() {
    file(
        "content/tracks/broken/track.json",
        """
        {
          "title": "망가진 트랙",
          "kind": "build",
          "summary": "파일과 모양 문제를 모았다.",
          "stages": [
            { "dir": "01-bad", "title": "모양 문제" },
            { "dir": "02-missing", "title": "없는 파일" }
          ]
        }
        """.trimIndent(),
    )
    file("content/tracks/broken/stages/01-bad/spec.md", "[1] 첫 문단이다.\n\n[1] 겹친 문단이다.\n\n[2] 둘째 문단이다.\n")
    file(
        "content/tracks/broken/stages/01-bad/hidden/quiz.json",
        """
        {
          "questions": [
            {
              "id": "q1",
              "type": "choice",
              "prompt": "발문",
              "choices": ["하나", "둘"],
              "answer": 4,
              "refs": ["7"],
              "quote": "없는 문장",
              "explanation": "해설"
            }
          ]
        }
        """.trimIndent(),
    )
    file(
        "content/tracks/broken/stages/01-bad/hidden/tests.json",
        """
        {
          "tests": [
            {
              "name": "Bad_Name",
              "refs": ["2"],
              "quote": "둘째 문단이다.",
              "input": ["x"],
              "gen": ["sh", "gen.sh"]
            }
          ]
        }
        """.trimIndent(),
    )
    file("content/tracks/broken/stages/01-bad/hidden/solution/run.sh", "#!/bin/sh\ncat\n", executable = true)
    val missing = "content/tracks/broken/stages/02-missing"
    file("$missing/quiz.json", """{"questions": []}""")
    file("$missing/tests.json", """{"tests": []}""")
    file("$missing/gen.main.kts", "println(1)\n")
    file("$missing/solution/run.sh", "#!/bin/sh\ncat\n", executable = true)

    file(
        "content/tracks/wrong/track.json",
        """
        {
          "title": "틀린 풀이",
          "kind": "build",
          "summary": "실행 검증 문제를 모았다.",
          "stages": [
            { "dir": "01-echo", "title": "그대로" },
            { "dir": "02-same", "title": "새 규칙 없음" }
          ]
        }
        """.trimIndent(),
    )
    for ((stage, expected) in listOf("01-echo" to "y", "02-same" to "x")) {
        file("content/tracks/wrong/stages/$stage/spec.md", "[1] 입력을 그대로 출력한다.\n")
        file(
            "content/tracks/wrong/stages/$stage/hidden/quiz.json",
            """
            {
              "questions": [
                {
                  "id": "q1",
                  "type": "short",
                  "prompt": "a 의 출력은?",
                  "input": ["a"],
                  "answers": ["b"],
                  "refs": ["1"],
                  "quote": "그대로 출력한다.",
                  "explanation": "그대로다."
                }
              ]
            }
            """.trimIndent(),
        )
        file(
            "content/tracks/wrong/stages/$stage/hidden/tests.json",
            """
            {
              "tests": [
                {
                  "name": "echo",
                  "refs": ["1"],
                  "quote": "입력을 그대로 출력한다.",
                  "input": ["x"],
                  "expected": ["$expected"]
                }
              ]
            }
            """.trimIndent(),
        )
        file("content/tracks/wrong/stages/$stage/hidden/solution/run.sh", "#!/bin/sh\ncat\n", executable = true)
    }
    file("content/tracks/wrong/starters/sh/run.sh", "#!/bin/sh\necho y\n", executable = true)
}

const val SUM_RUN = "#!/bin/sh\nawk '{ total += \$1 } END { print total + 0 }'\n"
const val SUM_SKIPPING_FIRST_RUN = "#!/bin/sh\nawk 'NR > 1 { total += \$1 } END { print total + 0 }'\n"
const val MAX_RUN = "#!/bin/sh\nawk 'NR == 1 || \$1 > max { max = \$1 } END { if (NR > 0) print max }'\n"
const val MAX_FROM_ZERO_RUN = "#!/bin/sh\nawk '\$1 > max { max = \$1 } END { if (NR > 0) print max }'\n"
private const val NO_BUILD = "#!/bin/sh\nexit 0\n"

fun Sandbox.addCodeTrack() {
    file(
        "content/tracks/lens/track.json",
        """
        {
          "title": "미니 코드 읽기",
          "kind": "code",
          "summary": "장바구니 코드를 읽는다.",
          "stages": [
            { "dir": "01-cart", "title": "장바구니 합계", "target_minutes": 10 }
          ]
        }
        """.trimIndent(),
    )
    val stage = "content/tracks/lens/stages/01-cart"
    file(
        "$stage/guide.md",
        """
        # 장바구니

        읽을 파일: Main.kt, Cart.kt

        [1] 프로그램은 표준 입력에서 `ADD <상품> <수량>` 명령을 한 줄씩 읽는다.

        [2] 입력이 끝나면 담긴 수량의 합을 한 줄로 출력한다.
        """.trimIndent() + "\n",
    )
    file(
        "$stage/code/src/main/kotlin/Cart.kt",
        """
        package lens

        class Cart {
            private val quantities = mutableMapOf<String, Int>()

            fun add(item: String, quantity: Int) {
                quantities[item] = (quantities[item] ?: 0) + quantity
            }

            fun total(): Int = quantities.values.sum()
        }
        """.trimIndent() + "\n",
    )
    file(
        "$stage/code/src/main/kotlin/Main.kt",
        """
        package lens

        fun main() {
            val cart = Cart()
            generateSequence(::readLine).map { it.split(" ") }.forEach { cart.add(it[1], it[2].toInt()) }
            println(cart.total())
        }
        """.trimIndent() + "\n",
    )
    file("$stage/code/run.sh", "#!/bin/sh\nawk '\$1 == \"ADD\" { total += \$3 } END { print total + 0 }'\n", executable = true)
    file("$stage/code/build.sh", NO_BUILD, executable = true)
    file(
        "$stage/hidden/quiz.json",
        """
        {
          "questions": [
            {
              "id": "q1",
              "type": "choice",
              "prompt": "같은 상품을 두 번 담으면 수량은?",
              "choices": ["마지막 수량만 남는다", "두 수량을 더한다", "처음 수량만 남는다"],
              "answer": 2,
              "refs": ["Cart.kt:6-8"],
              "quote": "quantities[item] = (quantities[item] ?: 0) + quantity",
              "explanation": "앞 수량에 새 수량을 더한다."
            },
            {
              "id": "q2",
              "type": "short",
              "prompt": "다음 입력의 출력은?",
              "input": ["ADD apple 2", "ADD pear 3"],
              "answers": ["5"],
              "refs": ["2", "Cart.kt:10"],
              "quote": "fun total(): Int = quantities.values.sum()",
              "explanation": "2 와 3 을 더한다."
            }
          ]
        }
        """.trimIndent(),
    )
}

fun Sandbox.addFixTrack() {
    file(
        "content/tracks/patch/track.json",
        """
        {
          "title": "미니 버그 수정",
          "kind": "fix",
          "summary": "합계와 최댓값의 버그를 고친다.",
          "stages": [
            { "dir": "01-total", "title": "합계" },
            { "dir": "02-max", "title": "최댓값" }
          ]
        }
        """.trimIndent(),
    )
    val total = "content/tracks/patch/stages/01-total"
    file(
        "$total/spec.md",
        """
        # 합계

        [1] 프로그램은 표준 입력의 줄마다 정수 하나를 읽는다.

        [2] 입력이 끝나면 모든 정수의 합을 한 줄로 출력한다. 입력이 없으면 0 을 출력한다.

        ## 버그 신고

        [3] 숫자 하나만 넣으면 0 이 나온다. `5` 를 넣으면 5 가 나와야 한다.
        """.trimIndent() + "\n",
    )
    file(
        "$total/hidden/tests.json",
        """
        {
          "timeout_sec": 2,
          "tests": [
            { "name": "single", "refs": ["2"], "quote": "모든 정수의 합을 한 줄로 출력한다.", "input": ["5"], "expected": ["5"] },
            { "name": "several", "refs": ["2"], "quote": "모든 정수의 합", "input": ["1", "2", "3"], "expected": ["6"] },
            { "name": "empty", "refs": ["2"], "quote": "입력이 없으면 0 을 출력한다.", "input": [], "expected": ["0"] }
          ]
        }
        """.trimIndent(),
    )
    file("$total/hidden/solution/run.sh", SUM_RUN, executable = true)
    file("$total/hidden/solution/build.sh", NO_BUILD, executable = true)
    file("$total/hidden/solution/src/main/kotlin/Total.kt", "fun total(numbers: List<Int>): Int = numbers.sum()\n")
    file("$total/starter/run.sh", SUM_SKIPPING_FIRST_RUN, executable = true)
    file("$total/starter/build.sh", NO_BUILD, executable = true)
    file("$total/starter/src/main/kotlin/Total.kt", "fun total(numbers: List<Int>): Int = numbers.drop(1).sum()\n")
    file("$total/starter/.DS_Store", "junk\n")
    file("$total/starter/build/app.jar", "stale\n")

    val max = "content/tracks/patch/stages/02-max"
    file(
        "$max/spec.md",
        """
        # 최댓값

        [1] 프로그램은 표준 입력의 줄마다 정수 하나를 읽는다.

        [2] 입력이 끝나면 가장 큰 정수를 한 줄로 출력한다.

        ## 버그 신고

        [3] 음수만 넣으면 빈 줄이 나온다.
        """.trimIndent() + "\n",
    )
    file(
        "$max/hidden/tests.json",
        """
        {
          "timeout_sec": 2,
          "tests": [
            { "name": "positive", "refs": ["2"], "quote": "가장 큰 정수를 한 줄로 출력한다.", "input": ["1", "5", "2"], "expected": ["5"] },
            { "name": "negative", "refs": ["2"], "quote": "가장 큰 정수", "input": ["-3", "-1"], "expected": ["-1"] }
          ]
        }
        """.trimIndent(),
    )
    file("$max/hidden/solution/run.sh", MAX_RUN, executable = true)
    file("$max/starter/run.sh", MAX_FROM_ZERO_RUN, executable = true)
}

fun Sandbox.addBrokenCodeTrack() {
    file(
        "content/tracks/murky/track.json",
        """
        {
          "title": "망가진 코드 읽기",
          "kind": "code",
          "summary": "코드 위치와 실행 문제를 모았다.",
          "stages": [
            { "dir": "01-bad", "title": "코드 위치 문제" },
            { "dir": "02-nobuild", "title": "빌드 실패" },
            { "dir": "03-crash", "title": "실행 실패" }
          ]
        }
        """.trimIndent(),
    )
    val bad = "content/tracks/murky/stages/01-bad"
    file("$bad/guide.md", "[1] 첫 문단이다.\n\n[2] 둘째 문단이다.\n")
    file("$bad/code/src/main/kotlin/Cart.kt", "class Cart {\n    val size = 0\n}\n")
    file("$bad/code/run.sh", "#!/bin/sh\necho a\necho b\n", executable = true)
    file(
        "$bad/hidden/quiz.json",
        """
        {
          "questions": [
            { "id": "q1", "type": "choice", "prompt": "발문", "choices": ["가", "나"], "answer": 1, "refs": ["Cart.kt:2-9"], "quote": "val size = 0", "explanation": "해설" },
            { "id": "q2", "type": "choice", "prompt": "발문", "choices": ["가", "나"], "answer": 1, "refs": ["Nope.kt:1"], "quote": "x", "explanation": "해설" },
            { "id": "q3", "type": "choice", "prompt": "발문", "choices": ["가", "나"], "answer": 1, "refs": ["Cart.kt:1"], "quote": "없는 줄", "explanation": "해설" },
            { "id": "q4", "type": "choice", "prompt": "발문", "choices": ["가", "나"], "answer": 1, "refs": ["Cart.kt:x"], "quote": "class Cart", "explanation": "해설" },
            { "id": "q5", "type": "short", "prompt": "발문", "answers": ["1"], "refs": ["1"], "quote": "첫 문단이다.", "explanation": "해설" },
            { "id": "q6", "type": "short", "prompt": "발문", "input": ["x"], "answers": ["a"], "refs": ["Cart.kt:1-3"], "quote": "val   size = 0", "explanation": "해설" },
            { "id": "q7", "type": "choice", "prompt": "발문", "choices": ["가", "나"], "answer": 1, "refs": ["Cart.kt:3-1"], "quote": "}", "explanation": "해설" },
            { "id": "q8", "type": "choice", "prompt": "발문", "choices": ["가", "나"], "answer": 2, "refs": ["2", "Cart.kt:2"], "quote": "둘째 문단이다.", "explanation": "해설" }
          ]
        }
        """.trimIndent(),
    )
    val validQuiz = """
        {
          "questions": [
            { "id": "q1", "type": "choice", "prompt": "발문", "choices": ["가", "나"], "answer": 1, "refs": ["1"], "quote": "첫 문단이다.", "explanation": "해설" }
          ]
        }
    """.trimIndent()
    for (stage in listOf("02-nobuild", "03-crash")) {
        file("content/tracks/murky/stages/$stage/guide.md", "[1] 첫 문단이다.\n")
        file("content/tracks/murky/stages/$stage/hidden/quiz.json", validQuiz)
        file("content/tracks/murky/stages/$stage/code/src/main/kotlin/Main.kt", "fun main() {}\n")
    }
    file("content/tracks/murky/stages/02-nobuild/code/run.sh", "#!/bin/sh\necho 0\n", executable = true)
    file("content/tracks/murky/stages/02-nobuild/code/build.sh", "#!/bin/sh\necho 'error: unresolved reference' >&2\nexit 1\n", executable = true)
    file("content/tracks/murky/stages/03-crash/code/run.sh", "#!/bin/sh\necho 'Exception in thread \"main\"' >&2\nexit 3\n", executable = true)
}

fun Sandbox.addBrokenFixTracks() {
    file(
        "content/tracks/shaky/track.json",
        """
        {
          "title": "흔들리는 버그 수정",
          "kind": "fix",
          "summary": "시작 코드와 참조 풀이 문제를 모았다.",
          "stages": [
            { "dir": "01-same", "title": "버그 없는 시작 코드" },
            { "dir": "02-wrong", "title": "틀린 참조 풀이" }
          ]
        }
        """.trimIndent(),
    )
    for ((stage, expected) in listOf("01-same" to "x", "02-wrong" to "y")) {
        val dir = "content/tracks/shaky/stages/$stage"
        file("$dir/spec.md", "[1] 입력을 그대로 출력한다.\n")
        file(
            "$dir/hidden/tests.json",
            """{"tests": [{"name": "echo", "refs": ["1"], "quote": "그대로 출력한다.", "input": ["x"], "expected": ["$expected"]}]}""",
        )
        file("$dir/hidden/solution/run.sh", "#!/bin/sh\ncat\n", executable = true)
    }
    file("content/tracks/shaky/stages/01-same/starter/run.sh", "#!/bin/sh\ncat\n", executable = true)
    file("content/tracks/shaky/stages/01-same/hidden/quiz.json", """{"questions": []}""")
    file("content/tracks/shaky/stages/02-wrong/starter/run.sh", "#!/bin/sh\necho z\n", executable = true)

    file(
        "content/tracks/flimsy/track.json",
        """
        {
          "title": "빠진 시작 코드",
          "kind": "fix",
          "summary": "시작 코드가 없다.",
          "stages": [
            { "dir": "01-nostarter", "title": "시작 코드 없음" }
          ]
        }
        """.trimIndent(),
    )
    val nostarter = "content/tracks/flimsy/stages/01-nostarter"
    file("$nostarter/spec.md", "[1] 입력을 그대로 출력한다.\n")
    file("$nostarter/hidden/tests.json", """{"tests": [{"name": "echo", "refs": ["1"], "quote": "그대로 출력한다.", "input": ["x"], "expected": ["x"]}]}""")
    file("$nostarter/hidden/solution/run.sh", "#!/bin/sh\ncat\n", executable = true)
}
