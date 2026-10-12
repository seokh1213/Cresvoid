---
id: B-observability-20
title: 지표 엔진 만들기
kind: build
track: metrics
stage: 01-counters, 02-histograms, 03-slo-alerts
status: 완료(2026-10-11)
wave: 5
env: 없음
prereq: R-observability-10
---

# B-observability-20 지표 엔진 만들기

- 목표: 시계열 지표를 저장하고 범위 질의, 분위수 추정, 소진율 경보를 계산하는 엔진을 만든다.
- 1단계 `01-counters`: 카운터와 게이지 표본 적재, 범위 안 증가량과 초당 비율, 카운터 재시작 보정. 범위 끝 보외 규칙은 명세가 단순하게 정한다.
- 2단계 `02-histograms`: 버킷 누적 개수의 합치기, 버킷 안 선형 보간으로 분위수 추정, 가장 낮은 버킷과 무한대 버킷 처리 규칙.
- 3단계 `03-slo-alerts`: SLO 와 창 설정을 받아 시각마다 소진율을 계산하고 경보가 켜지고 꺼지는 시각을 출력한다.
- 난도: 단계마다 2시간 안팎. 대규모 테스트는 시계열 수만 개다.
- 함정: 재시작 직후 표본, 빈 범위, 버킷 경계 위의 값, 창 끝의 정렬.
- 검증: build 스킬의 교차 검증. 분위수 추정 규칙은 Prometheus 문서와 대조하고 스펙이 단순화한 부분을 밝힌다.
- 출처: Prometheus 문서(Histograms and summaries, 질의 함수), Google SRE Workbook.
- 피할 것: PromQL 파서를 요구하는 것.
