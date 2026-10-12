---
id: B-system-design-30
title: 분산 고유 ID 생성기 만들기
kind: build
track: id-generator
stage: 01-snowflake, 02-sharded-ids, 03-ordering
status: 후보
wave: 3
env: 없음
prereq: 
---

# B-system-design-30 분산 고유 ID 생성기 만들기

- 목표: 조율 없이 여러 서버가 고유하고 대략 시간순인 64비트 ID 를 만드는 방법을 구현하고 시계 문제와 샤딩을 다룬다.
- 1단계 `01-snowflake`: 시각·기계·순번 비트 배치(비트 수는 인자), 같은 밀리초에 순번이 다 차면 다음 밀리초까지 기다리기, 시계가 뒤로 가면 거절하거나 기다리는 규칙, ID 해석.
- 2단계 `02-sharded-ids`: ID 안에 논리 샤드 번호를 담는 방식(Instagram), 논리 샤드를 물리 서버에 대응시키는 표, 논리 샤드를 옮기는 재배치, ID 만으로 샤드 찾기.
- 3단계 `03-ordering`: 생성기 여러 대의 ID 흐름을 합칠 때의 순서, 서버 사이 시계 차이가 정렬에 주는 영향, k-정렬 보장 검사.
- 난도: 단계마다 2시간 안팎.
- 함정: 부호 비트, 순번 넘침 직후의 시각, 기준 시각 이전의 시각, 비트 수 합이 64를 넘는 설정, 시계가 크게 뒤로 간 경우.
- 검증: build 스킬의 교차 검증.
- 출처: Twitter Snowflake README(2010), Instagram "Sharding & IDs at Instagram"(2012).
- 피할 것: UUID 나 DB 시퀀스 같은 대안을 구현하게 하는 것. 대안 비교는 스펙의 배경 개념 절에 쓴다.
