# 데이터베이스

관계형 데이터베이스의 트랜잭션 격리, 잠금, 인덱스, 저장 구조를 익힌다. 같은 코드가 격리 수준과 엔진에 따라 다른 결과를 내는 이유, 교착과 대기가 생기는 조건, 질의가 인덱스를 쓰지 못하는 이유를 설명하는 힘을 기른다. 커넥션 풀은 [../spring/README.md](../spring/README.md) 에서, 부하 상황의 갱신 손실은 [../load/README.md](../load/README.md) 에서 다룬다.

## 1. 개념 지도

### 1.1 격리 수준

- MySQL InnoDB 의 기본 격리 수준은 REPEATABLE READ 다. 일반 SELECT 는 트랜잭션의 첫 읽기가 만든 스냅숏을 읽는다.
- InnoDB 의 잠금 읽기(FOR UPDATE, FOR SHARE), UPDATE, DELETE 는 유일 인덱스에 유일 조건이면 찾은 레코드만 잠그고 범위 조건이면 갭 잠금이나 넥스트키 잠금으로 범위를 잠가 삽입을 막는다.
- InnoDB 의 READ COMMITTED 는 읽을 때마다 새 스냅숏을 만들고 갭 잠금을 쓰지 않는다(외래 키와 중복 키 검사는 예외). 팬텀이 생길 수 있다.
- PostgreSQL 의 기본 격리 수준은 READ COMMITTED 다. READ UNCOMMITTED 는 READ COMMITTED 처럼 동작한다. REPEATABLE READ 는 팬텀도 막는다. SERIALIZABLE 은 직렬화 스냅숏 격리(SSI)로 직렬화 이상을 막고 감지하면 SQLSTATE 40001 로 트랜잭션을 실패시킨다. 애플리케이션은 트랜잭션을 처음부터 다시 실행해야 한다.
- 스냅숏 격리에서도 쓰기 비틀림(write skew)이 생긴다. 두 트랜잭션이 같은 조건을 읽고 서로 다른 행을 바꾸면 각자는 옳지만 합친 결과가 규칙을 어긴다.
- 읽고 계산해서 쓰면 갱신 손실이 생긴다. 원자적 갱신, 잠금 읽기, 버전 열을 쓴 낙관적 잠금으로 막는다.

### 1.2 인덱스와 질의 계획

- B+트리 인덱스는 키 순서로 정렬되어 있어 같음과 범위 조건, 정렬을 돕는다. 복합 인덱스는 앞 열부터 쓴다. 질의에 필요한 열이 모두 인덱스에 있으면 테이블을 읽지 않는다(커버링 인덱스).
- 인덱스 열에 함수를 씌우거나 자료형이 달라 암묵 변환이 일어나면 인덱스를 쓰지 못할 수 있다. 실행 계획(EXPLAIN)으로 확인한다. 카드를 만들 때 MySQL Optimization 장과 PostgreSQL Indexes 장으로 세부를 확인한다.
- 오프셋 페이지네이션은 앞 행을 모두 세며 건너뛴다. 키셋(커서) 페이지네이션은 마지막 키에서 이어 읽는다.

### 1.3 메타데이터 잠금과 스키마 변경

- 긴 트랜잭션이 테이블을 쓰고 있으면 ALTER TABLE 이 메타데이터 잠금을 기다리고 그 뒤에 온 질의가 모두 줄을 선다. 카드를 만들 때 MySQL Metadata Locking 문서로 대기 규칙을 확인한다.

### 1.4 저장 구조

- LSM 트리는 쓰기를 메모리 테이블에 모았다가 정렬된 파일로 내리고 압축으로 합친다. 쓰기 증폭, 읽기 증폭, 공간 증폭이 서로 맞바뀐다. 블룸 필터로 없는 키의 읽기를 줄이고 삭제는 툼스톤으로 남긴다. 레벨 압축과 크기 계층 압축이 다르다.

## 2. 학습 경로

1. R-database-10 기술 지문으로 MVCC 와 격리 수준별 현상을 익힌다.
2. B-database-20 `mvcc` 트랙에서 스냅숏 격리, 잠금과 교착 감지, 직렬화 실패를 구현한다.
3. B-database-30 `lsm` 트랙에서 LSM 저장소를 만든다.
4. R-database-40 기술 지문으로 운영 중 데이터 이전과 샤딩을 익힌다. 배경은 '사례 글' 절에 있다.
5. L-database-50~L-database-53 실험은 Docker 의 MySQL 과 PostgreSQL 로 한다.

## 3. 환경

- reading 과 build 카드는 추가 도구가 필요 없다.
- lab 카드는 Docker 의 MySQL 8.4 와 PostgreSQL 이미지가 필요하다. 실험 코드는 JDBC 드라이버 의존성이 필요하다.

## 4. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 복제 지연과 쓴 뒤 읽기 | lab | 읽기 복제본으로 보낸 질의가 방금 쓴 값을 못 읽는 경우 |
| 해시 분산 DB 의 분산 열 | reading | 분산 열 선택, 같은 노드에 놓인 조인, 참조 테이블 |
| 샤드별로 쓰기 묶기 | lab | 분산 열 값의 샤드를 미리 구해(Citus 는 `get_shard_id_for_distribution_column`) 트랜잭션 하나가 샤드 하나만 쓰게 묶는 방식. 여러 샤드에 걸친 트랜잭션의 2단계 커밋과 분산 교착, 처리량 비교. Citus 이미지가 필요하다 |
| 시간대와 TIMESTAMP 자료형 | code | 자료형마다 저장과 변환 규칙이 다른 문제 |
| 문자 집합과 콜레이션 | fix | 대소문자를 구분하지 않는 콜레이션의 유일 키 충돌 |

