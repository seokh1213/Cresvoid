# 긴 트랜잭션과 스키마 변경 대기

[1] 이 단계는 관찰 실험이다. 먼저 예측 문항을 풀어라. 열린 트랜잭션만 있을 때, ALTER TABLE을 요청한 뒤, 트랜잭션을 끝낸 뒤의 조회 결과를 예측해라. 그다음 시작 코드를 실험하고 지표를 기록해 예측과 비교해라. 대기열이 생기는 조건을 확인하고 코드를 계약에 맞게 수정한 뒤 다시 실험해라. 조회 속도를 높이거나 스키마 변경을 우회하는 것이 목표가 아니다.

[2] Docker의 MySQL 8.4와 JDK 21 HTTP 서비스를 사용한다. 실행 환경은 `.lab-libs`의 `jdbc-mysql` 묶음이며 코드는 `lab` 패키지에 둔다. `build.sh`는 `build/app.jar`를 만들고 `run.sh`는 PORT로 받은 포트를 루프백에 공개한다. 실험기는 고정 실행 이미지의 앱과 MySQL을 격리된 compose 프로젝트로 시작하고 준비 상태를 확인한다. 실행마다 빈 포트와 새 데이터베이스를 사용하고 끝나면 컨테이너와 볼륨을 지운다.

[3] 공개 HTTP 계약은 다음과 같다. `GET /health`는 준비가 끝나면 200과 `ready`, `GET /item`은 새 JDBC 연결로 `SELECT payload FROM items WHERE id = 1`을 실행해 200과 `7`을 반환한다. 테이블은 InnoDB이며 `items(id INT PRIMARY KEY, payload INT NOT NULL)`에 `(1, 7)` 한 행을 둔다. JDBC 연결마다 `SET SESSION lock_wait_timeout = 2`를 적용한다. MySQL 오류 번호 1205를 받으면 503과 `metadata-timeout`을 반환하고, 다른 SQL 오류는 500과 `database-error`를 반환한다. 캐시나 상수 조회로 결과를 대신하지 마라.

[4] `POST /hold`는 전용 연결에서 REPEATABLE READ, 자동 커밋 해제, 일반 SELECT 순서로 위 행을 읽고 결과 집합만 닫는다. 연결과 트랜잭션은 `/release`까지 열어 두고 200과 `held`를 반환한다. 이미 보관한 연결이 있으면 409와 `invalid-state`를 반환하고 기존 연결과 트랜잭션을 그대로 유지한다. SELECT가 끝나도 트랜잭션의 메타데이터 잠금은 유지된다. 이 읽기는 행 잠금 읽기가 아니므로 이 상태만으로 다른 연결의 일반 SELECT를 막지는 않는다.

[5] `POST /ddl`은 별도 연결에서 `lock_wait_timeout = 40`을 설정하고 `ALTER TABLE items ADD COLUMN marker INT NOT NULL DEFAULT 0, ALGORITHM=INSTANT`를 비동기로 실행하며 202와 `started`를 반환한다. `GET /state`는 `IDLE`, `RUNNING`, `DONE`, `FAILED` 중 하나를 200으로 반환한다. IDLE이 아닌 상태에서 `/ddl`을 다시 호출하면 409와 `invalid-state`를 반환한다. 이 요청은 새 ALTER를 실행하거나 기존 상태를 덮어쓰지 않으며, 진행 중인 ALTER는 원래대로 계속된다. ALTER가 성공한 뒤에만 `DONE`으로 바꿔라. INSTANT도 메타데이터 잠금이 필요하다. 이 실험의 MySQL 기본 잠금 우선순위에서는 대기 중인 배타적 잠금 뒤에 새 조회도 대기한다. 다른 테이블의 조회나 이미 실행 중인 모든 질의가 막힌다는 뜻은 아니다.

[6] `POST /release?mode=commit`은 보관한 연결에서 커밋하고, `mode=rollback`은 롤백한다. 두 방식 모두 연결을 닫고 200과 `released`를 반환한다. 이미 해제됐으면 같은 응답을 반환하고, mode가 잘못됐으면 400과 `invalid-mode`를 반환한다. 두 시나리오는 각각 커밋과 롤백으로 읽기 트랜잭션을 끝내며 나머지 관찰 순서는 같다. 커밋과 롤백 모두 메타데이터 잠금을 해제하므로 ALTER와 후속 조회가 진행될 수 있다.

