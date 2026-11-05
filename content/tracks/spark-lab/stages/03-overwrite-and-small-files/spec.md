# 파티션 덮어쓰기와 작은 파일

[1] 날짜별 매출 파일을 다시 만드는 작업을 관찰해라. 모드는 observe다. 먼저 STATIC·DYNAMIC에서 남는 날짜, 파일 수, 앞 단계 병렬성을 예측하고 시작 코드를 실행해라. 이어서 요청과 다른 실험 설정을 적용하는 WritePolicy를 고쳐 같은 관찰을 반복해라. 파일 수를 줄이는 일이 데이터 보존이나 병렬성 보존을 자동으로 보장하지는 않는다.

[2] 실행 환경은 기존 실행 이미지의 Spark SQL 4.3.0, JDK 21, Kotlin 2.4.21이다. Kotlin 2.4.21은 build.sh가 사용하는 이미지 안의 컴파일러 버전이며, build.gradle.kts의 2.4.20은 IDE용 Gradle 플러그인 버전이다. .lab-libs의 spark 묶음과 local[2] 세션을 사용해라. Spark UI와 AQE는 끄고 셔플 파티션은 4로 고정한다. 날짜를 STRING으로 읽도록 spark.sql.sources.partitionColumnTypeInference.enabled도 false로 둔다. Parquet V1 데이터 소스 테이블을 사용하며 Hive SerDe 테이블은 다루지 않는다. partitionBy는 디렉터리를 나누는 쓰기 옵션이며, 같은 날짜가 여러 태스크에 있으면 날짜 하나에 파일이 여러 개 생긴다.

[3] build.sh로 build/app.jar를 만들고 PORT를 지정해 run.sh를 실행해라. 호스트에서는 127.0.0.1의 PORT로만 접근한다. 컨테이너 내부 서버는 포트 게시를 위해 0.0.0.0에 바인딩한다. GET /health는 Spark 세션이 준비된 뒤 200과 ready를 반환한다. POST /overwrite와 POST /files의 정상 응답은 HTTP 200이며 UTF-8의 key=value 줄로 구성한다. 본문은 비워 보낸다. 잘못된 인자는 400, POST 이외의 실험 요청은 405다. 요청마다 별도 임시 경로와 초기 데이터를 만들며, Spark 작업은 요청 도착 순서대로 하나씩 처리한다.

[4] POST /overwrite?mode=STATIC 또는 mode=DYNAMIC은 같은 초기 테이블을 만든다. 초기 행은 (id, dt) 순으로 (1, 2026-01-01), (2, 2026-01-02), (3, 2026-01-03)이다. 날짜 열은 STRING이다. 설정 spark.sql.sources.partitionOverwriteMode에 요청 모드를 적용한 뒤 INSERT OVERWRITE TABLE daily PARTITION (dt) SELECT id, dt FROM patch를 실행한다. patch는 (20, 2026-01-02), (21, 2026-01-02) 두 행이다. 실제 테이블 이름만 요청마다 달라진다. 정적 날짜 리터럴을 지정한 PARTITION (dt='2026-01-02')와 혼동하지 마라.

[5] STATIC은 INSERT의 파티션 명세에 맞는 영역을 쓰기 전에 지운다. 이 실험은 날짜 리터럴이 없으므로 기존 날짜를 모두 지운다. DYNAMIC은 실제로 새 행이 쓰인 날짜만 교체한다. 따라서 STATIC의 날짜는 2026-01-02 하나이며 id는 20,21이고 행 수는 2다. DYNAMIC의 날짜는 2026-01-01,2026-01-02,2026-01-03이며 id는 1,3,20,21이고 행 수는 4다. 응답 dates와 ids는 중복 없이 오름차순으로 쉼표로 잇고 rows는 실제 행 수다. 두 모드 모두 기존 id 2는 남지 않아야 한다.

[6] POST /files?tasks=T&dates=P&rows=R&strategy=S를 실행해라. T는 2 또는 4, P는 2 또는 3, R은 6 또는 8이다. 입력은 range(0, T×P×R, 1, T)로 만들고 날짜는 id % P + 1을 2026-01-01부터의 날짜 문자열로 바꾼다. 각 입력 태스크에는 날짜마다 R행이 있다. 전략 S는 plain, coalesce, repartition, capped 중 하나다. plain과 capped는 입력 파티션을 유지한다. coalesce는 셔플 없이 쓰기 파티션을 하나로 줄이고, repartition은 셔플로 같은 날짜의 행을 모으되 출력 파티션 수는 T로 유지해야 한다. 모든 전략은 partitionBy("dt")로 새 Parquet 경로에 쓴다. capped만 파일 하나에 최대 5행을 쓰고 나머지는 파일당 행 수를 제한하지 않는다. 이 조건과 [7]–[9]를 함께 만족할 변환과 설정을 선택해라.

[7] plain 파일 수는 T×P, coalesce와 repartition 파일 수는 각각 P다. capped 파일 수는 T×P×ceil(R/5)다. 한 파일을 여러 날짜가 공유하지 않으며 capped는 태스크·날짜마다 파일을 나눈다. 이 식은 [6]의 입력 배치와 AQE를 끈 설정에서만 적용한다. 예를 들어 T=2, P=2, R=6이면 plain 4개, coalesce 2개, repartition 2개, capped 8개다. T=4, P=3, R=8이면 각각 12개, 3개, 3개, 24개다. maxRecordsPerFile은 파일 수의 상한이 아니라 파일당 행 수의 상한이다.

