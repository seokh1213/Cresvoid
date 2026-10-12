---
id: L-database-51
title: 긴 트랜잭션과 스키마 변경 대기
kind: lab
track: db-lab
stage: metadata-lock
status: 후보
wave: 4
env: docker, maven, k6
prereq: R-database-10
---

# L-database-51 긴 트랜잭션과 스키마 변경 대기

- 모드: observe
- 실행 방식: Docker 의 MySQL 8.4 와 조회 API 에 k6 로 부하를 넣는 동안 열어 둔 트랜잭션과 ALTER TABLE 을 실행한다.
- 목표: 끝나지 않은 트랜잭션 하나가 스키마 변경을 막고 그 뒤 모든 질의가 줄을 서는 과정을 관찰한다.
- 예측 문항: ALTER 를 실행한 뒤 조회 API 의 오류율과 지연, 트랜잭션을 끝낸 뒤 회복 시간.
- 출처: MySQL 8.4 Metadata Locking 문서.
