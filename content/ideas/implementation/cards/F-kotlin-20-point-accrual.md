---
id: F-kotlin-20
title: 포인트 적립 계산
kind: fix
track: bug-fix
stage: point-accrual
status: 후보
wave: 1
env: 없음
prereq: 
---

# F-kotlin-20 포인트 적립 계산

- 맥락: 결제 금액과 적립률로 포인트를 계산하고 일별 정산 합계를 낸다. 적립 포인트는 결제 금액에 적립률을 곱해 내림한 정수다. 적립률은 등급마다 다르고 이벤트 기간에는 더해진다.
- 버그 후보: Double 로 곱한 뒤 내림해 1포인트가 모자란다(먼저 고른다. `100 * 0.29` 는 28.999999999999996 이라 내림하면 28 이다), 규칙은 내림인데 `RoundingMode.HALF_UP` 으로 반올림해 경계 금액에서 1포인트가 많다, 일별 합계를 Int 로 더해 큰 정산일에 음수가 된다.
- 버그 신고: "적립률 29% 이벤트에서 100원 결제의 적립 포인트가 29가 아니라 28로 나온다. 다른 금액은 대부분 맞다." 재현 입력과 기대한 결과, 실제 결과를 적는다.
- 형식: 220~320줄, 파일 3~4개.
- 난도: 증상에서 원인까지 두세 단계 추론이 필요한 수준. 특정 금액에서만 어긋나 재현 범위를 좁혀야 한다.
- 검증: 숨은 테스트는 Double 로 계산해도 우연히 맞는 금액과 어긋나는 금액을 함께 둔다. 어긋나는 금액은 실행으로 찾아 싣는다. BigDecimal 이나 정수 연산으로 고친 수정이 모두 통과해야 한다.
- 출처: Goldberg, "What Every Computer Scientist Should Know About Floating-Point Arithmetic", ACM Computing Surveys 1991. Java SE 21 API 문서의 `BigDecimal`, `RoundingMode`.
- 피할 것: C-kotlin-10 의 BigDecimal 비교 함정, F-kotlin-110 의 누적 오차와 같은 원인. `BigDecimal.equals` 로 중복 판정에 실패하는 후보는 F-kotlin-40 과 겹치므로 쓰지 않는다.
