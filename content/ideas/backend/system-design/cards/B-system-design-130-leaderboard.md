---
id: B-system-design-130
title: 실시간 순위표 만들기
kind: build
track: leaderboard
stage: 01-sorted-set, 02-range-queries, 03-sharding
status: 후보
wave: 4
env: 없음
prereq: 
---

# B-system-design-130 실시간 순위표 만들기

- 목표: 점수 갱신과 순위 조회가 빠른 순위표를 만든다.
- 1단계 `01-sorted-set`: 점수 더하기와 설정, 순위 조회, 동점 규칙(먼저 그 점수에 이른 사람이 앞).
- 2단계 `02-range-queries`: 상위 N, 내 주변 순위, 기간별 순위표와 초기화.
- 3단계 `03-sharding`: 점수 구간으로 나눈 샤드와 해시로 나눈 샤드에서 전체 순위 계산의 비용 비교.
- 난도: 단계마다 2시간 안팎. 대규모 테스트는 사용자 수백만 명이다. 순위를 구할 때마다 정렬하면 제한 시간을 넘게 만든다.
- 함정: 동점 규칙이 실제 Redis 정렬 집합(같은 점수면 멤버 이름 순서)과 다르다는 점, 점수가 깎이는 갱신, 기간 경계.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 2권 10장의 주제, Redis 정렬 집합 문서.
