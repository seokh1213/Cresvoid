# 실무

서버 개발자가 실무에서 만나는 영역을 도메인별로 나눠 다룬다. 부하가 걸린 서비스, 프레임워크, 데이터베이스, 메시징, 검색, 시스템 설계, 데이터 플랫폼, AI 에이전트, 그리고 그 밑의 분산 시스템과 운영 기반이다. 공개 장애 보고서로 실제 장애의 원인 사슬도 읽는다.

## 1. 도메인 지도

| 묶음 | 도메인 | 폴더 | 다루는 것 |
|---|---|---|---|
| 서비스 | 부하 엣지 케이스 | [load](load/README.md) | 캐시, 동시성, 재시도, 과부하, 속도 제한 |
| 서비스 | 프레임워크 심화와 튜닝 | [spring](spring/README.md) | 커넥션 풀, 트랜잭션 프록시, 실행기, HTTP 클라이언트, JPA |
| 서비스 | 데이터베이스 | [database](database/README.md) | 격리 수준, 잠금, 인덱스, LSM |
| 서비스 | 메시징 | [messaging](messaging/README.md) | 파티션, 복제, 커밋 시점, 압축 |
| 서비스 | 검색 | [search](search/README.md) | 샤드, refresh, translog, 집계 근사, 한글 분석 |
| 서비스 | 시스템 설계 | [system-design](system-design/README.md) | 규모 추정, 처리율 제한, ID 생성, 피드, 채팅, 원장, 매칭 엔진 |
| 데이터 플랫폼 | Spark | [spark](spark/README.md) | 실행 계획, 셔플, 적응 실행, SQL 의미론 |
| 데이터 플랫폼 | HDFS | [hdfs](hdfs/README.md) | 블록 배치, 쓰기 경로, 장애 회복, 작은 파일 |
| 데이터 플랫폼 | 레이크하우스 | [lakehouse](lakehouse/README.md) | Parquet, Iceberg, 통계 기반 건너뛰기 |
| 데이터 플랫폼 | OLAP | [olap](olap/README.md) | 열 저장, 희소 인덱스, 사전 집계, 근사 집계 |
| AI | AI 에이전트 | [ai-agent](ai-agent/README.md) | 도구 루프, 컨텍스트, RAG, MCP |
| 기반 | 분산 시스템 | [distributed](distributed/README.md) | 일관성, Raft, CRDT, 일관 해싱, 시뮬레이션 시험 |
| 기반 | 관측성 | [observability](observability/README.md) | 백분위수, 히스토그램, 소진율 |
| 기반 | JVM | [jvm](jvm/README.md) | 힙 크기, ThreadLocal, JIT, 스레드 덤프 |
| 기반 | 네트워크 | [network](network/README.md) | keep-alive, 타임아웃 |
| 기반 | 코루틴 | [coroutines](coroutines/README.md) | 중단과 재개, 구조적 동시성, 취소, 예외 전파, 디스패처, Flow |
| 운영 | 장애 보고서 | [incidents](incidents/README.md) | 공개 사후 보고서의 원인 사슬, 회복 지연, 재발 방지책 |

## 2. 도메인 하나를 배우는 순서

1. 기술 독해(reading, `tech-reading` 트랙): 개념 모형을 수능 비문학 형식의 지문과 문항으로 먼저 익힌다.
2. 구현(build): 그 시스템의 핵심을 작게 직접 만든다. 표준 입출력 명령 처리기라 외부 도구 없이 결정적으로 채점한다.
3. 코드 읽기와 버그 수정(code, fix): 그 도메인에서 생기는 함정을 코드로 만난다.
4. 실험(lab): 실제 시스템에 부하나 장애를 넣고 예측과 관측을 맞춘다. 엔진 작업과 도구 설치가 필요하다.

## 3. 종류를 고르는 기준

- 개념 사이의 인과와 조건이 핵심이면 reading 으로 만든다.
- 규칙을 결정적으로 명세할 수 있으면 build 로 만든다. 실제 제품의 출력 형식이나 내부 구현을 흉내 내지 않고 명세가 정한 단순한 형식을 쓴다.
- 코드에서 생기는 함정이 핵심이면 code 나 fix 로 만든다.
- 실제 제품의 기본값, 버전별 동작, 부하에서의 성질이 핵심이면 lab 으로 만든다.

