---
id: L-olap-41
title: 병합 때만 지워지는 중복
kind: lab
track: olap-lab
stage: replacing-merge
status: 후보
wave: 4
env: docker
prereq: L-olap-40
---

# L-olap-41 병합 때만 지워지는 중복

- 모드: observe 뒤 implement
- 실행 방식: Docker 의 ClickHouse 에 ReplacingMergeTree 테이블을 만들고 같은 키의 갱신 행을 여러 번 넣는다.
- 목표: 병합 전후로 집계 결과가 달라지는 이유를 설명하고 병합 시점과 무관하게 맞는 질의를 쓴다.
- 예측 문항: 병합 전 COUNT, FINAL 을 붙인 COUNT, 버전 열이 없을 때 남는 행.
- implement 목표: argMax 나 FINAL 로 언제 실행해도 같은 결과를 내는 질의를 쓴다.
- 출처: ClickHouse ReplacingMergeTree 문서.
