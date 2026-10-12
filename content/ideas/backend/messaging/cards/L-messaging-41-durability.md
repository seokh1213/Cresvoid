---
id: L-messaging-41
title: acks 와 최소 동기 복제본
kind: lab
track: kafka-lab
stage: durability
status: 후보
wave: 5
env: docker, maven
prereq: R-messaging-10
---

# L-messaging-41 acks 와 최소 동기 복제본

- 모드: observe
- 실행 방식: Docker 로 브로커 3개를 띄우고 쓰는 도중 브로커를 멈춘다.
- 목표: acks, min.insync.replicas, 복제 수 조합마다 장애 뒤 남는 메시지와 쓰기 실패를 예측한다.
- 예측 문항: 조합별 실패한 쓰기 수, 잃은 메시지 수, NotEnoughReplicas 가 나는 시점.
- 출처: Kafka Topic Configs(min.insync.replicas, unclean.leader.election.enable), Producer Configs.