[7] 실험기는 정상 상태에서 8건, `/hold` 뒤 8건, ALTER 대기 뒤 12건, `/release` 뒤 12건의 `/item` 요청을 보낸다. 모든 구간은 초당 4건의 개방형 부하이며 앞 요청이 느려져도 예정된 도착률을 낮추지 않는다. 요청 제한 시간은 15초다. ALTER 뒤에는 최대 10초 동안 배타적 잠금 대기를 확인하고, 조회 부하와 겹쳐 최대 4초 동안 조회 잠금 대기를 확인한다. 첫 두 구간의 8건은 모두 200과 `7`, 대기 구간의 12건은 모두 503과 `metadata-timeout`, 회복 구간의 12건은 모두 200과 `7`이어야 한다.

[8] `baseline_ok`, `held_ok`, `recovered_ok`는 각 구간에서 200과 `7`을 받은 요청 수다. `blocked_errors`는 대기 구간에서 503과 `metadata-timeout`을 받은 요청 수다. `blocked_p95_ms`는 대기 구간 전체 12건의 요청 전송부터 응답까지 걸린 시간의 최근접 순위 95백분위수다. 올바른 관찰에서는 오류가 늘고 지연이 잠금 대기 제한 시간 부근으로 늘어난다. 합격 범위는 `baseline_ok = 8`, `held_ok = 8`, `blocked_errors = 12`, `recovered_ok = 12`, `1500 ≤ blocked_p95_ms ≤ 15000`이다. 회복 구간의 성공 건수도 확인하므로 빠르게 실패시키는 구현으로는 통과할 수 없다.

[9] 실험기는 MySQL의 `performance_schema.metadata_locks`를 직접 읽는다. `ddl_pending`은 `lab.items`에 EXCLUSIVE/PENDING과 SHARED_READ/GRANTED가 함께 관찰됐으면 1, 아니면 0이다. `queued_reads`는 조회 부하 중 관찰한 SHARED_READ/PENDING 개수의 최댓값이며 최소 1이어야 한다. `ddl_pending = 1`과 `queued_reads ≥ 1`을 모두 만족해야 한다. SQL 실행 시간이 길다는 사실만으로 메타데이터 잠금이라고 판단하지 마라.

[10] `recovery_ms`는 `/release` 요청 직전부터 `/state`의 DONE을 확인하기까지 걸린 시간이다. 상태 확인은 최대 12초 동안 반복하며 `recovery_ms ≤ 15000`이어야 한다. 이후 회복 부하를 보내고 `marker` 열과 잠금 잔류 여부를 확인한다. `schema_changed`는 DONE 상태와 실제 `marker` 열을 모두 확인했으면 1, 아니면 0이다. `locks_after`는 모든 부하가 끝난 뒤 `lab.items`에 남은 메타데이터 잠금의 수다. 합격하려면 `schema_changed = 1`, `locks_after = 0`이어야 하며 제어 API의 상태 코드도 계약과 같아야 한다. 이 회복 시간은 실험 부하에서 관찰한 값이며 운영 환경의 회복 시간을 보장하지 않는다.

[11] 수정 범위는 `src/main/kotlin/lab/`의 Kotlin 코드다. `.lab-libs`, 빌드·실행 스크립트, 테이블 정의와 SQL, HTTP 경로와 응답, 부하와 지표 계약을 바꾸지 마라. 관찰한 지표와 계약이 어긋나는 원인을 찾아 수정해라. 추가 라이브러리나 외부 부하 도구는 쓰지 마라. 모든 종료 경로에서 연결을 정리해라.

[12] 공식 근거는 [MySQL 8.4 Metadata Locking](https://dev.mysql.com/doc/refman/8.4/en/metadata-locking.html)의 트랜잭션 종료까지의 잠금 유지와 쓰기 잠금 우선순위, [metadata_locks 테이블](https://dev.mysql.com/doc/refman/8.4/en/performance-schema-metadata-locks-table.html)의 LOCK_TYPE·LOCK_STATUS·OBJECT_SCHEMA·OBJECT_NAME이다. JDBC의 자동 커밋 해제와 명시적 종료는 [JDK 21 Connection](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html)의 `setAutoCommit`, `commit`, `rollback` 계약을 따른다. 근거를 읽고 SELECT 완료, ResultSet 닫기, 트랜잭션 종료가 각각 어느 자원을 해제하는지 설명해라.
