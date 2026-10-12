---
id: L-network-20
title: 타임아웃의 층
kind: lab
track: net-lab
stage: timeout-layers
status: 후보
wave: 5
env: 없음
prereq: 
---

# L-network-20 타임아웃의 층

- 모드: implement
- 실행 방식: 지연과 연결 끊김을 넣는 Kotlin TCP 프록시 뒤에 하위 서비스를 둔다.
- 목표: 연결, 응답 대기, 요청 전체 타임아웃을 각각 넣고 어느 장애에 어느 타임아웃이 작동하는지 확인한다.
- 불변식: 모든 장애 시나리오에서 요청이 정해진 시간 안에 끝난다. 스레드 수가 기준 이하로 유지된다.
- 출처: java.net.http.HttpClient 문서, Amazon Builders' Library 의 Timeouts, retries, and backoff with jitter.
