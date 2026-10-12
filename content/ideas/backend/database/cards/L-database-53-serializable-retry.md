---
id: L-database-53
title: 직렬화 실패와 재시도
kind: lab
track: db-lab
stage: serializable-retry
status: 후보
wave: 5
env: docker, maven
prereq: B-database-20
---

# L-database-53 직렬화 실패와 재시도

- 모드: implement
- 실행 방식: Docker 의 PostgreSQL 에 SERIALIZABLE 트랜잭션을 동시에 실행하는 Kotlin 실험.
- 목표: 40001 실패를 처음부터 다시 실행하는 재시도 루프를 만들고 재시도가 결과를 바르게 지키는지 확인한다.
- 불변식: 쓰기 비틀림이 없다. 재시도 횟수의 상한을 지킨다. 재시도 사이 지터가 있다.
- 출처: PostgreSQL Transaction Isolation 문서.
