---
id: L-jvm-20
title: 스레드 풀과 ThreadLocal
kind: lab
track: jvm-lab
stage: threadlocal-leak
status: 후보
wave: 4
env: 없음
prereq: 
---

# L-jvm-20 스레드 풀과 ThreadLocal

- 모드: implement
- 실행 방식: 요청마다 사용자 정보를 ThreadLocal 에 넣는 작은 서버를 스레드 풀에서 실행한다.
- 목표: 지우지 않은 ThreadLocal 값이 다른 요청에 새는 경로를 찾아 고친다.
- 불변식: 요청 10만 개 동안 다른 사용자의 값이 보인 응답 0건, 메모리 사용량 기준 이하.
- 출처: java.lang.ThreadLocal API 문서.
