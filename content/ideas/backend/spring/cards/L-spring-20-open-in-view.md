---
id: L-spring-20
title: OSIV 와 느린 외부 호출
kind: lab
track: spring-lab
stage: open-in-view
status: 후보
wave: 4
env: gradle, maven, k6
prereq: 
---

# L-spring-20 OSIV 와 느린 외부 호출

- 모드: tune
- 실행 방식: Gradle 프로젝트. Spring Boot web, Data JPA, H2, k6.
- 목표: 요청이 끝날 때까지 커넥션을 점유하는 기본 설정이 부하에서 풀을 고갈시키는 과정을 관찰하고 고친다.
- 실험: 컨트롤러가 엔티티를 읽은 뒤 300ms 걸리는 가짜 외부 API 를 호출한다. 풀 크기 10. constant-arrival-rate 를 올리며 p95 와 풀 대기를 측정한다.
- tune 목표: 같은 풀 크기로 목표 도착률에서 p95 기준을 맞춘다. `editable` 은 application.yml 과 서비스 계층 파일이다.
- 예측 문항: OSIV 가 켜져 있을 때 커넥션을 반납하는 시점, 풀 크기를 키우는 해결의 한계, 꺼질 때 생기는 LazyInitializationException 을 피하는 방법.
