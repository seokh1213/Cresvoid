---
id: L-coroutines-50
title: 블로킹 호출과 디스패처 고갈
kind: lab
track: coroutine-lab
stage: 01-dispatcher-starvation
status: 완료(2026-10-13)
wave: 4
env: kotlinx-coroutines
prereq: B-coroutines-20
---

# L-coroutines-50 블로킹 호출과 디스패처 고갈

- 모드: 시작 코드를 관찰한 뒤 구현한다.
- 실행 방식: JDK HTTP 서버 위에서 코루틴으로 요청을 처리한다. Kotlin 실험 스크립트가 JDK HTTP 클라이언트로 개방형 부하를 보내므로 별도 부하 도구가 필요 없다. 가짜 블로킹 JDBC 호출은 `Default.limitedParallelism(2)`에서 스레드를 300밀리초 동안 막는다.
- 목표: 계산 경로의 지연을 관찰한 뒤 IO 디스패처로 블로킹 호출을 분리하고 세마포어로 동시 실행 수를 제한한다.
- 불변식: `/fast` 응답 20개의 p95 가 500밀리초 이하, 블로킹 호출 12개의 시작·완료 수 일치, 최대 동시 실행 수 2 이하.
- 출처: kotlinx.coroutines API 의 [Dispatchers.IO](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html), [limitedParallelism](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html), [Semaphore](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-semaphore/) 문서.
