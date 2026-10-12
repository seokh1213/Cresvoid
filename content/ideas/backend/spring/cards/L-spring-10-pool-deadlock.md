---
id: L-spring-10
title: REQUIRES_NEW 와 풀 교착
kind: lab
track: spring-lab
stage: pool-deadlock
status: 후보
wave: 4
env: maven
prereq: 
---

# L-spring-10 REQUIRES_NEW 와 풀 교착

- 모드: observe 뒤 tune
- 실행 방식: Kotlin 스크립트. spring-jdbc, spring-tx, HikariCP, H2.
- 목표: 한 요청이 커넥션 두 개를 쓰는 구조가 풀 크기와 만나 교착하는 과정을 예측하고 풀 크기 공식이나 구조 변경으로 푼다.
- 실험: 바깥 `@Transactional` 메서드가 안쪽 REQUIRES_NEW 메서드를 호출한다. 풀 크기 N, 동시 스레드 N 개. `connectionTimeout` 은 2초로 줄인다.
- 예측 문항: 몇 초 뒤 무슨 예외가 몇 개 나는가, 풀 크기를 N+1 로 올리면 어떻게 되는가, Tn × (Cm − 1) + 1 공식에 이 실험의 값을 넣으면 얼마인가, NESTED 로 바꾸면 어떻게 되는가.
- tune 목표: 풀 크기 상한 N 을 지키며 교착 없이 처리한다(구조 변경 필요).
- 관측: 스레드별 대기 시간, Hikari 의 활성·대기 커넥션 수(`HikariPoolMXBean`).
- 출처: HikariCP About Pool Sizing, Spring Framework 트랜잭션 전파 문서.
