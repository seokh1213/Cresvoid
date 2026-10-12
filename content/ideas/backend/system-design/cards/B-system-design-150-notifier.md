---
id: B-system-design-150
title: 알림 발송기 만들기
kind: build
track: notifier
stage: 01-dispatch, 02-reliability, 03-throttling
status: 후보
wave: 5
env: 없음
prereq: 
---

# B-system-design-150 알림 발송기 만들기

- 목표: 이벤트를 받아 사용자 설정에 맞는 채널로 알림을 보내고 실패와 중복과 폭주를 견디게 한다.
- 1단계 `01-dispatch`: 템플릿, 채널(푸시, 메일, 문자), 사용자 수신 설정.
- 2단계 `02-reliability`: 가짜 발송 서버의 실패와 재시도, 이벤트 ID 로 중복 발송 막기, 발송 기록.
- 3단계 `03-throttling`: 사용자별 발송 한도, 야간 금지 시간(사용자 시간대), 우선순위.
- 난도: 단계마다 2시간 안팎.
- 함정: 재시도 중 들어온 같은 이벤트, 시간대가 다른 사용자의 금지 시간 경계, 한도를 넘은 알림을 버릴지 미룰지.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 1권 10장의 주제.
