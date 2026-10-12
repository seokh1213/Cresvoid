---
id: C-kotlin-80
title: 상품 일괄 등록
kind: code
track: code-reading
stage: csv-import
status: 후보
wave: 5
env: 없음
prereq: 
---

# C-kotlin-80 상품 일괄 등록

- 도메인: 판매자가 올린 CSV 로 상품을 일괄 등록하는 처리기. 표준 입력으로 CSV 텍스트를 받아 행마다 검증하고 성공한 행과 실패한 행, 실패 이유를 출력한다. 첫 줄 앞에 UTF-8 BOM 이 올 수 있다. CSV 파싱, 머리글 해석, 행 검증, 출력으로 파일을 나눈다.
- 겨냥: 큰따옴표 안의 쉼표와 줄바꿈, 큰따옴표 두 개로 쓴 이스케이프, Kotlin 의 `split(",")` 은 끝의 빈 문자열을 남긴다(Java 의 `String.split` 은 지운다), BOM(`\uFEFF`)이 첫 열 이름 앞에 남고 `trim()` 으로 지워지지 않는다, Kotlin `trim()` 은 NBSP(`\u00A0`)를 지우지만 Java `trim()` 은 지우지 않는다, `"1,000".toIntOrNull()` 은 null 이다, CRLF 줄 끝.
- 형식: 280~380줄, 파일 4~5개, 6문항(실행 결과 예측 short 3, choice 3), 목표 30분.
- 난도: 문자 단위로 따라가야 하는 문항이 있다. 보이지 않는 문자는 guide.md 에 코드값으로 밝힌다.
- 검증: 실행으로 확인한다. BOM 과 공백 문자 동작은 짧은 스크립트로 따로 확인한다.
- 출처: RFC 4180(CSV 형식). Kotlin 표준 라이브러리 문서의 `split`, `trim`, `toIntOrNull`. Java SE 21 API 문서의 `String.split`, `String.trim`.
- 피할 것: 특정 인코딩 표를 외워야 풀리는 문항.
