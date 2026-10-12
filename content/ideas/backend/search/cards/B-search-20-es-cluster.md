---
id: B-search-20
title: 분산 검색 조정 노드 만들기
kind: build
track: es-cluster
stage: 01-routing, 02-terms-agg, 03-paging
status: 완료(2026-10-10)
wave: 2
env: 없음
prereq: R-search-10
---

# B-search-20 분산 검색 조정 노드 만들기

- 목표: 샤드 여러 개에 나뉜 색인에서 조정 노드가 하는 일을 구현하고 분산 검색의 결과가 왜 근사이거나 흔들리는지 직접 확인한다.
- 입출력 모양: 색인 만들기(주 샤드 수), 문서 색인(id, 라우팅 값, 필드), 검색(낱말, 정렬, from, size, search_after), terms 집계(size, shard_size), 상태 조회 명령이다. 라우팅 해시는 명세가 정한 단순한 함수다.
- 1단계 `01-routing`: 라우팅 값으로 샤드를 정한다. 샤드마다 BM25 통계(문서 수, 문서 빈도, 평균 길이)를 따로 두고 샤드별 점수로 상위 결과를 합친다. 같은 낱말이라도 샤드에 따라 IDF 가 달라 점수가 흔들리는 것이 출력에 드러난다. 전역 통계로 점수를 내는 옵션을 더해 차이를 비교한다.
- 2단계 `02-terms-agg`: 샤드마다 상위 shard_size 개 버킷을 보내고 합친다. `doc_count_error_upper_bound`, `sum_other_doc_count` 를 Elastic 문서의 정의대로 계산한다. 정확한 집계도 함께 출력해 근사의 오차가 생기는 데이터를 보여 준다.
- 3단계 `03-paging`: from + size 를 샤드마다 from + size 개씩 받아 합치는 방식과 max_result_window 한도 오류, search_after(정렬 값과 동점 해소 필드), 페이지 사이에 문서가 색인될 때 생기는 중복과 누락, 스냅숏을 고정하는 PIT 를 구현한다.
- 난도: 단계마다 2~3시간. 3단계 대규모 테스트는 깊은 페이지를 매번 처음부터 계산하면 제한 시간을 넘게 만든다.
- 함정: 샤드별 IDF 차이, 동점 정렬, 집계에서 shard_size 가 size 보다 작을 때, 오류 상한을 샤드마다 다르게 계산하는 경우, PIT 없이 search_after 를 쓸 때의 누락.
- 검증: build 스킬의 교차 검증. 2단계 계산식은 terms 집계 문서와 대조한다.
- 출처: Elastic 문서의 Terms aggregation, Paginate search results. BM25 는 search-engine 트랙 02-ranking 스펙.
- 피할 것: Elasticsearch 의 실제 REST 응답 형식을 흉내 내는 것. 명세가 정한 줄 형식을 쓴다.
