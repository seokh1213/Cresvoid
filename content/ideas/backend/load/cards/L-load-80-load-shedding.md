---
id: L-load-80
title: 과부하와 load shedding
kind: lab
track: backend-lab
stage: 08-load-shedding
status: 제작 중
wave: 5
env: 없음
prereq:
---

# L-load-80 과부하와 load shedding

- 모드: implement
- 난도: 어려움.
- 목표: 처리 한도를 넘는 요청이 와도 받아들인 요청의 지연을 지킨다.
- 비울 핵심: 동시 처리 상한, 큐 길이 제한, 처리 전 마감 시각 확인, 빠른 503.
- 불변식: 받아들인 요청의 p99 ≤ 기준, goodput ≥ 처리 한도의 90%.
