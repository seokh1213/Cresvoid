---
id: L-coroutines-50
title: 블로킹 호출과 디스패처 고갈
kind: lab
track: coroutine-lab
stage: dispatcher-starvation
status: 후보
wave: 4
env: kotlinx-coroutines, k6
prereq: B-coroutines-20
---

# L-coroutines-50 블로킹 호출과 디스패처 고갈

- 모드: observe 뒤 implement
- 실행 방식: JDK HTTP 서버 위에 코루틴으로 요청을 처리하는 서비스를 실행하고 k6 로 부하를 가한다. 일부 경로가 Dispatchers.Default 에서 블로킹 JDBC 를 흉내 낸 호출을 한다.
- 목표: 코어 수만큼의 스레드가 막혀 모든 경로의 지연이 늘어나는 과정을 관찰하고 IO 디스패처, limitedParallelism, 세마포어로 고친다.
- 불변식: 블로킹 경로가 느려져도 다른 경로의 p95 가 기준 이하, 블로킹 경로의 동시 실행 수가 상한 이하.
- 출처: kotlinx.coroutines API 의 Dispatchers.Default, Dispatchers.IO 문서.
