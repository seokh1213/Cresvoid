---
id: B-kv-store-10
title: 키-값 저장소
kind: build
track: kv-store
stage: 01-basic, 02-log, 03-transactions
status: 후보
wave: 4
env: 없음
prereq: 
---

# B-kv-store-10 키-값 저장소

- 목표: 메모리 키-값 저장소를 만들고 로그로 재시작을 견디게 하고 낙관적 트랜잭션을 더한다.
- 1단계: `SET`, `GET`, `DEL`, `EXPIRE`, `TTL`, 입력 명령으로 움직이는 시계(`TICK <ms>`), 만료의 수동·능동 처리. 값을 덮어쓰면 TTL 이 지워지는 규칙처럼 Redis 와 같은 의미를 명세한다.
- 2단계: 명령을 로그 파일에 덧붙이고 `RESTART` 명령에서 로그로 상태를 되살린다. 마지막 줄이 잘린 로그, 로그 압축(스냅숏과 로그 교체)을 다룬다. 파일은 작업 디렉터리 아래 지정한 경로에만 쓴다.
- 3단계: `WATCH`, `MULTI`, `EXEC`, `DISCARD`. 감시한 키가 바뀌면 `EXEC` 가 실패한다.
- 난도: 단계마다 2시간 안팎.
- 함정: 만료된 키의 `WATCH`, 로그 재생 중 만료 시각 계산, 압축 도중의 재시작.