## 4. 여러 도메인이 함께 쓰는 트랙

| 트랙 | 종류 | 출제 기준 |
|---|---|---|
| `tech-reading` | reading | [../reading/README.md](../reading/README.md) 의 '출제 기준' 절 |
| `code-reading` | code | [../implementation/README.md](../implementation/README.md) 의 'code: 남의 코드를 끝까지 읽는다' 절. C 카드와 도메인 카드가 함께 쓴다 |
| `bug-fix` | fix | [../implementation/README.md](../implementation/README.md) 의 'fix: 버그를 재현하고 고친다' 절. F 카드와 도메인 카드가 함께 쓴다 |
| `incident-reading` | reading | [incidents/README.md](incidents/README.md) 의 '지문 쓰는 규칙' 절 |

단계 하나짜리 카드(reading, code, fix, lab)는 만들기 전까지 단계 번호 없이 영문 slug 만 적는다. 카드를 제작 중으로 바꿀 때 [../INDEX.md](../INDEX.md) 트랙 표의 다음 번호를 붙인다. 그래서 단계 번호는 만든 순서와 같다. build 카드는 카드 하나가 단계 순서를 정하므로 처음부터 번호를 적는다.

## 5. 환경

카드의 `env` 값과 그 값이 뜻하는 결정이다. 결정이 나기 전에는 그 카드를 만들지 않는다.

| env | 필요한 것 | 결정할 것 |
|---|---|---|
| 없음 | JDK 21, kotlinc | 없음 |
| `k6` | 부하 생성기 k6 | `brew install k6` |
| `redis` | Redis 서버 | `brew install redis` |
| `docker` | Elasticsearch, ClickHouse, Kafka, MySQL, PostgreSQL 이미지 | Docker 사용 여부와 이미지 버전 |
| `maven` | 실험 코드의 Maven 의존성 | 허용 범위와 버전 고정 방식 |
| `gradle` | Spring Boot 프로젝트 빌드 | Gradle 래퍼를 둘지 |
| `spark` | Spark 실행 환경 | Docker 이미지와 Homebrew 가운데 무엇을 쓸지 |
| `llm-api` | 실제 모델 API | API 키와 비용 한도 |
| `kotlinx-coroutines` | kotlinx-coroutines-core, kotlinx-coroutines-test | 허용함(D16). 버전과 받는 방식은 첫 카드를 만들 때 정한다 |

kind 가 lab 인 카드는 env 와 상관없이 lab 종류 엔진 작업([lab.md](lab.md))이 먼저다. JVM 과 네트워크 실험은 추가 도구 없이 JDK 만 쓴다.

## 6. 아직 폴더가 없는 주제

| 주제 | 묶음 | 메모 |
|---|---|---|
| Kubernetes probe 와 우아한 종료 | 운영 | readiness 와 liveness 의 차이, SIGTERM 과 처리 중인 요청 정리. 종료 처리는 JDK 만으로 lab 이 된다 |
| HPA 판정 방식 | 운영 | 대상 pod 의 평균으로 판정해 일부 pod 의 쏠림이 묻히는 경우, `Utilization`(requests 대비 비율)과 `AverageValue`(절댓값)의 차이, 지표 수집 간격보다 짧은 급증, 지연(lag) 같은 외부 지표로 pod 늘리기, 줄일 때만 있는 300초 안정화 창. R-incidents-70 의 자동 축소와 이어진다 |
| 인증과 토큰 | 보안 | 토큰 폐기, 키 교체, 인가 코드 흐름과 PKCE |
| SSRF 와 입력 검증 | 보안 | 내부 주소 접근, 리다이렉트 우회 |
| API 하위 호환 | API 설계 | 필드와 enum 추가가 클라이언트를 깨는 경우, 버전 관리 |
| 웹훅 전달 | API 설계 | 재시도, 서명, 순서, 멱등 수신 |

이 표의 주제가 카드 세 장 이상으로 자라면 폴더를 만든다.
