---
id: R-technology-20
title: TCP 혼잡 제어
kind: reading
track: reading
stage: congestion-control
status: 후보
wave: 5
env: 없음
prereq: 
---

# R-technology-20 TCP 혼잡 제어

- 분야: 기술(네트워크)
- 목표: 상태 변화 규칙을 시간 순서로 추적한다.
- 다룰 내용: 혼잡과 흐름 제어의 차이, 혼잡 창(cwnd)과 임계값(ssthresh), 느린 시작(왕복마다 창이 두 배), 혼잡 회피(왕복마다 한 세그먼트씩 증가), 중복 확인 응답 세 번에 따른 빠른 재전송과 빠른 회복, 재전송 타이머 만료 때의 처리, 덧셈 증가 곱셈 감소가 공정성을 낳는 원리.
- 난도: 수능 기술 지문 최고난도.
- 형식: 2,800~3,200자, 7~8문단, 6문항. `<보기>` 는 왕복 시간마다의 사건(손실, 중복 확인 응답, 타이머 만료)을 주고 cwnd 를 추적하게 한다.
- 함정과 엣지 케이스: 타이머 만료와 중복 확인 응답의 처리 차이, ssthresh 가 바뀌는 시점.
- 검증: `<보기>` 의 cwnd 추적은 Kotlin 스크립트로 시뮬레이션해 확인한다.
- 출처: RFC 5681(TCP Congestion Control).
- 피할 것: CUBIC, BBR 같은 변형을 섞는 것.
