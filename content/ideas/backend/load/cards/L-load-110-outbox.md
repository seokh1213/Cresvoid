---
id: L-load-110
title: 트랜잭셔널 아웃박스
kind: lab
track: backend-lab
stage: 12-outbox
status: 완료(2026-11-06)
wave: 5
env: docker
prereq:
---

# L-load-110 트랜잭셔널 아웃박스

- 목표: DB 커밋과 이벤트 발행이 함께 성공하거나 함께 실패한 것처럼 보이게 한다.
- 모드: observe 뒤 implement
- 실행 방식: PostgreSQL 과 Docker 의 Kafka(KRaft 단일 노드, lab-env 고정 이미지). 주문 저장과 이벤트 발행 사이에 실패를 넣는다.
- 관찰: 커밋 뒤 발행 실패로 사라지는 이벤트, 발행 뒤 롤백으로 생기는 유령 이벤트.
- 불변식: 커밋된 주문마다 이벤트가 적어도 한 번 발행되고 소비 쪽 반영은 한 번이다. 롤백된 주문의 이벤트는 0건이다.
- 출처: microservices.io 의 Transactional outbox 패턴.
