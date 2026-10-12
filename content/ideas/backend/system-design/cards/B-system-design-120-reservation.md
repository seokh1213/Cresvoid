---
id: B-system-design-120
title: 호텔 예약 재고 만들기
kind: build
track: reservation
stage: 01-inventory, 02-idempotency, 03-concurrency
status: 후보
wave: 4
env: 없음
prereq: 
---

# B-system-design-120 호텔 예약 재고 만들기

- 목표: 날짜별 객실 재고를 예약과 취소와 동시 요청 속에서 바르게 유지한다.
- 1단계 `01-inventory`: 객실 유형과 날짜별 재고, 여러 박 예약, 초과 예약 비율, 취소.
- 2단계 `02-idempotency`: 예약 요청 ID 로 두 번 누른 요청 거르기, 같은 ID 에 다른 내용이 온 경우.
- 3단계 `03-concurrency`: 명세가 정한 순서로 섞인 동시 요청에서 비관적 잠금, 낙관적 잠금(버전), 제약 조건 방식의 결과와 재시도 수 비교.
- 난도: 단계마다 2시간 안팎.
- 함정: 박 수 계산의 마지막 날, 부분 취소, 초과 예약 비율의 반올림.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 2권 7장의 주제.
