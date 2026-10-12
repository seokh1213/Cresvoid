---
id: C-kotlin-20
title: 재고 예약과 만료
kind: code
track: code-reading
stage: stock-reservation
status: 후보
wave: 1
env: 없음
prereq: 
---

# C-kotlin-20 재고 예약과 만료

- 도메인: 주문 전에 재고를 잠시 예약하고 시간이 지나면 풀어 주는 서비스. 시각은 입력 명령으로 움직인다. 예약, 확정, 취소, 시각 이동 명령을 처리하고 상품별 가용 재고와 만료된 예약을 출력한다. 만료를 기다리는 예약은 만료 시각 순서의 우선순위 큐에 둔다.
- 겨냥: `java.time` 의 경계 비교(만료 시각과 같은 시각에서는 `isBefore` 와 `isAfter` 가 모두 false), `ZoneId.of("Asia/Seoul")` 로 계산한 날짜 경계, `getOrPut`, `associateBy` 와 `groupBy` 의 차이, `PriorityQueue` 는 동점 원소를 넣은 순서대로 꺼낸다는 보장이 없다, 순회 중 제거(`removeIf` 와 반복자의 `remove` 는 안전하다. `for` 로 `ArrayList` 를 돌며 리스트의 `remove` 를 부르면 대개 ConcurrentModificationException 이 나지만 끝에서 두 번째 원소를 지우면 예외 없이 마지막 원소를 건너뛴다), `sumOf`.
- 형식: 250~330줄, 파일 3~5개, 6문항(실행 결과 예측 short 2~3, 나머지 choice), 목표 25분.
- 난도: C-kotlin-10 보다 추적할 상태가 많다. 예약 목록과 시각을 함께 따라가야 풀린다.
- 검증: 실행으로 확인한다. PriorityQueue 의 동점 꺼냄 순서는 정답으로 쓰지 않는다. 동점을 다루는 문항은 "순서를 보장하지 않는다" 를 정답으로 둔다.
- 출처: Java SE 21 API 문서의 `Instant`, `ZonedDateTime`, `PriorityQueue`(동점은 임의로 정한다), `Iterator.remove`, `Collection.removeIf`, `ArrayList`(fail-fast 반복자). Kotlin 표준 라이브러리 문서의 `getOrPut`, `associateBy`, `groupBy`, `sumOf`.
- 피할 것: 실제 시계를 읽는 코드. C-kotlin-10 의 BigDecimal 함정을 다시 쓰는 것.
