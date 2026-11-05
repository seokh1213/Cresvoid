# 검색어 파서

[1] 읽을 파일은 `Main.kt`, `QueryTokenizer.kt`, `QueryParser.kt`, `QueryOutput.kt`이며 이 순서로 읽는다. 파일 경로는 `code/src/main/kotlin/` 기준이다.

[2] 쇼핑 카탈로그 검색창에서 사용하는 고급 검색어의 미리보기 모듈이다. 검색을 실행하기 전에 입력을 구문 트리로 만들고, 검색창에 다시 표시할 검색어를 생성한다. 상품 조회와 색인 처리는 이 코드의 범위 밖이다.

[3] 표준 입력의 각 줄은 `PARSE <검색어>` 명령이다. EOF까지 줄마다 독립적으로 처리한다. 명령과 검색어 사이는 공백으로 구분한다. 문항에 제시된 큰따옴표와 괄호는 입력의 일부다. 입력 줄을 감싸기 위한 별도 따옴표는 붙이지 않는다.

[4] 검색어에는 낱말, 큰따옴표 구문, `AND`, `OR`, `NOT`, 괄호, `price:1000..5000` 형태의 가격 조건이 들어간다. 검색창에 입력 중인 문자열도 전달된다. 허용 문자, 조합 방식, 미완성 입력의 처리는 코드에서 확인한다.

[5] 성공하면 `TREE <구문 트리> | QUERY <정규화한 검색어>` 한 줄을 출력한다. 트리는 `TERM`, `PHRASE`, `PRICE`, `NOT`, `AND`, `OR` 노드로 표현한다. `PRICE`의 값이 없는 자리는 `*`로 표시한다. 실패하면 `ERROR <이유>@<위치>`를 출력한다. 위치는 명령을 제외하고 파서에 전달된 검색어의 0부터 시작하는 문자열 인덱스다. 알 수 없는 명령은 `ERROR command`로 표시한다.

[6] 문항은 코드를 수정하지 않은 상태를 기준으로 한다. 변경을 명시한 문항에만 해당 변경을 적용한다. 출력 예측에서는 트리의 괄호, 쉼표, 큰따옴표와 정규화한 검색어를 함께 적는다. 목표 풀이 시간은 30분이다.

[7] 표준 라이브러리 동작을 확인할 때는 Kotlin 공식 문서의 [substring](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/substring.html), [indexOf](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/index-of.html), [split](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/split.html), [Char.isLetterOrDigit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/is-letter-or-digit.html)를 참고한다. 문항의 답은 제공된 코드만으로 판단할 수 있다.
