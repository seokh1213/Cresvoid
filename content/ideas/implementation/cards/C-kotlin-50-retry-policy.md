---
id: C-kotlin-50
title: 외부 결제 호출 재시도 정책
kind: code
track: code-reading
stage: retry-policy
status: 후보
wave: 4
env: 없음
prereq: 
---

# C-kotlin-50 외부 결제 호출 재시도 정책

- 도메인: 외부 결제 대행사 호출을 감싸는 재시도 정책. 호출 결과(성공, 시간 초과, 4xx, 5xx, 연결 실패)를 입력 명령으로 흉내 내고 시도마다 다음 대기 시간과 포기 여부를 출력한다. 지터는 시드를 받은 난수로 결정적으로 만든다. 정책 설정, 대기 시간 계산, 오류 분류, 명령 처리를 파일로 나눈다.
- 겨냥: 지수 백오프의 정수 시프트(`1 shl 32` 는 1 이다. Int 시프트는 거리의 하위 5비트만 쓴다), `Double` 을 `Long` 으로 바꿀 때의 포화(`1e30.toLong()` 은 `Long.MAX_VALUE`), 상한을 지터 전에 거는지 뒤에 거는지, `Random(seed)` 는 같은 시드에서 같은 수열을 내고 `nextLong(until)` 은 끝값을 제외하고 고른다, 예외 계층과 `when`·`catch` 분기 순서(상위 타입을 먼저 쓰면 하위 분기에 닿지 않는다), 재시도할 수 있는 오류와 없는 오류의 구분.
- 형식: 200~280줄, 파일 3~4개, 5문항(실행 결과 예측 short 2, choice 3), 목표 25분.
- 난도: 산술 경계를 계산해야 하는 문항이 둘 이상이다. 계산에 필요한 값은 guide.md 에 준다.
- 검증: 실행으로 확인한다. 시프트와 변환 결과는 짧은 스크립트로 따로 확인한다.
- 출처: Kotlin 표준 라이브러리 문서의 `Int.shl`(하위 5비트), `Double.toLong`, `kotlin.random.Random`. Amazon Builders' Library, "Timeouts, retries, and backoff with jitter".
- 피할 것: 실제 시간 대기와 실제 네트워크. 코루틴(KC 카드가 다룬다).
