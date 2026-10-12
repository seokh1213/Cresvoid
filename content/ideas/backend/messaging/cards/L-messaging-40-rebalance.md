---
id: L-messaging-40
title: 느린 처리와 재조정 폭풍
kind: lab
track: kafka-lab
stage: rebalance
status: 후보
wave: 5
env: docker, maven
prereq: B-messaging-20
---

# L-messaging-40 느린 처리와 재조정 폭풍

- 모드: observe 뒤 tune
- 실행 방식: Docker 의 Kafka 와 Kafka 클라이언트 의존성을 쓴 Kotlin 컨슈머.
- 목표: 처리 시간이 poll 간격 한도를 넘을 때 재조정이 되풀이되며 같은 메시지를 다시 처리하는 과정을 관찰하고 설정과 처리 구조로 푼다.
- 불변식: 같은 메시지의 처리 횟수가 기준 이하, 재조정 횟수가 기준 이하, 처리 지연이 기준 이하.
- 출처: Kafka Consumer Configs(max.poll.interval.ms, max.poll.records), Consumer Rebalance Protocol.
