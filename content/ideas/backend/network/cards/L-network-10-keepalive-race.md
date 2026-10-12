---
id: L-network-10
title: keep-alive 수명의 경합
kind: lab
track: net-lab
stage: keepalive-race
status: 후보
wave: 4
env: 없음
prereq: 
---

# L-network-10 keep-alive 수명의 경합

- 모드: observe 뒤 implement
- 실행 방식: 놀고 있는 연결을 5초 뒤 닫는 서버와 연결을 재사용하는 클라이언트를 JDK 로 만들고 요청 간격을 바꿔 가며 호출한다.
- 목표: 서버가 닫은 연결을 재사용할 때 나는 오류와 그 시점을 예측하고 클라이언트 쪽 연결 수명과 재시도 규칙으로 막는다.
- 불변식: 요청 간격이 서버 수명 근처일 때도 실패 0건. 재시도는 멱등한 요청에만 한다.
- 출처: RFC 9112 의 연결 관리 절, java.net.http.HttpClient 문서.
