---
id: B-distributed-60
title: 가상 시간의 lease 잠금 만들기
kind: build
track: lease-lock
stage: 01-acquisition, 02-expiry
status: 완료(2026-11-06)
wave: 4
env: 없음
prereq:
---

# B-distributed-60 가상 시간의 lease 잠금 만들기

- 목표: 명세가 보장하는 원자적 저장 연산 위에서 잠금의 획득·해제·갱신·만료를 구현한다. 클라이언트 이름과 획득 한 번의 식별자를 구분하고 사건 순서가 달라도 현재 소유자만 잠금을 바꾸게 한다.
- 입출력 모양: `ACQUIRE <key> <client> <ttl>`, `RELEASE <key> <acquisition_id>`, `STATUS <key>` 명령을 받는다. 획득 성공 시 단조 증가하는 획득 번호와 만료 시각을 처음부터 출력한다. 실제 시각을 읽지 않고 입력 명령의 시각만 쓴다.
- 1단계 `01-acquisition`: 고정된 시각에서 획득·조회·해제를 구현한다. 저장소의 조건부 생성과 획득 번호를 비교한 조건부 삭제를 각각 원자적 연산 하나로 정의한다. 같은 클라이언트가 해제 후 다시 얻어도 새 획득 번호를 받는다.
- 2단계 `02-expiry`: `ADVANCE <time>`과 `RENEW <key> <acquisition_id> <ttl>`을 더한다. 만료 시각과 현재 시각이 같으면 만료된 것으로 정하고, 만료한 획득은 갱신할 수 없다. 만료 후 새 획득과 늦은 해제·갱신이 섞여도 새 소유권을 보존한다. 앞 단계 명령의 입출력은 유지한다.
- 난도: 단계마다 1~3시간. 실제 병렬 스레드 대신 공개된 저장 연산과 사건 순서를 추적한다. 만료 후보를 효율적으로 찾는 자료구조가 필요한 대규모 입력을 둔다.
- 함정: 클라이언트 이름만 비교하는 해제, 만료 경계의 포함 여부, 오래된 만료 예약이 갱신된 잠금을 지우는 경우, 획득 번호 재사용, 시각 역행과 0 이하 TTL의 처리. 수치 범위와 오류 시 상태 보존은 스펙에서 고정한다.
- 검증: build 스킬의 독립 테스트·참조 풀이 교차 검증을 따른다. 두 클라이언트의 경쟁과 동일 클라이언트 재획득, 만료 직전·정각·직후, 갱신 뒤 남은 옛 만료 사건을 고정 테스트로 확인한다. 각 단계는 고정 10~14개와 생성 2~3개를 둔다.
- 출처: [etcd 3.6 concurrency API](https://etcd.io/docs/v3.6/dev-guide/api_concurrency_reference_v3/)의 lease와 소유권, [Chubby 논문](https://research.google/pubs/the-chubby-lock-service-for-loosely-coupled-distributed-systems/)의 잠금 세대. 명령·원자적 연산·가상 시간은 과제에서 정한 모형이며 실제 제품 API를 복제하지 않는다.
- 피할 것: 실제 HDFS·etcd·Redis 실행, 특정 파일시스템의 rename 동작을 모든 저장소에 일반화하는 것. 잠금 만료만으로 저장소의 늦은 쓰기까지 차단한다고 설명하지 않는다. 저장소 검사는 B-distributed-80에서 별도로 구현한다.
