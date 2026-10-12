---
id: B-system-design-40
title: 결제 원장 만들기
kind: build
track: ledger
stage: 01-double-entry, 02-psp-idempotency, 03-reconciliation
status: 후보
wave: 3
env: 없음
prereq: 
---

# B-system-design-40 결제 원장 만들기

- 목표: 복식 부기 원장과 외부 결제 대행사(PSP) 호출을 멱등하게 다루고 정산 파일과 대조한다.
- 1단계 `01-double-entry`: 계정, 차변과 대변의 합이 같은 분개, 잔액, 음수가 될 수 없는 계정, 금액은 정수 최소 단위.
- 2단계 `02-psp-idempotency`: 결제 요청의 상태 전이, 멱등 키, 사전 기록·원격 호출·사후 기록의 세 단계(Airbnb Orpheus), 응답을 받지 못한 결제의 상태 조회, 다시 시도할 수 있는 오류와 없는 오류.
- 3단계 `03-reconciliation`: 대행사 정산 파일과 원장 비교, 불일치 유형(누락, 금액 차이, 중복) 분류와 보고.
- 난도: 단계마다 2~3시간.
- 함정: 시간 초과 뒤 성공한 결제, 같은 멱등 키에 다른 금액, 환불이 원결제보다 큰 경우, 통화가 다른 금액.
- 검증: build 스킬의 교차 검증. 모든 테스트에서 분개 합이 0 인지 함께 확인한다.
- 출처: System Design Interview 2권 11장의 주제, Airbnb Orpheus 글(이 도메인 README 의 '빅테크 공개 사례' 절), Stripe 멱등 키 글.
