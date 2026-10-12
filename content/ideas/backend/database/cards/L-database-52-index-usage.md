---
id: L-database-52
title: 인덱스를 쓰지 못하는 질의
kind: lab
track: db-lab
stage: index-usage
status: 후보
wave: 5
env: docker, maven
prereq: 
---

# L-database-52 인덱스를 쓰지 못하는 질의

- 모드: tune
- 실행 방식: Docker 의 MySQL 8.4 에 상품 100만 행을 넣고 느린 질의 묶음을 받는다.
- 목표: 실행 계획을 읽고 함수 감싼 열, 자료형이 다른 비교, 앞 열이 빠진 복합 인덱스 조건, 오프셋 페이지네이션을 고친다.
- 불변식: 질의마다 결과가 기준과 같고 읽은 행 수가 기준 이하.
- 출처: MySQL 8.4 Optimization 장(EXPLAIN, 인덱스 사용).