## 5. 사례 글

카드를 만들 때 배경 자료로 쓴다. 수치는 각 글이 쓰인 때의 값이다.

- 온라인 이전(Stripe, 2017): 이전을 네 단계로 나눈다. 옛 테이블과 새 테이블에 함께 쓰는 단계, 모든 읽기 경로를 새 테이블로 바꾸는 단계, 모든 쓰기 경로가 새 테이블에만 쓰게 바꾸는 단계, 옛 데이터 모형에 기댄 데이터를 지우는 단계다. 과거 데이터는 DB 스냅숏을 Hadoop 의 MapReduce 로 처리해 채운다. 옛 경로와 새 경로를 Scientist 로 함께 실행해 결과가 다르면 알림과 지표를 남긴다. 실험 경로에서 오류가 나도 서비스에는 영향이 없다.
- 샤딩(Notion, 2021): 논리 샤드 480개를 물리 DB 32대에 고르게 나눴다. 480 은 많은 수로 나누어떨어져 물리 서버를 늘리거나 줄여도 고르게 나눌 수 있다. 분할 키는 워크스페이스 ID 다. 이전 중의 쓰기는 감사 기록 테이블에 남기고 따라잡기 프로세스가 새 DB 에 적용했다. 읽기를 옮기기 전에는 옛 DB 와 새 DB 를 함께 읽어 비교했다(dark read). 전환은 계획된 5분 점검 시간에 했다.
- 샤딩(Figma, 2024): 관련 테이블 묶음을 먼저 수직 분할하고 그다음 같은 샤드 키를 쓰는 테이블 묶음(colo) 단위로 수평 샤딩했다. 애플리케이션의 논리 샤딩과 Postgres 의 물리 샤딩을 나눴다. DBProxy 가 SQL 을 구문 트리로 바꿔 논리 샤드 ID 를 추출해 물리 DB 로 보낸다. 실제 트래픽이 샤드 키 후보마다 어떻게 동작할지 미리 예측하는 장치(shadow application readiness)를 두었다. 첫 물리 샤드 분할은 기본 DB 의 부분 가용성 저하 10초, 복제본 영향 없이 끝났다.
- 메시지 저장(Discord, 2023): Cassandra 177대에서 핫 파티션, GC 멈춤으로 인한 지연 급등, 압축 적체를 겪었고 ScyllaDB 72대로 옮겼다. DB 앞에 둔 데이터 서비스는 여러 사용자가 같은 행을 동시에 요청하면 DB 를 한 번만 읽는다. 라우팅은 채널 ID 기준의 일관 해싱이다. 이전은 초당 최대 320만 건으로 9일 걸렸다. p99 지연은 과거 메시지 읽기가 40~125ms 에서 15ms 로, 메시지 쓰기가 5~70ms 에서 5ms 로 줄었다.
- DB 교체(Uber, 2016): Postgres 9.2 에서 겪은 한계로 쓰기에 비효율적인 구조, 비효율적인 데이터 복제, 테이블 손상, 복제본의 약한 MVCC 지원, 새 버전 업그레이드의 어려움을 들었다. 많은 경우를 MySQL 위의 샤딩 계층 Schemaless 로 옮겼다. 글은 Postgres 9.2 시절의 구조를 다루므로 현재 버전과 비교할 때는 버전 차이를 확인한다.

## 6. 출처

- MySQL 8.4 Reference Manual, Transaction Isolation Levels: https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html
- MySQL 8.4 Reference Manual, Locking Reads: https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html
- PostgreSQL 18 문서, Transaction Isolation: https://www.postgresql.org/docs/current/transaction-iso.html
- Citus 문서, Citus Utility Functions(`get_shard_id_for_distribution_column`): https://docs.citusdata.com/en/stable/develop/api_udf.html
- Kleppmann, "Designing Data-Intensive Applications", 7장(트랜잭션), 3장(저장소와 검색).
- O'Neil 외, "The Log-Structured Merge-Tree (LSM-Tree)", Acta Informatica 1996.
- Berenson 외, "A Critique of ANSI SQL Isolation Levels", SIGMOD 1995.
- Stripe, "Online migrations at scale"(2017-02-02): https://stripe.com/blog/online-migrations
- Notion, Postgres 샤딩 글(2021-10-06): https://www.notion.com/blog/sharding-postgres-at-notion
- Figma, "How Figma's databases team lived to tell the scale"(2024-03-14): https://www.figma.com/blog/how-figmas-databases-team-lived-to-tell-the-scale/
- Discord, "How Discord Stores Trillions of Messages"(2023-03-06): https://discord.com/blog/how-discord-stores-trillions-of-messages
- Uber, "Why Uber Engineering Switched from Postgres to MySQL"(2016-07-26): https://www.uber.com/blog/postgres-to-mysql-migration/
