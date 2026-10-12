---
id: R-observability-10
title: 백분위수와 오류 예산
kind: reading
track: tech-reading
stage: 12-percentiles-and-slo
status: 완료(2026-10-10)
wave: 4
env: 없음
prereq: 
---

# R-observability-10 백분위수와 오류 예산

- 분야: 기술(운영)
- 목표: 지연 분포와 오류 예산을 숫자로 다루고 경보 규칙의 장단점을 판단한다.
- 다룰 내용: 평균과 백분위수, 히스토그램 버킷과 선형 보간 오차, 요약을 합칠 수 없는 이유, SLO 와 오류 예산, 소진율, 긴 창과 짧은 창을 함께 쓰는 경보, 닫힌 부하 모델이 꼬리 지연을 가리는 원리.
- 난도: 수능 기술 지문 최고난도.
- 형식: 2,800~3,200자, 7~8문단, 6문항. `<보기>` 하나는 버킷별 누적 개수를 주고 95번째 백분위수 추정값을 계산하게 한다. 다른 하나는 시간대별 오류율을 주고 어느 경보가 언제 켜지고 꺼지는지 묻는다.
- 함정과 엣지 케이스: 인스턴스별 p99 의 평균을 전체 p99 로 본 선지, 버킷 경계 밖의 값, 긴 창만 쓴 경보가 늦게 꺼지는 이유.
- 검증: `<보기>` 계산은 Kotlin 스크립트로 확인한다.
- 출처: Prometheus Histograms and summaries, Google SRE Workbook Alerting on SLOs.
- 피할 것: PromQL 문법을 외워야 풀리는 문항.
