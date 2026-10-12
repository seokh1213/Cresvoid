---
id: L-search-52
title: nori 분석기와 상품명
kind: lab
track: es-lab
stage: nori-analysis
status: 후보
wave: 4
env: docker
prereq: R-search-10
---

# L-search-52 nori 분석기와 상품명

- 모드: observe 뒤 tune
- 실행 방식: Docker 의 Elasticsearch 에 analysis-nori 플러그인을 넣고 `_analyze` 와 검색으로 상품명 토큰을 확인한다.
- 목표: decompound_mode, 사용자 사전, 품사 필터가 상품명 검색의 재현율과 정밀도에 주는 영향을 예측하고 설정을 고른다.
- 예측 문항: 복합 명사 상품명의 토큰(none, discard, mixed), 모델명과 숫자가 섞인 상품명, 사용자 사전에 넣은 뒤의 변화.
- tune 목표: 주어진 검색어 묶음의 기대 결과를 모두 맞추는 분석기 설정.
- 출처: Elastic 문서의 nori_tokenizer.
