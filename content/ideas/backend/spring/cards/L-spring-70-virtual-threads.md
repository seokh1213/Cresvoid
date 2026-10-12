---
id: L-spring-70
title: 가상 스레드와 풀
kind: lab
track: spring-lab
stage: virtual-threads
status: 후보
wave: 5
env: gradle, maven, k6
prereq: 
---

# L-spring-70 가상 스레드와 풀

- 모드: tune
- 실행 방식: Gradle 프로젝트. Spring Boot 3.2 이상, JDK 21, k6.
- 목표: 가상 스레드를 켜면 병목이 스레드에서 커넥션 풀과 하위 서비스로 옮겨 가는 것을 관찰하고 동시 실행 수를 제한한다.
- 예측 문항: 가상 스레드 1,000개와 풀 10개에서 대기는 어디에 쌓이는가, JDK 21 에서 `synchronized` 블록 안의 블로킹이 왜 문제인가.
