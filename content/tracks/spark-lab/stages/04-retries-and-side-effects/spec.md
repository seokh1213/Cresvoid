# 재실행과 부수효과

[1] 이 단계는 짧은 관찰 입문 실험이다. 실행 전에 변환 누산기, 행동 누산기, 실패 뒤 누산기, 외부 파일의 행 수, 클로저가 바꾼 드라이버 변수의 값을 예측해라. 시작 코드로 관찰한 뒤 외부 공개 기록만 수정하고 같은 실험을 다시 실행해라. 실행기는 Spark SQL 4.3.0 묶음과 JDK 21을 사용하며 SparkSession은 local[2]로 고정한다.

[2] local[2]의 태스크 실패 허용 횟수는 1이다. 이 단계는 태스크 자동 재시도 대신 실패한 행동을 드라이버가 한 번 다시 제출한다. 두 제출의 TaskContext.attemptNumber는 모두 0이며, 이 값을 태스크 재시도 횟수로 해석하지 마라. 같은 입력과 같은 외부 저장소를 다시 사용하는 상황에서 부수효과가 남는지 관찰해라. local[2,2]나 별도 클러스터로 바꾸지 마라.

[3] 먼저 0부터 N-1까지의 정수를 단일 파티션 RDD로 만든다. map은 행마다 변환 누산기를 1 늘리고 직렬화 가능한 드라이버 객체의 value도 1 늘린다. 이 RDD를 캐시하지 않고 count를 한 번, foreach를 한 번 실행한다. foreach는 별도의 행동 누산기를 행마다 1 늘린다. count는 N, 변환 누산기는 2N, 행동 누산기는 N, 드라이버의 원본 value는 0이어야 한다. 두 행동 사이의 재계산을 단일 행동 내부의 중복 반영 보장과 구분해라.

[4] 다음으로 원본 RDD의 foreachPartition에서 행마다 raw.log에 ID를 한 줄 쓰고 publishRecord를 호출한 뒤 복구 행동 누산기를 1 늘린다. 첫 제출은 K번째 행까지 외부 쓰기를 마친 직후 의도한 예외를 던진다. 실패한 잡이 끝난 뒤 같은 RDD를 다시 제출하며 두 번째 제출에는 예외를 넣지 않는다. 실패 잡은 정확히 1개, 제출은 정확히 2회, 실패 직후 복구 행동 누산기는 0, 두 번째 제출 완료 뒤 값은 N이어야 한다. raw.log의 쓰기는 롤백되지 않으므로 0부터 K-1까지의 ID 뒤에 0부터 N-1까지의 ID가 이어져 총 N+K행이어야 한다.

[5] 공개 기록은 같은 논리 ID를 다시 처리해도 한 번만 남아야 한다. 공개 기록을 readPublished로 읽었을 때 행 수는 N이고 ID 집합은 정확히 0부터 N-1까지여야 한다. 중복·누락·범위 밖 ID는 없어야 한다. 태스크 번호나 제출 번호는 논리 ID를 대체하지 못한다. raw.log와 공개 기록은 서로 다른 저장소다. raw.log를 지워서 중복을 숨기거나 readPublished에서 distinct로 감추지 마라. readPublished는 저장된 공개 기록 전체를 읽어야 한다. 이 단계의 저장소는 같은 JVM의 로컬 파일시스템이다. 저장 형식과 중복 방지 수단은 직접 선택해라. 파일을 쓰는 도중 프로세스가 중단되는 경우나 여러 호스트의 공유 저장소 원자성은 다루지 않는다.

[6] 마지막으로 4개 파티션의 행동을 실행하고 마지막 파티션에만 500ms 지연을 넣는다. spark.speculation=true, interval=100ms, quantile=0.5, multiplier=1.5, minTaskRuntime=100ms, efficiency.enabled=false로 설정한다. 느린 파티션은 1개여야 한다. SparkListenerTaskStart의 taskInfo.speculative로 실제 추가 실행 수를 센다. 로컬 실행에는 다른 호스트가 없으므로 이 실험의 speculative_tasks는 0이어야 한다. 설정이 켜졌다는 사실을 투기 태스크가 실행됐다는 증거로 쓰지 마라. 여러 호스트에서 투기 실행이 실제 발생했을 때의 외부 중복 수는 이 단계에서 측정하지 않는다.

[7] 서비스는 PORT 환경 변수의 포트를 사용한다. 실험 환경은 컨테이너 내부 포트를 호스트의 빈 루프백 포트로 연결한다. GET /health는 Spark 세션 준비 뒤 HTTP 200과 ready를 반환한다. POST /run?size=N&cut=K는 HTTP 200과 UTF-8 key=value 행을 반환한다. N은 2 이상 100 이하, K는 1 이상 N 미만이다. 잘못된 인자는 400, /run의 다른 메서드는 405다. 요청마다 새 임시 저장소를 만들고 응답을 만든 뒤 지운다. 요청 간 관찰 상태는 공유하지 않는다.

[8] 응답의 숫자 필드는 counted, transformation_updates, action_updates, failed_action_updates, recovered_updates, driver_value, failed_jobs, submissions, slow_partitions, speculative_tasks다. speculation_enabled는 true다. task_attempts는 두 외부 쓰기 태스크의 attemptNumber를 쉼표로 이은 0,0이다. raw와 published는 실제 파일에서 읽은 ID들을 쉼표로 잇는다. raw는 쓰기 순서를 보존하며 published의 순서는 판정하지 않는다. 숫자 필드와 실제 파일 읽기를 예상 상수로 바꾸지 마라.

