# 정렬 키와 읽는 그래뉼 수

[1] 같은 행을 ClickHouse MergeTree 테이블 세 개에 넣고 정렬 키에 따라 읽기량과 압축량이 어떻게 달라지는지 관찰해라. 먼저 시작 코드의 결과를 예측하고 실험해라. 그다음 테이블 a의 설정만 고쳐 질의 묶음의 읽기량을 줄여라. Docker Desktop을 켜고 JDK 21과 기존 Kotlin 빌드 도구를 써라. 추가 라이브러리와 외부 부하 도구는 쓰지 않는다.

[2] MergeTree는 파트 안의 행을 ORDER BY 순서로 정렬하고 희소 기본 인덱스로 필요 없는 그래뉼을 건너뛴다. 이 실험은 index_granularity=4096, index_granularity_bytes=0으로 행 수 기준 그래뉼을 사용한다. 조건에 맞는 행이 몇 개뿐이어도 그 행이 든 그래뉼을 읽는다. 인덱스 경계에서는 이웃 그래뉼까지 읽을 수 있다. read_rows를 결과 행 수나 디스크에서 새로 읽은 행 수로 해석하지 마라. 캐시에 데이터가 있어 실행 시간이 줄어도 논리적인 읽기량은 그대로일 수 있다.

[3] 공개 데이터는 id가 0 이상 1,048,576 미만인 정수 행이다. tenant UInt16은 id % 64, event_type UInt8은 (id / 2048) % 4, amount UInt64는 id % 97 + 1이다. 나눗셈은 정수 나눗셈이다. event_time은 UTC 2026-01-01 00:00:00에 ((id / 64) % 32)일과 (id / 8192)초를 더한 DateTime이다. 따라서 tenant는 64종, 날짜는 2026-01-01부터 2026-02-01까지 32종이다. 각 테이블은 모든 행을 정확히 한 번 보관한다. INSERT SELECT 한 번으로 넣고 OPTIMIZE FINAL로 파티션마다 한 파트로 합친 뒤 측정한다.

[4] 테이블 이름은 sorting_lab.a, sorting_lab.b, sorting_lab.c다. a의 정렬 키와 파티션 식은 Tuning.kt가 정한다. b의 정렬 키는 (event_time, tenant, event_type), c는 (event_type, tenant, event_time)이다. b와 c의 파티션 식은 toYYYYMM(event_time)이다. 시작 코드 a는 (event_type, event_time, tenant)로 정렬하고 월로 파티션한다. 타입, 행, 엔진, 그래뉼 크기와 압축 코덱은 세 테이블에서 같다. 압축량은 순서뿐 아니라 파트 경계에도 영향을 받으므로 파티션 식을 바꾼 비교에는 두 효과가 함께 들어간다.

[5] build.sh로 build/app.jar를 만들고 run.sh로 HTTP 서비스를 실행해라. run.sh는 PORT 환경 변수의 127.0.0.1 포트에서 듣고 CLICKHOUSE_URL의 ClickHouse HTTP 주소에 연결한다. GET /health는 ClickHouse SELECT 1이 성공하면 HTTP 200과 ok를 반환한다. POST /prepare는 빈 실험 DB에 세 테이블을 적재하고 200과 ready를 반환한다. 한 서비스 실행에서 준비는 한 번만 호출해라. 준비 뒤 GET /query?table=a&kind=tenant&tenant=17&day=7&type=2를 호출하면 200과 query_id, sum(amount)를 탭으로 구분한 한 줄을 반환한다. table은 a·b·c, kind는 tenant·day·type·window만 허용한다. tenant는 0~63, day는 0~31, type은 0~3이다. 생략한 값은 예시와 같으며 잘못된 값은 400, 없는 경로는 404다.

