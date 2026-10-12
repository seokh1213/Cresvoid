---
id: L-search-53
title: terms 집계의 오차
kind: lab
track: es-lab
stage: terms-agg-accuracy
status: 후보
wave: 5
env: docker
prereq: B-search-20
---

# L-search-53 terms 집계의 오차

- 모드: observe
- 실행 방식: 주 샤드 5개 색인에 브랜드별 상품 수가 샤드마다 다르게 쏠린 데이터를 넣는다.
- 목표: size 와 shard_size 에 따라 상위 브랜드 순위와 개수가 달라지는 것을 예측하고 오차 상한 값을 해석한다.
- 예측 문항: size 10, shard_size 기본값에서 빠지는 브랜드, `doc_count_error_upper_bound` 값, shard_size 를 늘렸을 때의 변화.
- 출처: Elastic 문서의 Terms aggregation.
