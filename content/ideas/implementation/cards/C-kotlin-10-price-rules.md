---
id: C-kotlin-10
title: 가격 규칙 계산
kind: code
track: code-reading
stage: 01-price-rules
status: 완료(2026-10-08)
wave: 1
env: 없음
prereq: 
---

# C-kotlin-10 가격 규칙 계산

- 도메인: 회원 등급 할인, 쿠폰, 묶음 할인을 장바구니에 적용해 결제 금액을 낸다. 표준 입력으로 상품, 장바구니 항목, 회원 등급, 쿠폰을 받아 항목별 할인과 결제 금액을 출력하는 명령 처리기다. 할인 규칙은 sealed 타입으로 나누고 등급 할인, 묶음 할인, 쿠폰 순서로 적용한다. 파일은 금액과 반올림, 할인 규칙, 적용 순서, 명령 처리로 나눈다.
- 겨냥: `BigDecimal("2.0")` 과 `BigDecimal("2.00")` 은 `equals` 로는 다르고 `compareTo` 로는 같다(`HashSet` 에 둘 다 들어간다), `setScale` 의 반올림 모드와 반올림 시점(항목마다 반올림한 합과 합계를 한 번 반올림한 값의 차이), data class 의 `copy` 는 안의 가변 리스트를 복사하지 않고 함께 쓴다, `sortedWith(compareBy(...))` 는 안정 정렬이라 동점이면 입력 순서를 지킨다, `fold` 의 초깃값과 누적 순서, sealed 타입 `when` 의 분기.
- 형식: 180~250줄, 파일 3~4개, 5문항(실행 결과 예측 short 2, choice 3), 목표 20분.
- 난도: 숙련 Kotlin 개발자가 실행 없이 읽으면 한두 문항을 틀리는 수준. 첫 단계라 추적할 상태는 장바구니 하나로 제한한다.
- 검증: 문항 정답과 해설에 쓴 동작은 모두 실행으로 확인한다. BigDecimal 비교와 반올림 결과는 짧은 스크립트로 따로 확인한다.
- 출처: Java SE 21 API 문서의 `BigDecimal`(equals, compareTo, setScale)과 `RoundingMode`. Kotlin 문서의 Data classes, 표준 라이브러리 문서의 `sortedWith` 와 `compareBy`.
- 피할 것: Double 오차를 함정으로 쓰는 것(F-kotlin-20, F-kotlin-110 이 다룬다). 일부러 이상한 scale 의 금액을 만드는 것.
