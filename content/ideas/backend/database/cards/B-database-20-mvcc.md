---
id: B-database-20
title: 트랜잭션 격리 엔진 만들기
kind: build
track: mvcc
stage: 01-snapshot, 02-locking, 03-serializable
status: 완료(2026-10-10)
wave: 3
env: 없음
prereq: R-database-10
---

# B-database-20 트랜잭션 격리 엔진 만들기

- 목표: 키-값 저장소 위에 스냅숏 격리, 잠금과 교착 감지, 직렬화 실패 감지를 구현한다.
- 입출력 모양: `BEGIN <트랜잭션> [격리 수준]`, `READ`, `READ FOR UPDATE`, `WRITE`, `SCAN <범위>`, `COMMIT`, `ROLLBACK` 명령이 한 줄씩 들어온다. 막힌 트랜잭션의 명령은 대기열에 두고 풀리면 이어 실행한다. 명령마다 결과나 대기, 실패를 출력한다.
- 1단계 `01-snapshot`: 버전 체인, 트랜잭션 시작 시점 스냅숏과 문장 시점 스냅숏, 먼저 커밋한 쪽이 이기는 쓰기 충돌 판정.
- 2단계 `02-locking`: 공유·배타 잠금, 범위 잠금으로 막는 삽입, 대기 그래프로 교착 감지, 희생자를 고르는 규칙(명세가 정함).
- 3단계 `03-serializable`: 읽기-쓰기 의존을 기록해 연속 두 개의 위험한 의존을 찾으면 한 트랜잭션을 실패시키는 단순화한 SSI. 쓰기 비틀림 사례가 실패로 바뀐다.
- 난도: 단계마다 2~3시간.
- 함정: 자기가 쓴 값 읽기, 롤백한 버전의 정리, 대기 중인 트랜잭션의 롤백, 범위 잠금 경계, 실패시킬 트랜잭션 선택의 결정성.
- 검증: build 스킬의 교차 검증. 고전 사례(갱신 손실, 쓰기 비틀림, 팬텀)를 고정 테스트로 둔다.
- 출처: PostgreSQL Transaction Isolation 문서, Berenson 외 SIGMOD 1995, Cahill 외 "Serializable Isolation for Snapshot Databases" SIGMOD 2008.
- 피할 것: 특정 엔진의 내부 구현을 그대로 요구하는 것. 스펙이 단순화한 규칙을 밝힌다.
