# 데이터 쏠림과 적응 실행

[1] 거래 행을 작은 요율표와 조인하는 Spark 작업을 개선해라. 키 0에 거래의 75%가 몰려 있다. 결과를 유지하면서 가장 느린 태스크에 작업이 몰리는 현상을 줄여라. 실행 시간만 보지 말고 파티션별 행 수, 태스크 시간 분포, 실제 실행 계획을 함께 관찰해라.

[2] JDK 21, Kotlin과 실행 이미지의 spark 묶음(Spark SQL 4.3.0)을 사용해라. `.lab-libs`는 spark다. `build.sh`로 빌드하고 `PORT=18080 ./run.sh`로 서비스를 실행해라. 호스트에는 루프백 포트만 공개한다. SparkSession은 local[2]이며 UI는 끈다. 실험기는 같은 고정 이미지의 app 서비스를 compose로 한 번 띄운 뒤 모든 요청을 보내고 컨테이너를 정리한다. compose 정의는 실험기가 임시 파일로 생성한다. 추가 도구나 라이브러리는 설치하지 마라.

[3] 공개 서비스 계약은 GET /health와 POST /run?profile=ledger&mode=off다. health는 준비가 끝난 뒤 200과 ready를 반환한다. profile은 ledger, replay이고 mode는 off, on, factor, bytes, tuned다. 실행 요청은 한 작업씩 처리하며 각 요청은 새 데이터와 새 계획을 만든다. 같은 JVM의 직전 설정이 다음 실행에 섞이지 않게 초기화한다. 잘못된 메서드나 profile, mode에는 400을 반환하고 Spark 작업 실패에는 500을 반환한다.

[4] 거래에는 id 0~4095가 한 번씩 있다. ledger는 id < 3072가 키 0이고 replay는 id % 4 != 0이 키 0이다. 나머지 키는 1 + id % 127이다. amount는 id % 17 + 1이다. 거래의 입력 파티션은 16개다. 요율표는 키 0~127마다 weight = key + 3인 행 하나와 key = 0, weight = 7인 추가 행 하나로 구성한다. 내부 동등 조인으로 id, weight, amount를 출력해라. 키 0의 각 거래는 두 행이 된다. 두 profile 모두 결과는 7168행이다. 중복 제거, 누락, 잘못된 weight를 허용하지 않는다.

[5] 먼저 off 실행을 관찰한 뒤 on, factor, bytes를 비교하고 마지막에 tuned를 실행해라. off는 AQE를 끄고 정렬 병합 조인을 쓴다. 나머지 관찰 모드는 AQE와 skewJoin을 켜고 정렬 병합 조인을 쓴다. 자동 브로드캐스트는 모두 끄고 셔플 파티션 수는 16, 셔플 압축과 AQE 파티션 병합도 끈다. on의 쏠림 배수는 3, 절대 임계값은 8192바이트, 권장 분할 크기는 4096바이트다. factor는 배수만 10000으로, bytes는 절대 임계값만 1073741824바이트로 바꾼다. forceOptimizeSkewedJoin은 켠다. 이 수치는 작은 데이터로 분할을 관찰하려고 낮춘 실험값이며 운영 권장값이 아니다.

[6] 쏠림 파티션은 중앙값에 배수를 곱한 값과 절대 임계값을 모두 넘어야 한다. AQE를 켰다는 사실만으로 항상 분할되지는 않는다. on에서 최종 계획의 skew=true와 최종 파티션 수 증가를 확인해라. factor와 bytes에서는 분할이 막히며 최종 파티션 수가 off와 같아야 한다. 관찰한 행 분포와 실행 계획을 근거로 tuned의 조인 전략과 파티션 구성을 정해라.

[7] 성공한 실행은 줄마다 name=value인 본문을 반환한다. rows는 전체 결과 행 수다. digest는 모든 결과를 id:weight:amount 문자열로 바꾸고 사전순으로 정렬한 뒤 개행으로 연결한 UTF-8 바이트의 SHA-256 소문자 16진수다. 끝 개행은 넣지 않는다. partitions는 결과 소비 태스크 수이고 partition_rows와 task_ms는 같은 태스크 순서의 쉼표 구분 목록이다. 빈 파티션도 포함한다. shuffle_partitions는 셔플을 읽는 스테이지의 태스크 수 중 최댓값이며 없으면 0이다. adaptive와 skewed는 완료된 실행 계획에서 얻은 불리언이다. plan_base64를 Base64로 디코딩하면 UTF-8 문자열로 된 최종 실행 계획을 읽을 수 있다.

