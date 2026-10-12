---
id: B-search-30
title: 세그먼트와 translog 시뮬레이터 만들기
kind: build
track: nrt-index
stage: 01-refresh, 02-durability, 03-concurrency
status: 후보
wave: 3
env: 없음
prereq: R-search-10
---

# B-search-30 세그먼트와 translog 시뮬레이터 만들기

- 목표: 샤드 하나의 색인 경로를 시뮬레이터로 만들어 보임, 내구성, 동시성 규칙을 구현한다.
- 1단계 `01-refresh`: 메모리 버퍼, refresh 로 만드는 세그먼트, 삭제 표시, 병합 정책(명세가 정한 단순한 규칙), 검색이 없던 샤드의 refresh 생략.
- 2단계 `02-durability`: translog 와 fsync 시점, 내구성 request 와 async, flush, 장애와 재시작 뒤 되살아나는 문서.
- 3단계 `03-concurrency`: 시퀀스 번호와 주 임기, 조건부 색인의 충돌, 복제본에 순서가 뒤바뀌어 도착한 연산의 적용 규칙.
- 난도: 단계마다 2시간 안팎.
- 함정: 같은 id 의 재색인이 삭제와 추가로 나뉘는 점, async 에서 잃는 연산의 범위, 주 샤드가 바뀐 뒤의 임기.
- 검증: build 스킬의 교차 검증.
- 출처: Elastic 문서의 Near real-time search, Translog settings, Optimistic concurrency control.
- 피할 것: Lucene 파일 형식을 요구하는 것.
