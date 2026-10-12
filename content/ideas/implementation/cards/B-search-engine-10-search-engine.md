---
id: B-search-engine-10
title: 나만의 검색 엔진
kind: build
track: search-engine
stage: 01-boolean, 02-ranking, 03-phrase
status: 완료(2026-10-07)
wave: 1
env: 없음
prereq: 
---

# B-search-engine-10 나만의 검색 엔진

- 목표: 역색인과 불리언 질의, BM25 랭킹, 구문·근접 검색을 표준 입출력 명령 처리기로 만든다.
- 1단계: 토큰화, 역색인, AND·OR·NOT 과 괄호 질의, 암묵적 AND.
- 2단계: SEARCH 명령, 필수·제외·선택 낱말, BM25 점수와 반올림 규칙.
- 3단계: 위치 색인, 구문 검색, NEAR/k 근접 검색.
- 후속 카드: B-search-engine-20(오타 허용 검색), B-search-engine-30(자동 완성), B-search-engine-40(검색 결과 발췌).
