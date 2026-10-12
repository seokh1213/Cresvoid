---
id: L-database-50
title: 갭 잠금과 삽입 교착
kind: lab
track: db-lab
stage: gap-lock
status: 후보
wave: 4
env: docker, maven
prereq: R-database-10
---

# L-database-50 갭 잠금과 삽입 교착

- 모드: observe 뒤 implement
- 실행 방식: Docker 의 MySQL 8.4 에 두 세션을 열어 정해진 순서로 문장을 실행하는 Kotlin 실험.
- 목표: REPEATABLE READ 에서 범위 잠금 읽기 뒤 삽입이 교착하는 순서를 예측하고 READ COMMITTED 나 조건부 갱신으로 피한다.
- 예측 문항: 각 문장이 바로 끝나는지 기다리는지, 교착이 나면 어느 세션이 실패하는지.
- 출처: MySQL 8.4 Transaction Isolation Levels, Locking Reads.
