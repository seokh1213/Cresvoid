---
id: L-spring-50
title: HTTP 클라이언트 커넥션 풀
kind: lab
track: spring-lab
stage: http-client-pool
status: 후보
wave: 4
env: gradle, maven, k6
prereq: 
---

# L-spring-50 HTTP 클라이언트 커넥션 풀

- 모드: tune
- 실행 방식: Gradle 프로젝트. Spring Boot web, Apache HttpClient 5, 가짜 하위 서비스, k6.
- 목표: 경로당 커넥션 한도, 풀 대기 타임아웃, 응답 본문 반납이 처리량과 지연에 주는 영향을 체험한다.
- 실험: 하위 서비스 응답 100ms. 기본 풀 설정에서 도착률을 올리면 처리량이 약 50/s 에서 막힌다(5 커넥션 ÷ 0.1초). 본문을 닫지 않는 경로가 하나 섞여 있다.
- tune 목표: 목표 도착률에서 p95 기준을 맞추고 커넥션 누수가 없게 한다.
