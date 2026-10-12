---
id: L-load-120
title: 적응형 동시성 제한
kind: lab
track: backend-lab
stage: adaptive-concurrency
status: 후보
wave: 5
env: k6
prereq: L-load-80
---

# L-load-120 적응형 동시성 제한

- 모드: implement
- 난도: 어려움.
- 목표: 하위 서비스의 처리 능력이 실험 도중 바뀌어도 고정 한도 없이 동시 실행 한도를 스스로 맞춰 받아들인 요청의 지연과 goodput 을 지킨다.
- 비울 핵심: 지연 측정으로 한도를 늘리고 줄이는 제한기, 한도를 넘는 요청의 빠른 429.
- 시나리오: constant-arrival-rate 로 처리 능력보다 조금 높은 부하를 가하고 실험 중간에 하위 서비스의 처리 능력을 절반으로 줄였다가 되돌린다.
- 불변식: 받아들인 요청의 p99 ≤ 기준, 처리 능력이 바뀐 뒤 정해진 시간 안에 goodput 이 다시 기준에 이른다.
- 오답 구현(must_fail): 처음 처리 능력에 맞춘 고정 동시 실행 한도, 고정 RPS 한도.
- 문항이 노릴 것: 리틀의 법칙으로 동시 실행 수를 추정하는 법, 고정 RPS 한도가 처리 능력 변화를 따라가지 못하는 이유, 최소 지연 추정이 오래된 값에 묶이는 문제, 거절 응답이 클라이언트 재시도 폭주를 부르는 경우.
- 출처: Netflix concurrency-limits([../../system-design/README.md](../../system-design/README.md) 의 '빅테크 공개 사례' 절).