[5.1] 수동 관찰은 저장소 루트에서 아래 명령으로 실행해라. 마지막 인자는 관찰할 학습자 프로젝트 경로로 바꿔라. 도구는 기존 build.sh로 빌드하고 고정 이미지의 새 ClickHouse를 띄운 뒤, 빈 포트를 찾아 CLICKHOUSE_URL과 PORT를 설정하고 서비스를 시작한다. /health 확인 뒤 /prepare를 정확히 한 번 호출한다. 예측을 적고 Enter를 누르면 /observe와 /storage를 차례로 호출하고 원문을 출력한다. 출력한 주소로 다른 터미널에서 curl을 실행할 수도 있다. 관찰 뒤 Enter 또는 Ctrl+C로 끝내라. 정상 종료·오류·중단 시 서비스와 이 실행의 컨테이너·볼륨을 정리한다. 설정을 바꿀 때마다 새로 실행해라.

```sh
kotlin content/tracks/olap-lab/stages/01-sorting-key/tools/observe.main.kts /absolute/path/to/learner-project
```

[6] tenant 질의는 tenant 하나, day 질의는 지정 날짜의 UTC 자정 이상 다음 자정 미만, type 질의는 event_type 하나를 조건으로 sum(amount)를 구한다. window는 tenant 조건과 day 조건의 교집합이다. query_id는 매 실행마다 새 UUID다. GET /observe는 tenant=17, day=7, type=2로 각 테이블의 tenant·day·type 질의를 순서대로 실행한다. 응답은 9행이며 각 행은 테이블, kind, 합계, read_rows, SelectedMarks를 탭으로 구분한다. GET /storage는 테이블 이름순으로 테이블, 활성 파트의 총 행 수, data_compressed_bytes 합, data_uncompressed_bytes 합, 활성 파트 수를 탭으로 구분한 3행을 반환한다. 두 경로도 준비 뒤 호출하고 HTTP 200을 받아야 한다.

[7] 처음에는 GET /observe의 9행을 보기 전에 tenant·day·type 질의별로 가장 적게 읽을 테이블을 예측해라. 동률이면 해당 테이블을 모두 적어라. 실제 read_rows와 SelectedMarks를 비교하고, GET /storage의 압축된 바이트 / 압축 전 바이트 비율도 기록해라. 비율이 작을수록 저장 공간을 적게 쓴다. 정렬 키의 첫 열만 보고 나머지 열은 무조건 쓸모없다고 단정하지 마라. 희소 인덱스는 비선두 열 조건으로도 일부 범위를 제외할 수 있다. 읽기량을 줄인 순서가 모든 열의 압축률까지 가장 좋게 만드는지도 따로 확인해라.

[8] 두 실험 시나리오는 각각 새 ClickHouse 컨테이너와 새 서비스를 띄워 준비를 마치고 /observe와 /storage를 수집한다. 이어 테이블 a에 초당 2개씩 4초 동안 총 8개 요청을 예정 시각에 보낸다. 앞 요청이 느려도 다음 요청의 도착을 늦추지 않는 개방형 부하다. first-pass는 tenant 3·17·38·59, second-pass는 tenant 0·21·42·63을 쓴다. 각 tenant마다 tenant 질의 한 번과 window 질의 한 번을 이 순서로 보낸다. window의 day는 tenant 목록의 순서대로 2·11·20·29다. 실험은 시나리오별로 세 번 반복한다. 요청 제한 시간은 90초, 실험 전체 제한 시간은 420초다. 시간은 오류를 탐지하는 상한이며 성능 합격 기준은 아니다.

[9] 모든 요청은 HTTP 200으로 완료되고 모든 합계가 공개 데이터의 정확한 합과 같아야 한다. 활성 데이터는 각 테이블 1,048,576행이어야 하며 a의 활성 파트 수는 2개 이하여야 한다. 모든 측정 질의의 read_rows와 SelectedMarks는 양수여야 하며, a에 보낸 8개 부하 질의의 read_rows 합은 160,000 이하여야 한다. 실험기는 SYSTEM FLUSH LOGS 뒤 system.query_log의 QueryFinish를 query_id로 찾아 실제 읽기량을 합산한다. 관찰 질의 9개의 읽기량과 준비·메타데이터 질의는 이 예산에서 제외한다. 합계를 상수로 돌려주거나 데이터를 삭제·복제하거나 질의 조건을 바꿔 예산을 맞추지 마라.

