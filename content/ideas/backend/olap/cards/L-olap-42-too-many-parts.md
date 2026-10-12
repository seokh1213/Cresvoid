---
id: L-olap-42
title: 작은 삽입과 파트 폭증
kind: lab
track: olap-lab
stage: too-many-parts
status: 후보
wave: 5
env: docker, k6
prereq: L-olap-40
---

# L-olap-42 작은 삽입과 파트 폭증

- 모드: tune
- 실행 방식: Docker 의 ClickHouse 에 k6 로 행 한두 개짜리 삽입을 빠르게 보낸다.
- 목표: 삽입마다 파트가 생기고 병합이 따라가지 못할 때 생기는 일을 관찰하고 묶음 삽입으로 고친다.
- 불변식: 같은 행 수를 넣는 동안 활성 파트 수가 기준 이하, 삽입 오류 0건.
- 출처: ClickHouse MergeTree 문서. 파트 수 한도 설정은 쓰는 버전의 문서로 확인한다.
