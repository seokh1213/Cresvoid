---
id: L-load-90
title: 속도 제한
kind: lab
track: backend-lab
stage: rate-limit
status: 후보
wave: 5
env: k6, redis
prereq: 
---

# L-load-90 속도 제한

- 모드: implement
- 난도: 보통.
- 목표: 사용자별 속도 제한을 Redis 로 여러 인스턴스에 걸쳐 지킨다.
- 비울 핵심: 토큰 버킷이나 미끄러지는 창을 Lua 스크립트로 원자적으로 구현한다.
- 불변식: 어떤 1초 구간에서도 사용자별 허용 요청 수 ≤ 한도 + 버스트, 인스턴스 둘에서도 같다.
- 문항이 노릴 것: 고정 창 경계의 두 배 폭주, `INCR` 와 `EXPIRE` 사이의 장애.
