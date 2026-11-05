# 병합 때만 지워지는 중복

[1] 이 단계는 observe 뒤 implement 순서다. 같은 업무 키의 갱신 행을 여러 번 삽입하고 물리 행 수와 최신 상태의 개수를 구별하라. 먼저 문항에 답하고 시작 코드를 실행한 다음 병합 시점에 관계없이 최신 상태를 집계하도록 SQL을 고쳐라.

[2] ReplacingMergeTree는 INSERT할 때 기존 행을 즉시 덮어쓰지 않는다. ORDER BY에 지정한 열이 모두 같은 행을 병합할 때 하나로 줄인다. 버전 열을 지정하면 가장 큰 버전의 행을 남긴다. 버전 열이 없으면 병합 대상 중 가장 최근 INSERT의 행을 남기며, 업무상 최신 상태라는 뜻은 아니다. 버전이 같고 값이 다른 행의 선택은 이 실험에서 다루지 않는다.

[3] 공식 근거는 [ReplacingMergeTree](https://clickhouse.com/docs/engines/table-engines/mergetree-family/replacingmergetree), [SYSTEM](https://clickhouse.com/docs/sql-reference/statements/system), [OPTIMIZE](https://clickhouse.com/docs/sql-reference/statements/optimize) 문서다. OPTIMIZE TABLE의 FINAL은 물리 병합을 요청하는 관리 명령이다. 매 조회 전에 OPTIMIZE를 실행하는 방식은 허용하지 않는다. 병합을 제어하는 명령과 현재 상태를 읽는 질의의 역할을 구별하라.

[4] 실험기는 고정 이미지의 ClickHouse를 한 번 띄우고 lab.events 테이블을 만든다. 열은 tenant UInt32, id UInt32, version UInt64, state String, amount Int64다. 엔진은 ReplacingMergeTree(version), 정렬 키는 (tenant, id)이며 명시적 파티션 키는 없다. 업무 키는 (tenant, id)이고 서로 다른 tenant에 같은 id가 있다. state는 active 또는 hold다. amount는 음수가 아닌 정수이며 최신 버전에서 줄어들 수도 있다. 같은 키·버전의 재전송은 모든 열이 동일하다.

[5] 공개 서비스는 PORT 환경 변수의 포트에 127.0.0.1로만 바인딩한다. CLICKHOUSE_URL은 실험기가 지정하는 ClickHouse HTTP 주소다. GET /health는 준비되면 HTTP 200을 반환한다. GET /summary?tenant=T&state=S는 T에 속한 각 업무 키의 최대 version 행을 고른 뒤 S가 active 또는 hold이면 해당 상태만, all이면 모든 상태를 집계한다. HTTP 200의 본문은 십진수 개수, 탭 한 글자, 십진수 amount 합계, 줄바꿈 한 글자다. amount 합계는 Int64 범위를 넘어도 오버플로 없이 정확한 십진수로 반환하라. 빈 결과는 0과 0이다. tenant는 1 이상 4294967295 이하 정수이고, state는 all·active·hold 중 하나다. 잘못된 인자는 HTTP 400, 존재하지 않는 경로는 HTTP 404다. 요청은 GET만 사용한다.

[6] 실험기는 자동 병합을 멈추고 동일 키의 여러 버전을 별도 INSERT로 삽입한다. 물리 행 수와 최신 상태의 개수를 관찰한 뒤 자동 병합을 재개해 OPTIMIZE TABLE lab.events FINAL을 실행하고 다시 측정한다. 학습자 서비스는 테이블을 만들거나 바꾸거나 병합하지 않고 SELECT만 실행한다. 세 구간의 물리 행 수와 서비스 응답을 비교하고, 응답이 [5]의 정의와 달라지는 원인을 설명하라.

[7] 작은 예를 직접 예측하라. 한 tenant에서 (id, version, state, amount)가 (1, 1, active, 100), (2, 1, hold, 40), (1, 3, hold, 70), (2, 3, active, 25), (1, 2, active, 150), (2, 2, hold, 90)인 여섯 행을 순서대로 별도 INSERT했다고 하자. 병합을 멈춘 상태의 일반 COUNT, 최신 상태의 all·active·hold 응답, 물리 병합 후 일반 COUNT를 각각 예측하라. 버전 열을 지정하지 않은 테이블에 같은 순서로 삽입하고 물리 병합했다면 all 집계가 어떻게 달라질지도 예측하라. 예측은 문항에 제출하고 해설과 대조하라.

[8] 시나리오 daily-summary는 tenant마다 6개 키, rolling-summary는 tenant마다 17개 키를 사용한다. tenant는 1·2·3이고 id는 1부터 시작한다. 초기 적재는 모든 키의 version 10, 30, 20, 30을 이 순서의 네 배치로 삽입한다. 마지막 배치는 version 30의 동일 재전송이다. 배치마다 INSERT 완료를 기다린 뒤 다음 배치를 넣는다. 자동 병합은 적재 전부터 멈춰 있다. version 10의 amount는 1000+tenant×100+id, version 20은 500+tenant×10+id, version 30은 tenant×100+id×3이다. version 30의 state는 (tenant+id)가 짝수면 active, 홀수면 hold이고, version 10·20의 state는 그 반대다.

[9] 측정은 적재 직후, 명시적 물리 병합 직후, 추가 갱신 직후의 세 구간이다. 병합 직후 자동 병합을 다시 멈추고 모든 키에 version 40과 과거 version 20을 순서대로 추가한다. version 40의 state는 version 30과 반대이며 amount는 tenant×10+id다. version 20은 초기 행과 같다. 삽입과 측정은 겹치지 않는다. 최신 값이 달라진 세 번째 구간도 새 데이터에 맞아야 하며 이전 결과를 재사용하면 안 된다.

[10] 각 구간은 초당 12건으로 2초 동안 총 24건을 예정된 시각에 보내는 개방형 부하다. 요청 조합은 tenant 1·2·3·4 각각과 state all·active·hold 각각을 두 번씩 조회한다. tenant 4는 빈 결과를 확인한다. 한 실행은 총 72건이다. 공통 load.main.kts의 steady와 runLoad를 사용한다. 각 HTTP 요청 제한 시간은 10초이며 응답이 늦어져도 다음 요청의 예정 시각을 늦추지 않는다. 처리량이나 지연 시간으로 합격을 판정하지 않는다.

[11] 모든 72건은 HTTP 200으로 완료되어야 하며 각 구간의 응답은 입력으로 독립 계산한 최신 상태의 개수와 합계에 정확히 일치해야 한다. 지표 requests는 72, http_errors는 0, wrong_before·wrong_after·wrong_refresh는 모두 0이어야 한다. wrong 값은 해당 구간에서 본문이 예상값과 다른 요청 수이며 HTTP 실패도 포함한다. p95_ms는 세 구간 응답 시간의 최근접 순위 95백분위수로 관찰용이다.

[12] N을 세 tenant의 전체 업무 키 수라 할 때 raw_before는 4N, final_before는 N, raw_after는 N, raw_refresh는 3N이어야 한다. 이 네 지표는 서비스가 아닌 실험기가 ClickHouse에 직접 질의한 COUNT다. raw는 일반 COUNT, final_before는 [5]에서 정의한 최신 행만 센 값이다. 따라서 두 시나리오에서 N은 각각 18과 51이다. 이 관계가 깨지면 관찰 환경이 성립하지 않은 실험 오류로 처리한다. 관계는 맞지만 서비스의 응답이 틀리면 불변식 실패다.

[13] 수정 범위는 starter/src/main/kotlin/SummaryQuery.kt의 SQL 생성뿐이다. Main.kt, ClickHouse.kt, 환경 변수, 테이블 구조, 실행·빌드 스크립트와 실험기는 바꾸지 마라. 집계를 Kotlin 메모리로 옮기거나 특정 시나리오의 결과를 상수로 반환하지 마라. 각 요청 시 ClickHouse의 현재 데이터를 질의하라. 추가 의존성을 설치하지 말고 JDK 21과 제공된 Kotlin 템플릿만 사용하라.

[14] 프로젝트에서 ./build.sh로 실행 파일을 만든 뒤 ./run.sh로 서비스를 시작한다. Docker Desktop이 실행 중이어야 한다. 훈련 도구의 실험 명령은 컨테이너·빈 포트·환경 변수·부하·정리를 모두 맡는다. 시작 코드는 빌드와 HTTP 응답에 성공하지만 병합 전 집계가 틀리게 작성되어 있다. 지표를 비교하고 SQL을 수정한 다음 두 시나리오를 다시 실행하라.

[15] HTTP 500과 http_errors만으로는 SQL 오류와 연결 실패를 구별할 수 없다. SQL 오류를 조사할 때는 학습자 프로젝트에서 아래 두 명령으로 실제 생성 SQL을 파일에 저장하라. 이어서 저장소 루트에서 마지막 명령을 실행하라. 공개 도구 diagnose.main.kts는 고정 이미지의 ClickHouse에 [4]의 스키마와 tenant 1의 [7] 입력을 준비하고 생성 SQL을 한 번 질의한다. 출력의 HTTP 상태와 ClickHouse 오류 본문을 확인하라. 환경 준비나 연결이 실패하면 표준 오류에 예외를 출력한다. 도구는 종료할 때 컨테이너와 볼륨을 지운다. 이 진단은 SQL 조사용이며 두 시나리오의 불변식 검증을 대신하지 않는다.

```sh
./build.sh
kotlin -classpath build/app.jar -e 'print(summaryQuery(1L, "active"))' > /tmp/cresvoid-summary.sql
kotlin content/tracks/olap-lab/stages/02-replacing-merge/tools/diagnose.main.kts /tmp/cresvoid-summary.sql
```