[10] 출력 지표 request_errors는 HTTP 오류·요청 예외의 수, wrong_results는 합계·행 수·파트 수·관찰 응답·질의 기록의 위반 수다. 둘 다 0이어야 한다. failure_flags는 위반 범주의 비트 합이다. 합계·부하 응답 형식은 1, 질의 기록 누락·중복·읽기량 또는 SelectedMarks 비양수는 2, /observe 위반은 4, /storage와 실제 저장 상태 불일치는 8, 실제 테이블 누락·행 수 위반은 16, a의 파트 상한 위반은 32, 압축 바이트 비양수는 64다. 같은 범주는 여러 번 위반해도 한 번만 더하므로 33이면 합계와 파트 수를 확인해라. tuned_read_rows는 부하 질의 8개의 read_rows 합이며 160,000을 넘으면 읽기 예산 위반이다. a_tenant_rows·b_tenant_rows·c_tenant_rows는 /observe에서 얻은 각 테이블의 tenant 질의 읽기량이다. a_compression·b_compression·c_compression은 각 테이블의 압축된 바이트 / 압축 전 바이트다. 압축 비율에는 순위나 시간 임계값을 두지 않는다. 측정 지표는 10개이며, 일·종류 질의의 세부 읽기량과 SelectedMarks는 /observe에서 확인해라.

[11] 수정 범위는 src/main/kotlin/Tuning.kt의 order와 partition뿐이다. order에는 tenant·event_time·event_type을 각각 한 번씩 넣어 순서만 바꿔라. partition은 toYYYYMM(event_time) 또는 toDate(event_time) 중 하나를 골라라. 다른 소스, SQL 조건, 데이터 생성식, 엔진 설정, 빌드·실행 파일은 고치지 마라. 날짜별로 파티션을 쪼개면 day 조건으로 제외할 파티션은 늘지만 날짜 조건 없는 tenant 질의는 모든 파티션을 방문한다. 파트마다 인덱스와 그래뉼 경계가 새로 생기는 비용까지 예측한 뒤 월별 설정과 비교해라. 파티션을 잘게 나누면 모든 질의가 빨라진다고 일반화하지 마라.

[11.1] 일별 설정은 활성 파트가 32개여서 상한 2개를 넘는 탈락 대조군이다. 파티션 경계의 비용을 측정하려고 사용한다. 시작 코드의 관찰 뒤 order를 고정하고 월별·일별 설정을 각각 공개 관찰 도구로 실행해라. a의 tenant·day 읽기량, SelectedMarks, 활성 파트 수와 압축 비율을 나란히 기록해라. 읽기량 변화와 합격 여부를 구분해 설명한 뒤 월별 설정에서 정렬 순서를 고르고 읽기 예산을 검증해라. 이 비교 기록은 수동 학습 과제이며 자동 합격 판정은 [9]의 불변식으로 한다.

[12] 근거는 ClickHouse 공식 [MergeTree 문서](https://clickhouse.com/docs/engines/table-engines/mergetree-family/mergetree)의 정렬 키·희소 인덱스·그래뉼·파티션 설명, [HTTP 인터페이스](https://clickhouse.com/docs/interfaces/http)의 query_id 매개변수, [system.query_log](https://clickhouse.com/docs/operations/system-tables/query_log)의 read_rows·ProfileEvents·QueryFinish와 SYSTEM FLUSH LOGS, [system.parts](https://clickhouse.com/docs/operations/system-tables/parts)의 active·rows·압축 바이트 필드다. 실험은 고정된 ClickHouse 26.8 LTS 이미지와 공용 Kotlin 부하 도구를 사용한다. 기본 그래뉼 크기 8,192와 이 실험의 설정 4,096을 혼동하지 마라.
