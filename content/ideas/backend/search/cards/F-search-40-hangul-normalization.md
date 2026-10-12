---
id: F-search-40
title: 한글 정규화가 어긋난 상품 검색
kind: fix
track: bug-fix
stage: hangul-normalization
status: 후보
wave: 4
env: 없음
prereq: 
---

# F-search-40 한글 정규화가 어긋난 상품 검색

- 맥락: 판매자가 올린 상품명을 색인하고 검색어로 찾는 명령 처리기. 일부 상품명은 한글이 자모로 나뉜 형태(유니코드 NFD)로 들어온다.
- 버그 후보: 색인 경로와 검색 경로 가운데 한쪽에만 NFC 정규화가 있음, 대소문자 바꾸기를 정규화보다 먼저 해서 일부 문자가 어긋남, 보이지 않는 문자(폭 없는 공백) 처리 누락.
- 형식: 200~300줄, 파일 3~4개. 버그 신고에는 "검색하면 안 나오는데 목록에는 보이는 상품" 증상과 재현 입력을 적는다.
- 검증: 버그 신고의 재현 입력은 starter 와 solution 으로 실행해 확인한다. 자모 분리 문자열은 `java.text.Normalizer` 로 만들어 테스트 입력에 넣는다.
- 출처: Unicode Standard Annex #15(Unicode Normalization Forms), Java `java.text.Normalizer` 문서.
- 피할 것: 화면에서 구분되지 않는 문자열을 문항 설명 없이 내놓는 것. 스펙에 코드 포인트 표기를 함께 준다.