[9] sample-round는 N=12, K=4이고 expanded-round는 N=20, K=7이다. 서비스가 준비되면 개방형 부하 도구가 예정 시각 0ms에 POST 한 건을 보낸다. 이 단계는 처리량 대신 Spark 행동과 외부 쓰기의 관계를 측정한다. 요청 제한은 90초, 준비 제한은 100초, 실험 전체 제한은 240초다. 500ms 지연의 실제 경과 시간은 합격 기준으로 쓰지 않는다. 각 시나리오는 새 서비스로 세 번 실행한다.

[10] 실험기는 transformation_updates, action_updates, failed_action_updates, recovered_updates, driver_value, raw_rows, published_rows, publication_errors, speculative_tasks, observation_errors의 열 가지 지표를 출력한다. publication_errors는 누락 ID 수, 범위 밖 ID 수, 중복 초과 행 수를 더한 값이다. observation_errors는 [3], [4], [6], [8]의 관측 계약 중 어긴 항목 수다. raw_rows=N+K, published_rows=N, publication_errors=0, observation_errors=0이어야 한다. 모든 요청은 HTTP 200이어야 하며 나머지 관측값도 해당 문단과 일치해야 통과한다. 누산기 값과 파일 행 수를 비교하고 실패한 행동의 누산기가 외부 파일을 롤백해 주는지 설명해라.

[11] 수정 범위는 src/main/kotlin/lab/Publication.kt의 publishRecord와 readPublished다. 시작 코드는 빌드되지만 공개 기록 불변식을 어긴다. 논리 ID를 기준으로 외부 공개 기록을 남기도록 수정해라. Main.kt, Observation.kt, ExternalWrite.kt, 입력, 실패 위치, 누산기, 투기 설정, 실험기, 빌드·실행 스크립트는 바꾸지 마라. .lab-libs의 spark 묶음과 기존 템플릿을 사용하고 새 라이브러리를 추가하지 마라. build.sh로 빌드하고 run.sh로 실행해라.

[12] 공식 근거는 Spark 4.3.0 [RDD Programming Guide의 Accumulators](https://spark.apache.org/docs/4.3.0/rdd-programming-guide.html#accumulators), [Understanding closures](https://spark.apache.org/docs/4.3.0/rdd-programming-guide.html#understanding-closures), [Configuration의 speculation 설정](https://spark.apache.org/docs/4.3.0/configuration.html#scheduling)이다. 행동 안의 누산기 갱신에 관한 보장을 외부 쓰기의 정확히 한 번 처리 보장으로 확대하지 마라. 변환은 재계산될 수 있으며 클로저의 사본을 바꿔 드라이버 원본을 갱신하는 방식에 의존하지 마라. 드라이버 값 0은 제공 코드의 관측 계약이다. 공식 문서는 클로저 밖 상태 변경을 보장하지 않으며 일부 로컬 코드가 원본을 바꾸는 경우도 설명한다.

[13] 로컬 스케줄러의 실패 허용 횟수는 Spark 4.3.0 [SparkContext.createTaskScheduler](https://github.com/apache/spark/blob/v4.3.0/core/src/main/scala/org/apache/spark/SparkContext.scala), 같은 호스트에 투기 사본을 배치하지 않는 조건은 [TaskSetManager의 dequeueTaskFromList와 hasAttemptOnHost](https://github.com/apache/spark/blob/v4.3.0/core/src/main/scala/org/apache/spark/scheduler/TaskSetManager.scala)로 확인해라. 이 제약 때문에 관측된 잡 재제출을 자동 태스크 재시도라고 부르지 마라.

[14] observation_errors가 0이 아니면 /run 원본 응답으로 위반 항목을 찾아라. 학습자 작업 디렉터리의 첫 터미널에서 아래 명령으로 서비스를 실행해라. 18084가 사용 중이면 빈 포트로 바꾸고 두 번째 터미널의 URL에도 같은 포트를 써라. 준비 중에는 /health 연결이 실패할 수 있으므로 ready가 나올 때까지 확인한 뒤 /run을 호출해라. sample-round는 size=12&cut=4, expanded-round는 size=20&cut=7을 사용해라. 진단을 마치면 첫 터미널에서 Ctrl+C로 서버를 종료해라.

```sh
./build.sh
PORT=18084 ./run.sh
```

```sh
curl --fail --max-time 5 http://127.0.0.1:18084/health
curl --fail --max-time 90 -X POST 'http://127.0.0.1:18084/run?size=12&cut=4'
```

[15] 원본 응답은 다음 순서로 대조해라. 먼저 counted=N, transformation_updates=2N, action_updates=N, driver_value=0으로 [3]의 재계산과 클로저를 확인해라. 이어 failed_jobs=1, submissions=2, task_attempts=0,0, failed_action_updates=0, recovered_updates=N으로 실패와 재제출을 확인해라. raw가 0부터 K-1까지, 이어서 0부터 N-1까지인지 순서까지 대조해라. 마지막으로 slow_partitions=1, speculation_enabled=true, speculative_tasks=0을 확인해라. 이 열세 항목 중 불일치한 항목 수가 observation_errors다. 공개 기록은 별도로 published의 전체 행 수와 누락·범위 밖·중복을 확인해라. 원본 응답 진단은 [9]의 새 서비스 세 번 검증을 대신하지 않는다.
