---
id: B-mini-sql-10
title: 작은 SQL 엔진
kind: build
track: mini-sql
stage: 01-select, 02-group, 03-join
status: 후보
wave: 5
env: 없음
prereq: 
---

# B-mini-sql-10 작은 SQL 엔진

- 목표: 작은 SQL 부분집합의 파서와 실행기를 만든다.
- 1단계: `CREATE TABLE`, `INSERT`, `SELECT 열 FROM 표 WHERE 조건`, 비교와 논리 연산, NULL 의 3값 논리.
- 2단계: `ORDER BY`(여러 열, NULL 순서), `LIMIT`, `GROUP BY`, 집계 함수(COUNT, SUM, AVG, MIN, MAX)와 NULL 처리, `HAVING`.
- 3단계: `INNER JOIN`, `LEFT JOIN`. 대규모 테스트가 중첩 루프 조인으로는 제한 시간을 넘게 만들어 해시 조인을 유도한다.
- 함정: `COUNT(*)` 와 `COUNT(열)` 의 NULL 차이, 빈 그룹의 집계, 정렬 안정성.
