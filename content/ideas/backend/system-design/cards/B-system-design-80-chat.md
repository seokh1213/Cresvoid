---
id: B-system-design-80
title: 채팅 순서와 동기화 만들기
kind: build
track: chat
stage: 01-sequencing, 02-sync, 03-presence
status: 후보
wave: 4
env: 없음
prereq: 
---

# B-system-design-80 채팅 순서와 동기화 만들기

- 목표: 채팅방 메시지의 순서, 여러 기기의 동기화, 접속 상태를 구현한다.
- 1단계 `01-sequencing`: 방마다 늘어나는 메시지 번호, 클라이언트 메시지 ID 로 재전송 중복 거르기, 순서가 뒤바뀌어 도착한 메시지.
- 2단계 `02-sync`: 기기별 읽은 위치, 안 읽은 개수, 오프라인 기기의 따라잡기.
- 3단계 `03-presence`: 하트비트와 시간 초과로 접속 판정, 상태 변화 알림의 묶음 전송.
- 난도: 단계마다 2시간 안팎.
- 함정: 같은 메시지의 재전송과 수정, 기기가 둘일 때 안 읽은 개수, 하트비트 경계 시각.
- 검증: build 스킬의 교차 검증.
- 출처: System Design Interview 1권 12장의 주제.
