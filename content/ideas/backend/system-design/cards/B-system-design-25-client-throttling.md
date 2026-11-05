---
id: B-system-design-25
title: 호출자 쪽 적응형 쓰로틀링
kind: build
track: rate-limiter
stage: 04-client-throttle
status: 후보
wave: 4
env: 없음
prereq: B-system-design-20
---

# B-system-design-25 호출자 쪽 적응형 쓰로틀링

- 목표: 과부하인 서버를 부르는 쪽이 거절 응답을 보고 스스로 요청을 줄이는 방법을 구현하고 03단계까지의 서버 쪽 제한과 무엇이 다른지 비교한다.
- 입출력 모양: 03단계까지의 명령은 그대로 두고 호출자 명령을 더한다. 가상 시계 위에서 호출자는 최근 일정 구간의 시도 수와 서버가 받아들인 수를 세고 요청마다 로컬에서 거절할지 정해 출력한다. 거절 여부의 난수는 명세가 정한 시드로 결정한다.
- 4단계 `04-client-throttle`: 로컬 거절 확률 max(0, (requests − K × accepts) / (requests + 1)). requests는 로컬에서 거절한 요청을 포함한 시도 수이고 accepts는 서버가 받아들인 수다. K의 의미(서버가 거절을 보내는 비용을 얼마나 감수할지), 집계 구간의 미끄러짐, 서버가 준 `Retry-After` 기간의 처리, 서버 응답이 회복된 뒤 거절 확률이 줄어드는 속도를 출력으로 확인한다.
- 난도: 2시간 안팎.
- 함정: 로컬에서 거절한 요청을 requests에서 빼는 실수, 구간 경계에서 오래된 기록 지우기, 확률 계산의 정수 나눗셈, `Retry-After` 동안 들어온 요청을 셀지 말지(명세가 정한다), 시계가 뒤로 가는 입력.
- 검증: build 스킬의 교차 검증. 요청마다 구간 안 기록을 전부 다시 세는 단순 구현과 출력을 비교한다.
- 출처: Beyer 외, "Site Reliability Engineering", O'Reilly, 2016, 21장 "Handling Overload"의 클라이언트 쪽 쓰로틀링. RFC 9110의 `Retry-After`.
- 피할 것: 실제 네트워크, L-load-120(서버 쪽 적응형 동시성 제한)과 같은 지연 기반 추정, 01~03단계 출력 바꾸기.
