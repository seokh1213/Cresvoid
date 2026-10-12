---
id: L-load-30
title: 캐시 스탬피드
kind: lab
track: backend-lab
stage: cache-stampede
status: 후보
wave: 3
env: k6, redis
prereq: 
---

# L-load-30 캐시 스탬피드

- 모드: implement
- 난도: 보통.
- 목표: 인기 키가 만료될 때 원천 조회가 한 번만 일어나게 하고 잠금의 함정을 피한다.
- 서비스: `GET /products/{id}`. Redis cache-aside, TTL 5초. 원천 조회 200ms. 인기 상품 하나에 요청의 80% 가 몰린다.
- 비울 핵심: `ProductCache.get(id)`. Redis `SET NX PX` 잠금과 토큰 확인 해제(Lua), 대기 방식, 잠금 TTL 을 정한다.
- 시나리오: constant-arrival-rate 500/s 로 40초. 인기 키가 여러 번 만료된다. 두 번째 시나리오는 원천 지연을 1초로 올려 잠금 TTL 보다 길게 만든다.
- 불변식: 인기 키 원천 조회 수 ≤ 만료 횟수 + 2, 5xx 0건, 2초 넘게 기다린 요청 0건.
- 오답 구현(must_fail): 잠금 없음, 토큰 확인 없이 `DEL`, 잠금 TTL 이 재계산보다 짧고 연장도 없음.
- 문항이 노릴 것: 잠금 TTL 과 재계산 시간, 남의 잠금 해제, 잠금 보유자 장애, 짧은 sleep 대기가 만드는 Redis 호출 폭주, 프로세스 안 single-flight 와 분산 잠금의 범위 차이, XFetch 의 확률적 조기 갱신.
