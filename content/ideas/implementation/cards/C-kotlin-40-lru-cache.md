---
id: C-kotlin-40
title: 상품 상세 로컬 캐시
kind: code
track: code-reading
stage: lru-cache
status: 후보
wave: 4
env: 없음
prereq: 
---

# C-kotlin-40 상품 상세 로컬 캐시

- 도메인: 상품 상세 조회 앞에 둔 프로세스 안 캐시. 용량을 넘으면 가장 오래 쓰지 않은 항목을 버리고(LRU) 항목마다 만료 시각이 있다. 조회, 갱신, 무효화, 시각 이동 명령을 처리하고 적중과 실패, 남은 항목 순서를 출력한다. 시각은 입력 명령으로 움직인다.
- 겨냥: `LinkedHashMap(초기 용량, 부하율, accessOrder = true)` 의 접근 순서, `removeEldestEntry` 는 `put` 이 항목을 넣은 뒤에 불린다, `get` 과 `getOrDefault` 는 순서를 바꾸고 `containsKey`(Kotlin 의 `in`)는 바꾸지 않는다, 접근 순서 모드에서는 `get` 도 구조 변경이라 키를 순회하며 `get` 하면 ConcurrentModificationException 이 난다, `getOrPut` 은 `get` 을 먼저 불러 순서를 바꾼다, 만료 확인과 접근 순서 갱신의 앞뒤.
- 형식: 200~300줄, 파일 3~4개, 5문항(실행 결과 예측 short 2, choice 3), 목표 25분.
- 난도: 숙련 개발자도 `in` 과 `get` 의 순서 차이를 놓치기 쉬운 수준.
- 검증: 실행으로 확인한다. 접근 순서 동작은 Java SE 21 의 LinkedHashMap 문서 설명과 대조한다.
- 출처: Java SE 21 API 문서의 `LinkedHashMap`(접근 순서, removeEldestEntry, 구조 변경의 정의), Kotlin 표준 라이브러리 문서의 `getOrPut`.
- 피할 것: 스레드 동시성까지 섞는 것. 단일 스레드로 둔다.
