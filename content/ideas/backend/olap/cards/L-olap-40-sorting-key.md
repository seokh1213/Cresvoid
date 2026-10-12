---
id: L-olap-40
title: 정렬 키와 읽는 그래뉼 수
kind: lab
track: olap-lab
stage: sorting-key
status: 후보
wave: 4
env: docker
prereq: R-olap-10
---

# L-olap-40 정렬 키와 읽는 그래뉼 수

- 모드: observe 뒤 tune
- 실행 방식: Docker 의 ClickHouse 에 같은 데이터를 정렬 키만 다르게 세 번 적재한다.
- 목표: 정렬 키의 열 순서가 질의마다 읽는 그래뉼 수와 압축률을 어떻게 바꾸는지 예측한다.
- 예측 문항: 질의별로 읽는 행 수가 가장 적은 테이블, 파티션 키를 날짜로 잘게 나눴을 때의 변화.
- tune 목표: 주어진 질의 묶음의 읽는 행 수 합을 기준 이하로 만드는 정렬 키를 고른다.
- 출처: ClickHouse MergeTree 문서.
