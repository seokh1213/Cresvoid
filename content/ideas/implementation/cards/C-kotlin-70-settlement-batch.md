---
id: C-kotlin-70
title: 정산 배치
kind: code
track: code-reading
stage: settlement-batch
status: 후보
wave: 5
env: 없음
prereq: 
---

# C-kotlin-70 정산 배치

- 도메인: 판매자별 월 정산을 계산하는 배치. 주문 행(판매자, 주문일, 수량, 단가, 수수료율)을 읽어 판매자와 월별 매출, 수수료, 지급액을 내고 지급일을 정한다. 읽기, 집계, 지급일 계산, 출력으로 파일을 나눈다.
- 겨냥: `groupingBy { }.fold` 와 `groupBy().mapValues` 의 같은 결과와 다른 중간 객체, `groupBy` 는 키를 처음 나온 순서로 둔다, Int 곱셈 오버플로(`50_000 * 50_000` 은 -1794967296)와 `Long.toInt()` 의 비트 잘림(`3_000_000_000L.toInt()` 은 -1294967296), `zip` 은 짧은 쪽에 맞춰 남는 원소를 버린다, `plusMonths(1)` 은 1월 31일을 2월 말일로 맞춘다, `maxByOrNull` 은 동점이면 앞의 원소를 고른다.
- 형식: 280~380줄, 파일 4~5개, 6문항(실행 결과 예측 short 3, choice 3), 목표 30분.
- 난도: 큰 수의 오버플로 결과를 따져야 하는 문항이 있다. 계산에 필요한 값은 guide.md 에 준다.
- 검증: 실행으로 확인한다.
- 출처: Kotlin 표준 라이브러리 문서의 `groupingBy`, `fold`, `zip`, `maxByOrNull`, `Long.toInt`. Java SE 21 API 문서의 `LocalDate.plusMonths`, `Math.multiplyExact`.
- 피할 것: 비교자 계약 위반(F-kotlin-50 이 다룬다)과 Double 오차(F-kotlin-110 이 다룬다).
