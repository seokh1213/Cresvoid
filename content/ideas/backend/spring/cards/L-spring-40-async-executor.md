---
id: L-spring-40
title: @Async 실행기와 큐
kind: lab
track: spring-lab
stage: async-executor
status: 후보
wave: 4
env: maven
prereq: 
---

# L-spring-40 @Async 실행기와 큐

- 모드: observe 뒤 tune
- 실행 방식: Kotlin 스크립트(spring-context) 또는 Spring Boot.
- 목표: core·max·queue 의 관계 때문에 max 를 키워도 스레드가 늘지 않는 이유를 설명하고 설정을 고친다.
- 실험: 1초 걸리는 작업 100개를 제출하고 동시에 실행되는 수와 끝나는 시각을 기록한다. 큐 무한, 큐 0, 큐 유한 세 설정을 비교한다.
- tune 목표: 동시 실행 상한과 거절 정책을 정해 제출 폭주에서도 메모리와 지연을 지킨다.
