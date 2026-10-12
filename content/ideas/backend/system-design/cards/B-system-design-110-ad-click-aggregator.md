---
id: B-system-design-110
title: 광고 클릭 집계기 만들기
kind: build
track: ad-click-aggregator
stage: 01-windows, 02-late-events, 03-exactly-once
status: 후보
wave: 4
env: 없음
prereq: 
---

# B-system-design-110 광고 클릭 집계기 만들기

- 목표: 이벤트 시각 기준의 창 집계를 늦게 오는 이벤트와 재처리 속에서도 정확히 한 번 반영하게 만든다.
- 1단계 `01-windows`: 1분 고정 창 집계, 광고별 상위 N, 이벤트 시각과 처리 시각의 구분.
- 2단계 `02-late-events`: 워터마크(본 최대 이벤트 시각 − 지연 한도), 한도 안의 늦은 이벤트로 결과 고치기, 한도를 넘은 이벤트 따로 세기.
- 3단계 `03-exactly-once`: 클릭 ID 로 중복 거르기, 처리 중 장애와 재시작에서 같은 결과를 내는 체크포인트, 멱등한 결과 쓰기.
- 난도: 단계마다 2~3시간.
- 함정: 창 경계 시각, 워터마크가 뒤로 가는 입력, 재시작 뒤 중복 반영.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 2권 6장의 주제, Spark Structured Streaming 문서의 watermark 절(spark README 의 '구조적 스트리밍' 절).