[8] /files 응답은 실제 .parquet 파일만 센 files, 파일을 다시 읽어 센 rows, 오름차순 ids, 오름차순 id@날짜를 쉼표로 이은 records, 날짜 디렉터리별 파일 수 per_date, 가장 큰 파일의 행 수 max_file_rows, 쓰기 작업의 첫 완료 스테이지 태스크 수 first_tasks를 반환한다. per_date는 dt=2026-01-01:개수 형식의 항목을 날짜순으로 쉼표로 잇는다. _SUCCESS와 CRC 파일은 세지 않는다. 모든 전략에서 원래 id와 날짜가 정확히 한 번씩 남아야 하고 rows=T×P×R이어야 한다. capped의 max_file_rows는 1 이상 5 이하이며 날짜마다 파일 수가 동일해야 한다.

[9] first_tasks는 SparkListener의 쓰기 jobGroup에 속한 StageCompleted 이벤트에서 측정한다. 파일을 다시 읽는 검증 작업은 제외한다. 이 입력에는 WritePolicy를 적용하기 전 캐시나 셔플이 없다. coalesce 전략은 앞의 range 계산까지 태스크 하나에서 실행해 first_tasks=1이어야 한다. repartition은 셔플 앞의 T개 입력 태스크를 유지하므로 first_tasks=T여야 한다. plain과 capped도 first_tasks=T다. local[2]의 동시 실행 슬롯 수 2와 스테이지의 태스크 수 T를 구별해라.

[10] daily-refresh 시나리오는 준비 확인 뒤 0, 150, 300, 450ms에 STATIC, DYNAMIC, DYNAMIC, STATIC 요청을 예정대로 보낸다. 날짜·행 결과를 [5]와 대조한다. 지표 requests=4, completed=4, partition_mismatches=0, data_mismatches=0을 모두 만족해야 한다. static_partitions와 dynamic_partitions는 각 모드의 첫 정상 응답에서 센 날짜 수로 각각 1과 3이다. 요청마다 초기 상태를 새로 만들어 앞 요청의 결과가 다음 요청을 바꾸지 않게 해라.

[11] batch-layout 시나리오는 (T,P,R)=(2,2,6), (4,3,8) 순서로 각 조합에 plain, coalesce, repartition, capped 요청을 보내 총 8개를 측정한다. 준비 확인 뒤 0ms부터 150ms 간격으로 보내며 응답을 기다려 다음 도착을 늦추지 않는다. requests=8, completed=8이어야 하고 file_mismatches, data_mismatches, stage_mismatches, limit_violations는 모두 0이어야 한다. file_mismatches는 전체·날짜별 파일 수가 [7]과 다른 응답 수, data_mismatches는 행 수·id·날짜가 [8]과 다른 응답 수, stage_mismatches는 [9]와 다른 응답 수, limit_violations는 capped 파일당 행 수가 [8]을 벗어난 응답 수다. plain_files, coalesced_files, repartitioned_files, capped_files는 두 입력 조합의 해당 파일 수 합이며 각각 16, 5, 5, 32다. 날짜나 수치 필드가 없거나 잘못된 응답도 해당 불일치로 센다.

[12] completed는 HTTP 200 응답 수이며 모든 실험 요청은 180초 안에 끝나야 한다. 준비 확인에는 최대 120초, 실험 전체에는 360초를 허용한다. 시간은 실행 오류와 무응답을 막기 위한 여유 한도이며 성능 합격 지표가 아니다. 실제 판정은 요청 수·파일 수·행 보존·태스크 수로 한다. 각 시나리오는 세 번 실행한다. 응답을 상수로 작성하거나 Spark 작업 대신 직접 파일을 만드는 방식은 허용하지 않는다.

[13] 수정 범위는 src/main/kotlin/lab/WritePolicy.kt의 설정과 Dataset 변환뿐이다. HTTP 처리, 입력 생성, SQL 문장, 리스너, 파일 읽기와 측정 로직, 실행 스크립트, .lab-libs는 바꾸지 마라. 먼저 예측과 관찰을 비교하고 모드 전달, 셔플 유무, 파일당 행 수 제한을 하나씩 확인해라. hidden은 열지 말고 공개 엔드포인트로 관찰해라.

[14] 공식 근거는 [Spark 4.3.0 SQLConf의 PARTITION_OVERWRITE_MODE와 MAX_RECORDS_PER_FILE](https://github.com/apache/spark/blob/v4.3.0/sql/catalyst/src/main/scala/org/apache/spark/sql/internal/SQLConf.scala), [Dataset API의 coalesce와 repartition](https://spark.apache.org/docs/4.3.0/api/java/org/apache/spark/sql/Dataset.html), [DataFrameWriter API의 partitionBy](https://spark.apache.org/docs/4.3.0/api/java/org/apache/spark/sql/DataFrameWriter.html)다. 덮어쓰기 설정은 Hive SerDe 테이블에는 적용되지 않고 DataFrameWriter의 같은 이름 옵션이 세션 설정보다 우선한다. 이 실험은 그런 쓰기 옵션을 지정하지 않고 SQL 설정만 비교한다.

## 관찰 후 힌트

[15] 시작 코드의 응답과 예측을 비교한 뒤 변환 선택이 막혔을 때 읽어라. coalesce(1)은 셔플 없이 파티션을 합치며 앞 단계의 계산도 한 태스크로 모을 수 있다. repartition(T, col("dt"))는 날짜를 기준으로 셔플하면서 입력 단계의 T개 태스크를 유지한다. recordLimit의 반환값은 Storage가 쓰기 옵션 maxRecordsPerFile에 전달한다. 5는 파일당 최대 5행, 0은 제한 없음을 뜻한다.
