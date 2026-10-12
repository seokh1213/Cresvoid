# Spark

분산 데이터 처리 엔진 Spark 의 실행 모델, SQL 의미론, 운영 함정을 익힌다. 데이터 파이프라인을 다룰 때 코드 한 줄이 몇 개의 스테이지와 태스크, 얼마의 셔플로 바뀌는지 예측하고 결과가 틀리거나 느린 원인을 찾는 힘을 기른다.

## 1. 개념 지도

기본값은 2026-10-08 에 Spark 4.2.0 문서로 확인한 값이다. 버전마다 달라지므로 카드를 만들 때 그 버전 문서로 다시 확인한다.

### 1.1 실행 모델

- 변환(transformation)은 지연된다. 행동(action)이 잡(job)을 하나 만들고 그때 계산이 시작된다.
- 잡은 셔플 경계에서 스테이지로 나뉜다. 스테이지는 파티션마다 태스크를 하나 만든다.
- 좁은 의존(map, filter)은 부모 파티션 하나에만 기대므로 같은 스테이지 안에서 이어 실행된다. 넓은 의존(groupByKey, reduceByKey, join, repartition)은 셔플을 만든다.
- 셔플은 디스크 I/O, 직렬화, 네트워크 I/O 가 들어 비싸다.
- reduceByKey 는 맵 쪽에서 미리 합쳐 셔플량을 줄인다. groupByKey 는 값을 모두 보낸다.
- 같은 파티셔너로 나뉜 두 RDD 의 조인은 셔플 없이 좁은 의존이 된다.
- coalesce(n) 은 셔플 없이 파티션을 줄인다. n=1 처럼 크게 줄이면 앞 단계 계산까지 노드 하나에서 실행된다. repartition 은 셔플을 넣는 대신 앞 단계를 병렬로 유지한다.
- 클로저 안에서 드라이버 변수를 바꾸면 실행기의 사본만 바뀐다. 드라이버의 값은 그대로다.

### 1.2 SQL 엔진과 적응 실행

- 셔플 파티션 수의 기본값은 200(`spark.sql.shuffle.partitions`)이다.
- 브로드캐스트 조인 임계값은 10MB(`spark.sql.autoBroadcastJoinThreshold`)다. 조인 힌트의 우선순위는 BROADCAST, MERGE, SHUFFLE_HASH, SHUFFLE_REPLICATE_NL 순서다.
- 적응 실행(AQE, `spark.sql.adaptive.enabled`)은 3.2.0 부터 기본으로 켜져 있다. 셔플이 끝난 뒤의 실제 통계로 계획을 고친다.
  - 파티션 병합: 권장 크기 64MB(`advisoryPartitionSizeInBytes`), 최소 크기 1MB. `parallelismFirst` 가 기본 true 라 권장 크기보다 병렬성을 먼저 지킨다.
  - 정렬 병합 조인을 브로드캐스트 조인으로 바꾼다.
  - 쏠린 파티션을 쪼갠다. 파티션 크기가 중앙값의 5배를 넘고 동시에 256MB 를 넘어야 쏠렸다고 판정한다.
- 파일을 읽을 때 파티션 하나의 최대 크기는 128MB(`spark.sql.files.maxPartitionBytes`), 파일 하나를 여는 비용은 4MB 로 계산한다.

### 1.3 의미론 함정

- `=` 는 한쪽이 NULL 이면 NULL 이다. `<=>` 는 둘 다 NULL 이면 true, 한쪽만 NULL 이면 false 다. `=` 로 조인하면 키가 NULL 인 행은 맞는 짝이 없다.
- GROUP BY 와 DISTINCT 는 NULL 을 한 묶음으로 다룬다. 집합 연산(UNION, INTERSECT, EXCEPT)도 NULL 끼리 같다고 다룬다.
- `NOT IN` 의 하위 질의에 NULL 이 있으면 결과는 비게 된다.
- COUNT(*) 는 NULL 행도 센다. COUNT(열), SUM, AVG, MAX, MIN 은 NULL 을 건너뛴다. 빈 입력의 SUM 과 MAX 는 NULL 이다.
- 정렬에서 NULL 은 오름차순이면 앞에, 내림차순이면 뒤에 온다.
- `union` 은 열을 이름이 아니라 위치로 맞춘다. 열 순서가 다른 두 DataFrame 을 합치면 오류 없이 값이 섞인다. `unionByName` 은 이름으로 맞춘다.
- 일부 열만 지정한 `dropDuplicates` 가 어느 행을 남기는지는 문서가 정하지 않는다.
- `monotonically_increasing_id` 는 유일하고 증가하지만 연속이 아니다. 상위 31비트가 파티션 번호이고 파티션 구성에 따라 값이 달라지는 비결정 함수다.
- Spark 4.0 부터 `spark.sql.ansi.enabled` 가 기본 true 다. 넘침, 잘못된 CAST, 0 나누기가 NULL 대신 오류를 낸다. 3.x 에서 돌던 작업이 4.x 에서 실패하는 대표 원인이다. 같은 판에서 `spark.sql.legacy.timeParserPolicy` 기본값도 CORRECTED 로 바뀌었다.
- 날짜 경계는 세션 시간대(`spark.sql.session.timeZone`)가 정한다. 한국 시각과 UTC 의 일 집계는 9시간 어긋난다.

### 1.4 쓰기와 커밋

