---
id: L-load-60
title: 캐시 장애와 원천 보호
kind: lab
track: backend-lab
stage: cache-outage
status: 후보
wave: 3
env: k6, redis
prereq: 
---

# L-load-60 캐시 장애와 원천 보호

- 모드: implement
- 난도: 어려움.
- 목표: 캐시가 죽어도 원천이 함께 무너지지 않게 한다.
- 서비스: L-load-30 서비스. 원천의 초당 처리 한도는 100 이다. 엔진이 10초에 Redis 를 종료하고 25초에 다시 시작한다.
- 비울 핵심: 캐시 호출 타임아웃, 회로 차단, 원천 요청률 상한, 로컬 소프트 TTL 캐시.
- 불변식: 초당 원천 호출 ≤ 120, 장애 구간 오류율 ≤ 30%(503 과 Retry-After), Redis 가 복구된 뒤 5초 안에 오류 0, 3초 넘는 응답 0건.
- 문항이 노릴 것: 클라이언트 기본 타임아웃, 재연결 폭주, 재시작 뒤 빈 캐시의 동시 만료, gutter 풀 같은 우회 캐시.
