---
id: L-spring-60
title: maxLifetime 과 끊긴 연결
kind: lab
track: spring-lab
stage: max-lifetime
status: 후보
wave: 5
env: maven
prereq: 
---

# L-spring-60 maxLifetime 과 끊긴 연결

- 모드: observe 뒤 tune
- 실행 방식: Kotlin 스크립트. HikariCP, H2 서버 모드, 놀고 있는 연결을 끊는 Kotlin TCP 프록시.
- 목표: 네트워크 장비가 연결을 끊는 시간보다 풀의 연결 수명이 길 때 생기는 오류를 재현하고 `maxLifetime` 과 `keepaliveTime` 으로 막는다.
- 실험: 프록시는 20초 놀고 있는 연결을 끊는다. 부하를 주다 30초 쉬고 다시 준다.
- 예측 문항: 쉬고 난 뒤 첫 요청들에 무슨 일이 생기는가, `maxLifetime` 을 얼마로 두어야 하는가.
