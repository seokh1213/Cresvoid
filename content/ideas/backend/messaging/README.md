# 메시징: Kafka

Kafka 의 파티션, 복제, 프로듀서와 컨슈머 규약을 익힌다. 메시지가 어떤 조건에서 사라지고 어떤 조건에서 두 번 오는지, 순서가 어디까지 보장되는지 설명하고 설정과 처리 코드로 원하는 보장을 만드는 힘을 기른다.

## 1. 개념 지도

기본값은 2026-10-08 에 Kafka 4.1 문서로 확인한 값이다.

### 1.1 파티션과 순서

- 순서는 파티션 안에서만 보장된다. 키가 같은 메시지는 같은 파티션으로 간다. 파티션 수를 늘리면 키와 파티션의 대응이 바뀌어 키별 순서가 끊길 수 있다.
- 메시지는 보관 기간(`retention.ms` 7일, `cleanup.policy` delete) 동안 남는다. 세그먼트 크기는 1GiB(`segment.bytes`)다.

### 1.2 프로듀서

- 기본값: `acks=all`, `enable.idempotence=true`(3.0 부터), `linger.ms=5`(4.0 부터, KIP-1030).
- 멱등 프로듀서는 프로듀서 ID 와 시퀀스 번호로 재시도 때문에 생긴 중복을 브로커가 걸러 내게 한다.
- `min.insync.replicas` 기본값은 1이다. 그래서 `acks=all` 이어도 동기 복제본 집합(ISR)이 리더 하나뿐이면 리더만 받고 성공한다. ISR 이 `min.insync.replicas` 보다 작으면 `acks=all` 쓰기는 NotEnoughReplicas 로 실패한다.
- `unclean.leader.election.enable` 기본값은 false 다. 동기화되지 않은 복제본은 리더가 되지 못한다.

### 1.3 컨슈머

- 기본값: `enable.auto.commit=true`, `auto.commit.interval.ms=5000`, `max.poll.records=500`, `max.poll.interval.ms=300000`, `session.timeout.ms=45000`, `heartbeat.interval.ms=3000`, `auto.offset.reset=latest`, `isolation.level=read_uncommitted`, `group.protocol=classic`.
- poll 사이 간격이 `max.poll.interval.ms` 를 넘으면 컨슈머는 실패로 보고 그룹에서 빠지고 재조정이 일어난다. 처리 중이던 메시지는 커밋 전이면 다른 컨슈머가 다시 받는다.
- 처리 뒤 커밋하면 최소 한 번, 처리 전 커밋하면 최대 한 번이다. 자동 커밋은 처리가 끝났는지와 상관없이 주기마다 커밋한다.
- 새 컨슈머 재조정 규약(KIP-848)은 4.0 부터 GA 다. `group.protocol=consumer` 로 켜고 조정을 브로커가 맡아 점진적으로 한다.

### 1.4 압축과 정확히 한 번

- 압축 토픽은 키마다 최신 값을 남긴다. 값이 null 인 툼스톤은 `delete.retention.ms`(1일) 동안 남았다가 지워진다. 더러운 비율이 0.5(`min.cleanable.dirty.ratio`)를 넘으면 압축 대상이 된다.
- 트랜잭션과 `read_committed` 는 Kafka 안의 읽기-처리-쓰기를 정확히 한 번으로 만든다. DB 쓰기나 외부 API 호출 같은 바깥 부수효과는 따로 멱등하게 만들어야 한다.
- DB 커밋과 메시지 발행을 따로 하면 한쪽만 성공한다. 아웃박스 테이블에 같은 트랜잭션으로 쓰고 따로 발행한다. 부하 엣지 케이스의 L-load-100, L-load-110 카드가 이어진다.

## 2. 학습 경로

1. R-messaging-10 기술 지문으로 복제, ISR, 확인 응답, 커밋 시점을 익힌다.
2. B-messaging-20 `mini-log` 트랙에서 파티션 로그, 멱등 프로듀서, 컨슈머 그룹, 압축을 시뮬레이터로 만든다.
3. C-messaging-30 코드 읽기로 커밋 시점에 따른 중복과 유실을 예측한다.
4. L-messaging-40~L-messaging-41 실험은 Docker 의 Kafka 로 재조정과 내구성을 확인한다.

## 3. 환경

- reading, build, code 카드는 추가 도구가 필요 없다.
- lab 카드는 Docker 로 KRaft 모드 Kafka 를 실행한다. 내구성 실험은 브로커 3개가 필요하다.

## 4. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 스키마 진화와 호환성 | reading | 필드 추가·삭제와 하위·상위 호환 |
| 독 메시지와 재시도 토픽 | lab | 재시도 한도, DLT, 순서와 재시도의 충돌 |
| CDC 와 아웃박스 | lab | DB 로그를 읽어 발행하는 방식과 폴링 방식 |
| 컨슈머 지연 감시 | lab | 지연 지표와 처리량, 파티션 수의 관계 |
| 재조정 비용 줄이기 | lab | 배포로 컨슈머를 차례로 다시 시작할 때 static membership(`group.instance.id`)과 cooperative 재조정을 쓴 경우와 쓰지 않은 경우의 재조정 횟수, 옮겨지는 파티션 수, 처리가 멈춘 시간. classic 규약의 기본 assignor 목록은 eager 인 Range 가 먼저라서 cooperative 가 저절로 쓰이지 않는다 |
| fetch 설정과 poll 크기 | lab | `fetch.min.bytes`·`fetch.max.wait.ms` 는 브로커가 응답을 모으는 기준이고 `max.poll.records` 는 이미 받은 레코드를 poll 한 번에 넘기는 수라는 차이. 처리량과 지연의 교환 |
| poll 묶음 안의 변경 합치기 | build | 같은 키의 변경이 한 묶음에 여러 번 올 때 마지막 값만 쓰기, CDC 변경의 첫 before 와 마지막 after 합치기, 생성 뒤 삭제처럼 합치면 없어지는 변경, 합친 뒤의 오프셋 커밋. B-coroutines-35 5단계의 묶음과 이어진다 |
| 보관 기간과 지워진 오프셋 | lab | 보관 기간보다 길어진 지연. 세그먼트가 지워져 커밋한 오프셋이 로그 시작 오프셋보다 작아지면 `auto.offset.reset` 기본값 latest 가 남은 메시지까지 건너뛴다. 지연을 줄이려고 컨슈머를 늘리기 전에 보관 기간까지 남은 시간을 확인한다 |

## 5. 출처

- Kafka 4.1 Consumer Configs: https://kafka.apache.org/41/configuration/consumer-configs/
- Kafka 4.1 Topic Configs: https://kafka.apache.org/41/configuration/topic-configs/
- Kafka 4.1 Consumer Rebalance Protocol: https://kafka.apache.org/41/operations/consumer-rebalance-protocol/
- KIP-1030(linger.ms 기본값 변경), KIP-848(새 컨슈머 재조정 규약).
- Kafka 문서의 Message Delivery Semantics 절.
