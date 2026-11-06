---
id: L-load-100
title: 멱등 소비자와 순서
kind: lab
track: backend-lab
stage: 11-idempotent-consumer
status: 완료(2026-11-06)
wave: 5
env: docker
prereq:
---

# L-load-100 멱등 소비자와 순서

- 목표: 중복과 순서 뒤바뀜이 있는 이벤트 흐름에서 상태를 올바르게 유지한다.
- 모드: observe 뒤 implement
- 실행 방식: Docker 의 Kafka(KRaft 단일 노드, lab-env 고정 이미지)에 같은 이벤트를 중복으로 넣고 엔터티 키를 섞어 보낸다. Kotlin 소비자가 이벤트를 PostgreSQL 에 반영한다.
- 목표: 재전송으로 생긴 중복, 처리 뒤 오프셋 커밋 전에 죽은 소비자의 재처리, 엔터티 사이의 순서 차이 속에서 최종 상태를 올바르게 유지한다.
- 불변식: 이벤트 ID 마다 반영 1회, 엔터티별 버전 역행 0건, 최종 상태가 기준 상태와 같다.
- 출처: Kafka 문서의 Message Delivery Semantics 와 Consumer Configs(enable.auto.commit).
