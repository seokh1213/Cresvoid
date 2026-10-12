---
id: L-load-10
title: 재고 차감과 동시 주문
kind: lab
track: backend-lab
stage: stock-race
status: 후보
wave: 3
env: k6
prereq: 
---

# L-load-10 재고 차감과 동시 주문

- 모드: implement
- 난도: 쉬움. 동시성을 처음 다루는 단계다.
- 목표: 읽고-계산하고-쓰는 갱신이 동시 요청에서 깨지는 이유를 설명하고 원자적 갱신으로 고친다.
- 서비스: `POST /orders` 가 `{productId, quantity}` 를 받아 재고를 줄이고 201 을 준다. 재고가 모자라면 409 다. 재고 저장소는 메모리에 있고 읽기와 쓰기 사이에 10ms 지연이 들어 있다.
- 비울 핵심: `OrderService.place(productId, quantity)` 의 재고 확인과 차감.
- 시나리오: 상품 하나의 재고 100개. constant-arrival-rate 400/s 로 20초. 예열 5초 뒤 측정.
- 불변식: 201 응답 수 = 100 − 남은 재고, 남은 재고 ≥ 0, 5xx 0건.
- 오답 구현(must_fail): 잠금 없는 확인 후 차감, `AtomicInteger.get()` 뒤 `set()`.
- 문항이 노릴 것: 확인 후 행동, `get` 뒤 `set` 의 비원자성, `synchronized` 가 인스턴스 둘에서 쓸모없는 이유, `compute` 안의 지연이 다른 키를 막는 문제, DB 였다면 쓸 조건부 UPDATE 와 영향 행 수 판정.
- 피할 것: 지연 주입 없이 경합을 기대하는 것. 운 좋게 통과하는 오답 구현이 생긴다.
