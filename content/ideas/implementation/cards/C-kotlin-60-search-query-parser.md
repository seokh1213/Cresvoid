---
id: C-kotlin-60
title: 검색어 파서
kind: code
track: code-reading
stage: search-query-parser
status: 후보
wave: 5
env: 없음
prereq: 
---

# C-kotlin-60 검색어 파서

- 도메인: 쇼핑 검색창의 고급 검색어를 해석하는 파서. 낱말, 큰따옴표 구문, AND·OR·NOT, 괄호, 가격 범위(`price:1000..5000`)를 받아 구문 트리와 정규화한 검색어를 출력한다. 토크나이저, 재귀 하강 파서, 구문 트리 출력, 명령 처리로 파일을 나눈다.
- 겨냥: 연산자 우선순위(NOT, AND, OR 순)와 왼쪽 결합, 낱말 사이의 암묵적 AND, 문자열 인덱스 경계(`substring` 의 끝 제외, `indexOf` 의 -1, 닫는 따옴표가 없는 입력), `split(Regex("\\s+"))` 는 앞 공백에서 빈 문자열을 만든다, `Char.isLetterOrDigit` 은 한글 음절에 true 다, 범위의 한쪽 끝이 빈 입력(`price:..5000`).
- 형식: 250~350줄, 파일 3~4개, 6문항(실행 결과 예측 short 3, choice 3), 목표 30분.
- 난도: 재귀 호출 깊이를 따라 토큰 위치를 추적해야 풀린다.
- 검증: 실행으로 확인한다. 문항에 쓴 입력마다 구문 트리 출력을 실행 결과로 싣는다.
- 출처: Kotlin 표준 라이브러리 문서의 `substring`, `indexOf`, `split`, `Char.isLetterOrDigit`. 질의 문법의 배경은 B-search-engine-10 의 search-engine 1단계다.
- 피할 것: 파서 생성기 수준의 문법 이론을 묻는 것. B-search-engine-10 의 질의 규칙을 그대로 옮기는 것.