[8] 고정 소비기는 조인 결과를 파티션별로 읽은 뒤 출력 행당 1밀리초의 소비 비용을 부여한다. task_ms는 SparkListenerTaskEnd의 성공 태스크 duration으로 측정하며 이 소비 태스크만 비교한다. 작업량 비율은 최대 partition_rows / max(1, 중앙값), 시간 비율은 최대 task_ms / max(250, 중앙값)이다. 중앙값은 오름차순 목록의 ceil(개수 × 0.5)번째 값이다. 두 tuned 실행의 작업량 비율은 각각 6 이하여야 한다. 시간 비율은 각각 12 이하여야 한다. 250밀리초 바닥값은 빈 태스크와 측정 잡음의 영향을 줄인다. 작업량 조건도 함께 검사하므로 느린 환경에서 쏠림이 가려져도 합격하지 못한다.

[9] daily-join 실험은 ledger의 off, on, factor, bytes, tuned와 replay의 tuned 총 6개 요청을 시작 후 0, 100, 200, 300, 400, 500밀리초에 예정해서 보낸다. 앞 요청의 응답과 관계없이 요청을 발송하는 개방형 부하다. 각 요청의 제한 시간은 180초이고 서비스는 요청을 직렬로 처리한다. 6개 모두 HTTP 200이어야 하며 결과 행 수와 digest가 독립적으로 계산한 기준 결과와 같아야 한다. 관찰 모드의 분할 관계도 [6]을 만족해야 한다. tuned는 두 profile에서 결과 소비 파티션 수와 셔플 읽기 스테이지의 최대 태스크 수가 모두 32 이하여야 한다.

[10] 실험기는 completed, incorrect_results, baseline_work_ratio, baseline_task_ratio, tuned_work_ratio, tuned_task_ratio, tuned_partitions, tuned_shuffle_partitions, adaptive_partitions, control_checks를 출력한다. baseline 지표는 ledger의 off 실행에서 얻고 adaptive_partitions는 ledger의 on 실행에서 얻는다. tuned로 시작하는 지표는 두 tuned 실행의 최댓값이다. tuned_partitions는 결과 소비 파티션 수이고 tuned_shuffle_partitions는 셔플 읽기 스테이지의 최대 태스크 수다. completed는 6, incorrect_results는 0, control_checks는 1이어야 한다. 나머지 합격 조건은 [8], [9]를 따라라. 파티션만 많이 늘려 시간 분포를 평평하게 만들면 자원 상한에서 실패한다. 전체 실험 제한 시간은 240초이고 참조 풀이 검증은 같은 시나리오를 3회 반복한다.

[11] 수정 범위는 src/main/kotlin/lab/Tuning.kt다. 조인 전략과 AQE 설정, 소금 열 추가와 제거, 조인 전후 파티션 구성은 바꿔도 된다. Fixture.kt의 입력과 관찰 모드, Main.kt의 HTTP 계약, Measurement.kt의 소비 비용·측정·digest, 실행 스크립트와 .lab-libs는 바꾸지 마라. 드라이버에서 조인 결과를 직접 만들거나 측정값을 위조하지 마라. 순서는 달라도 결과 다중집합은 같아야 한다.

[12] 공식 근거는 [Spark Performance Tuning의 Adaptive Query Execution과 Join Strategy Hints](https://spark.apache.org/docs/4.3.0/sql-performance-tuning.html), [SparkListenerTaskEnd API](https://spark.apache.org/docs/4.3.0/api/java/org/apache/spark/scheduler/SparkListenerTaskEnd.html), [TaskInfo API의 duration](https://spark.apache.org/docs/4.3.0/api/java/org/apache/spark/scheduler/TaskInfo.html)이다. 문서의 쏠림 조건은 배수와 절대 크기의 논리곱이며 AQE가 실제 셔플 통계를 보고 분할한다는 점을 확인해라. 태스크 시간에는 대기와 실행 환경의 영향이 있으므로 작업량 지표도 함께 읽어라.
