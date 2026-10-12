---
id: L-load-100
title: 멱등 소비자와 순서
kind: lab
track: backend-lab
stage: idempotent-consumer
status: 보류(메시지 브로커 실행 방식 결정 필요)
wave: 5
env: docker, k6
prereq: 
---

# L-load-100 멱등 소비자와 순서

- 목표: 중복과 순서 뒤바뀜이 있는 이벤트 흐름에서 상태를 올바르게 유지한다.
- 메모: Kafka 를 Docker 로 띄울지, 재전송과 순서 뒤섞기를 흉내 내는 메모리 큐로 대신할지 정한 뒤 카드를 채운다.
