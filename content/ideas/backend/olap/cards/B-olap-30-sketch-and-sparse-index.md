---
id: B-olap-30
title: 근사 집계와 희소 인덱스
kind: build
track: mini-olap
stage: 04-sketch, 05-sparse-index
status: 후보
wave: 3
env: 없음
prereq: B-olap-20
---

# B-olap-30 근사 집계와 희소 인덱스

- 목표: B-olap-20 엔진에 HyperLogLog 근사 고유 수와 정렬 키 기반 희소 인덱스를 더한다.
- 4단계 `04-sketch`: `APPROX_COUNT_DISTINCT`. 해시 함수(예: UTF-8 바이트의 FNV-1a 64비트), 레지스터 수 2^p, 추정식, 작은 범위 보정을 명세가 모두 정해 출력이 결정적이다. 일별 스케치를 저장하고 기간 질의에서 병합한다. 정확한 고유 수와의 상대 오차를 함께 보고한다.
- 5단계 `05-sparse-index`: 테이블에 정렬 키와 그래뉼 크기를 정한다. 질의마다 결과와 함께 읽은 그래뉼 수와 행 수를 출력한다. 정렬 키 앞 열 조건, 뒤 열만의 조건, 범위 조건에서 건너뛰는 그래뉼이 다르다.
- 난도: 단계마다 2~3시간.
- 함정: 해시의 부호 없는 비트 다루기, 레지스터 값의 앞자리 0 개수 계산, 빈 스케치 병합, 그래뉼 경계에 걸친 키, 정렬 키 뒤 열만으로는 거의 건너뛰지 못하는 경우.
- 검증: build 스킬의 교차 검증. HyperLogLog 추정식은 Flajolet 외 2007 과 대조하고 스펙이 정한 변형을 밝힌다.
- 출처: Flajolet 외 AofA 2007, Heule 외 EDBT 2013, ClickHouse MergeTree 문서.
- 피할 것: 실제 ClickHouse 나 Redis 의 HyperLogLog 구현 세부를 그대로 요구하는 것.
