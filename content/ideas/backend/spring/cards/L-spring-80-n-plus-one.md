---
id: L-spring-80
title: N+1 과 페이징
kind: lab
track: spring-lab
stage: n-plus-one
status: 후보
wave: 5
env: gradle, maven, k6
prereq: 
---

# L-spring-80 N+1 과 페이징

- 모드: tune
- 실행 방식: Gradle 프로젝트. Spring Boot, Data JPA, H2, k6. Hibernate 통계로 쿼리 수를 센다.
- 목표: 목록 API 의 쿼리 수를 상수로 줄이고 컬렉션 fetch join 과 페이징의 함정을 피한다.
- 불변식: 요청당 쿼리 수 ≤ 3, 메모리 페이징 경고 없음.
