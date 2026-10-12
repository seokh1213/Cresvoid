---
id: C-kotlin-30
title: 주문 이벤트 처리
kind: code
track: code-reading
stage: order-events
status: 후보
wave: 1
env: 없음
prereq: 
---

# C-kotlin-30 주문 이벤트 처리

- 도메인: 결제와 배송 이벤트가 순서 없이 중복되어 들어오고 주문 상태를 계산한다. 이벤트는 주문 ID, 순번, 종류, 시각을 가진다. 처리기는 중복을 거르고 상태 전이 규칙을 적용해 주문별 최종 상태와 거부한 이벤트를 출력한다. 상태는 sealed 타입으로 표현하고 전이 규칙, 중복 제거, 집계, 명령 처리를 파일로 나눈다.
- 겨냥: `Sequence` 는 최종 연산마다 처음부터 다시 계산해 부수효과가 그만큼 반복된다, `distinctBy` 는 앞의 원소를 남기고 `associateBy` 는 뒤의 원소를 남긴다, `windowed` 는 기본값에서 모자란 마지막 창을 버리고 `chunked` 의 마지막 묶음은 작을 수 있다, sealed 타입 상태 전이, inline 함수 람다 안의 `return` 은 바깥 함수를 끝내고 `return@forEach` 는 다음 원소로 넘어간다, `runCatching` 안의 `finally` 가 `onFailure` 보다 먼저 실행된다, `lazy` 는 처음 읽을 때 한 번 초기화한다.
- 형식: 320~400줄, 파일 4~5개, 6문항(실행 결과 예측 short 2~3, 나머지 choice), 목표 30분.
- 난도: 첫 세 단계 가운데 가장 어렵다. 지연 평가와 람다 반환이 섞인 흐름을 끝까지 추적해야 한다.
- 검증: 실행으로 확인한다. Sequence 의 부수효과 횟수는 실행 기록으로 확인한다.
- 출처: Kotlin 문서의 Sequences, Returns and jumps, Delegated properties(lazy). Kotlin 표준 라이브러리 문서의 `distinctBy`, `associateBy`, `windowed`, `chunked`, `runCatching`.
- 피할 것: C-kotlin-10, C-kotlin-20 의 함정을 다시 쓰는 것.
