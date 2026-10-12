---
id: L-spring-30
title: 트랜잭션 프록시의 함정
kind: lab
track: spring-lab
stage: proxy-pitfalls
status: 후보
wave: 4
env: maven
prereq: 
---

# L-spring-30 트랜잭션 프록시의 함정

- 모드: observe
- 실행 방식: Kotlin 스크립트. spring-context, spring-tx, spring-jdbc, H2.
- 목표: 선언적 트랜잭션이 적용되지 않거나 예상과 다르게 끝나는 경우를 예측한다.
- 실험 묶음: self-invocation, private 메서드, final(Kotlin 기본) 클래스, checked 예외의 커밋, 안쪽 REQUIRED 의 롤백 전용 표시와 `UnexpectedRollbackException`, NESTED 세이브포인트.
- 예측 문항: 실험마다 DB 에 남는 행과 던져지는 예외를 묻는다.
- 관측: 실험마다 실제 행 수와 예외를 출력한다.