- INSERT OVERWRITE 의 파티션 덮어쓰기 모드(`spark.sql.sources.partitionOverwriteMode`) 기본값은 STATIC 이다. 정적 모드와 동적 모드가 지우는 파티션의 범위가 다르다. 카드를 만들 때 SQLConf 설명으로 정확한 의미를 확인한다.
- `partitionBy` 로 쓰면 태스크마다 파티션 값마다 파일이 생겨 작은 파일이 쏟아질 수 있다. 파일당 행 수 제한(`spark.sql.files.maxRecordsPerFile`)의 기본값은 0(제한 없음)이다.

### 1.5 장애와 재실행

- 태스크는 실패하면 다시 실행된다. 같은 태스크가 연속 4번(`spark.task.maxFailures`) 실패하면 잡이 실패한다.
- 행동 안에서 한 누산기(accumulator) 갱신만 한 번 반영이 보장된다. 변환 안의 갱신은 태스크나 스테이지가 다시 실행되면 두 번 이상 반영될 수 있다.
- 투기 실행(`spark.speculation`)은 기본으로 꺼져 있다. 켜면 스테이지의 90% 가 끝난 뒤 중앙값보다 3배 느린 태스크를 하나 더 실행한다. 외부 시스템에 쓰는 태스크는 중복을 낸다.
- 행동 하나의 결과를 드라이버로 모을 때 직렬화 크기가 1g(`spark.driver.maxResultSize`)를 넘으면 실패한다.
- 메모리: (힙 − 300MB) 의 0.6(`spark.memory.fraction`)이 실행과 저장 공간이다. 그 가운데 0.5(`spark.memory.storageFraction`)는 축출되지 않는 저장 공간이다. 실행기 오버헤드는 실행기 메모리의 10%(`spark.executor.memoryOverheadFactor`)이고 최솟값이 있다.
- RDD 의 `cache()` 는 MEMORY_ONLY 다. 메모리에 안 들어가는 파티션은 필요할 때마다 다시 계산한다. Dataset 의 `cache()` 는 MEMORY_AND_DISK 다. 캐시도 첫 행동 때 채워진다.

### 1.6 구조적 스트리밍

- 창 집계의 상태는 (엔진이 본 최대 이벤트 시각 − 지연 한도) 가 창의 끝을 지날 때까지 남는다.
- 지연 한도 안의 늦은 데이터는 반드시 집계된다. 한도를 넘은 데이터는 버려질 수도 있고 반영될 수도 있다. 보장은 한쪽 방향뿐이다.
- append 모드의 창 집계는 워터마크가 창을 지난 뒤 한 번 출력된다. 그래서 출력이 지연 한도만큼 늦다.
- `foreachBatch` 는 기본이 최소 한 번 쓰기다. batchId 로 중복을 걸러야 정확히 한 번이 된다.

## 2. 학습 경로

1. R-spark-10 기술 지문으로 실행 모델과 셔플을 먼저 익힌다.
2. B-spark-20 `dataflow` 트랙에서 계획, 셔플, 조인, 적응 실행을 직접 구현한다.
3. L-spark-30~L-spark-34 실험으로 실제 Spark 의 의미론과 성능을 확인한다. Spark 실행 환경을 정한 뒤에 한다.

## 3. 환경

- reading 과 build 카드는 추가 도구가 필요 없다.
- lab 카드(`env: spark`)는 Spark 실행 환경이 필요하다. Docker 의 Spark 이미지에서 `spark-sql` 과 `spark-submit` 을 쓰는 방법과 Homebrew 로 설치하는 방법이 있다. Kotlin 스크립트에 spark-sql 의존성을 넣는 방법은 의존성이 수백 MB 이고 Scala 바이너리 버전을 맞춰야 한다. 사용자가 정한다.
- 엔진이 lab 의 Spark 실행을 지원하려면 [../lab.md](../lab.md) 의 '도메인 lab 의 의존 서비스' 절의 의존 서비스 방식을 따른다.

## 4. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 캐시와 체크포인트로 계보 끊기 | lab | 반복 알고리즘에서 계보가 길어질 때의 재계산 비용 |
| Spark UI 읽기 | lab | 스테이지 타임라인과 태스크 시간 분포로 쏠림과 흘림을 찾는다 |
| 윈도 함수와 정렬 동점 | lab | 동점 행의 순서가 실행마다 달라지는 문제 |
| 상태 저장소 크기 | lab | 스트리밍 중복 제거와 상태가 커지는 조건 |
| 브로드캐스트 변수와 큰 조회표 | lab | 드라이버 메모리와 직렬화 비용 |

## 5. 출처

- Spark 4.2.0 문서
  - Performance Tuning: https://spark.apache.org/docs/latest/sql-performance-tuning.html
  - Configuration: https://spark.apache.org/docs/latest/configuration.html
  - RDD Programming Guide: https://spark.apache.org/docs/latest/rdd-programming-guide.html
  - NULL Semantics: https://spark.apache.org/docs/latest/sql-ref-null-semantics.html
  - ORDER BY Clause: https://spark.apache.org/docs/latest/sql-ref-syntax-qry-select-orderby.html
  - Migration Guide: https://spark.apache.org/docs/latest/sql-migration-guide.html
  - Structured Streaming: https://spark.apache.org/docs/latest/streaming/apis-on-dataframes-and-datasets.html
  - Dataset API: https://spark.apache.org/docs/latest/api/scala/org/apache/spark/sql/Dataset.html
  - 내장 함수(misc): https://spark.apache.org/docs/latest/api/sql/misc-functions/
- SQLConf 정의(설정 설명과 기본값): https://github.com/apache/spark/blob/master/sql/catalyst/src/main/scala/org/apache/spark/sql/internal/SQLConf.scala
- Zaharia 외, "Resilient Distributed Datasets: A Fault-Tolerant Abstraction for In-Memory Cluster Computing", NSDI 2012.
