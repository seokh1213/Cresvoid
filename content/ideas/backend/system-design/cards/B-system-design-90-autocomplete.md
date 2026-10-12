---
id: B-system-design-90
title: 검색어 자동완성 만들기
kind: build
track: autocomplete
stage: 01-trie, 02-cached-top-k, 03-filters-and-shards
status: 후보
wave: 4
env: 없음
prereq: 
---

# B-system-design-90 검색어 자동완성 만들기

- 목표: 접두어로 많이 찾은 검색어 상위 k 개를 빠르게 반환하는 구조를 만든다.
- 1단계 `01-trie`: 트라이, 빈도, 동점 규칙, 접두어 질의.
- 2단계 `02-cached-top-k`: 노드마다 상위 k 개를 미리 저장, 빈도 갱신을 묶어 다시 만드는 방식과 바로 고치는 방식의 비용 비교.
- 3단계 `03-filters-and-shards`: 금지어 거르기, 첫 글자 분포를 고려한 샤드 나누기.
- 난도: 단계마다 2시간 안팎. 대규모 테스트는 검색어 수백만 개다.
- 함정: 한글 음절과 자모 입력, 동점 순서, 상위 k 에서 빠진 항목의 빈도가 다시 오른 경우.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 1권 13장의 주제.
