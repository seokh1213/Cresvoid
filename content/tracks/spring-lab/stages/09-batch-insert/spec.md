# IDENTITY와 배치 삽입

[1] 먼저 시작 코드를 빌드하고 실험하여 IDENTITY 삽입의 JDBC 호출 수와 시간을 관찰해라. 그런 다음 삽입 경로를 구현하여 1만 건의 데이터를 적은 JDBC 호출로 저장해라. 이 단계는 고정된 IDENTITY 관찰 경로와 학습자가 바꿀 삽입 경로를 같은 Hibernate·H2 환경에서 비교한다. 실행 이미지의 spring 라이브러리 묶음을 사용하며 새 라이브러리를 추가하지 않는다.

[2] `build.sh`는 실행 이미지 안에서 `build/app.jar`를 만들고 `run.sh`는 `PORT` 환경 변수로 지정한 포트에서 HTTP 서비스를 시작한다. 호스트에는 루프백으로만 포트를 공개한다. 실험은 동일한 실행 이미지의 compose 앱을 임의의 호스트 포트로 띄우고 `GET /health`의 HTTP 200을 기다린다. DB는 실행마다 새로운 H2 메모리 DB다. HTTP 요청은 단일 작업 스레드가 처리한다.

[3] 공개 API는 `GET /health`, `POST /observe?run=<키>&count=<N>`, `POST /insert?run=<키>&count=<N>`, `GET /rows?run=<키>&count=<N>`이다. 키는 영문 소문자·숫자·하이픈 1~40자이며 N은 1~10000이다. POST 본문은 비워라. 유효하지 않은 키나 N에는 HTTP 400을 반환한다. 관찰 경로는 별도 `observed_row` 테이블을 사용한다. 삽입 경로는 `import_row` 테이블을 사용하고, 각 요청은 독립된 트랜잭션 하나로 처리한다.

[4] 정상 삽입은 입력의 각 행을 정확히 한 번 저장하고 HTTP 200을 반환해야 한다. 입력은 요청 키, 1부터 N까지의 순번, `value-<순번>` 문자열이다. ID는 DB/Hibernate가 생성하는 양수 Long이며 행마다 고유해야 한다. 연속 번호일 필요는 없다. `(run_key, ordinal_no)`는 유일해야 한다. `duplicate=true`를 삽입 URL에 붙이면 N행 뒤에 첫 행을 다시 넣는다. 이 요청은 HTTP 409로 끝나고 요청 전체가 롤백되어야 한다. 새 키라면 해당 키의 행이 하나도 남지 않아야 한다. 기존 성공 키를 재사용해 유일 제약을 위반한 요청도 HTTP 409로 끝나며 앞서 저장한 행을 그대로 보존해야 한다. 앞선 성공 요청의 행은 다른 키의 오류에도 보존해야 한다.

[5] 응답은 UTF-8 텍스트로 한 줄에 `이름=숫자` 하나씩 담는다. 정상 POST와 중복 오류 POST는 `rows`, `ids`, `ordinals`, `bad_rows`, `singles`, `batches`, `batch_rows`, `max_managed`, `elapsed_ms`를 반환한다. `rows`는 커밋 또는 롤백 뒤 DB에서 직접 읽은 행 수다. `ids`와 `ordinals`는 각각 서로 다른 ID와 순번의 수다. `bad_rows`는 ID가 양수가 아니거나 순번이 1..N 밖이거나 payload가 입력과 다른 행의 수다. `/rows`는 앞의 네 필드를 DB에서 다시 조회한다.

[6] `singles`는 INSERT용 PreparedStatement의 개별 execute·executeUpdate·executeLargeUpdate 호출 수다. `batches`는 같은 문장의 executeBatch·executeLargeBatch 호출 수이고 `batch_rows`는 addBatch 호출 수다. 시퀀스 조회·DDL·검증 SELECT는 세지 않는다. 이 값은 JDBC 프록시가 요청별로 직접 센다. `max_managed`는 `ImportSession.persist` 직후 Hibernate SessionStatistics의 entityCount를 읽은 최댓값이다. `elapsed_ms`는 트랜잭션과 사후 DB 조회를 포함한 서비스 처리 시간이다. HTTP 지연은 별도로 클라이언트에서 잰다.

[7] 두 시나리오는 각각 서비스를 한 번 띄운다. 먼저 `/insert`에 100행을 넣어 예열하고, 별도 키로 `/observe`에 10000행을 넣어 비교 기준을 얻는다. `bulk-window`는 서로 다른 키의 10000행 요청 두 개를 0ms와 250ms에 보낸다. `boundary-window`는 103행·17행·251행 요청을 0ms·250ms·500ms에 보낸다. 느린 응답을 기다려 다음 요청을 늦추지 않는 개방형 부하다. 각 HTTP 요청의 제한 시간은 30초다. 모든 측정 요청 뒤에는 `/rows`로 결과를 다시 읽는다.

[8] 모든 시나리오는 이어서 별도 키의 267행에 `duplicate=true`를 붙여 오류를 만들고, 새 키의 23행을 정상 삽입하여 복구를 확인한다. 중복 오류 전후로 앞선 성공 키를 다시 조회한다. 오류 요청을 제외한 예열·측정·복구 요청마다 `rows = ids = ordinals = N`, `bad_rows = 0`, `singles = 0`, `batch_rows = N`, `1 ≤ batches ≤ ceil(N / 25)`, `1 ≤ max_managed ≤ 200`을 만족해야 한다. 중복 오류 요청은 HTTP 409이며 DB 행 수가 0이어야 한다. 오류 요청의 배치 호출 수는 합격 기준으로 삼지 않는다.

[9] 고정 관찰 경로는 `rows = ids = ordinals = 10000`, `bad_rows = 0`, `singles = 10000`, `batches = batch_rows = 0`이어야 한다. 정상 삽입 요청의 INSERT 실행 호출 수 `singles + batches`는 관찰 경로와 달리 최대 ceil(N / 25)여야 한다. 이는 JDBC 실행 호출의 감소를 검증하며 H2에서의 네트워크 왕복 수나 운영 DB의 성능 배수를 뜻하지 않는다. 처리 시간과 HTTP p95는 관찰용으로만 보고하며 시간 비율로 합격을 판정하지 않는다.

[10] 실험은 `baseline_calls`(고정 관찰 경로의 실행 호출 수), `insert_calls`(정상 측정·복구 요청의 실행 호출 합), `batch_rows`(같은 요청의 addBatch 합), `max_managed`(같은 요청의 최댓값), `stored_rows`(같은 키들의 최종 행 수 합), `errors`(기대한 HTTP 상태나 응답 형식을 벗어난 횟수), `violations`(어긴 검사 수), `import_p95_ms`(정상 측정·복구 요청의 HTTP 지연 p95), `first_failure_rule`, `first_failure_n`을 출력한다. 예열은 불변식을 검사하되 집계 지표에서는 제외한다. 예상한 중복 오류도 집계에서 제외한다. 합격 조건은 errors와 violations가 모두 0이고 [4], [8], [9]의 조건을 모두 만족하는 것이다.

[10.1] `first_failure_rule`은 실험이 처음 발견한 실패의 규칙 코드다. 0은 실패 없음, 1은 HTTP 상태·통신·응답 형식, 2는 rows, 3은 ids, 4는 ordinals, 5는 bad_rows, 6은 singles, 7은 batch_rows, 8은 batches, 9는 max_managed, 10은 elapsed_ms 조건 위반이다. `elapsed_ms`는 0 이상의 유한한 숫자여야 하며 상한은 없다. 유한한 숫자가 아니면 응답 형식 오류인 1, 음수이면 10으로 판정한다. `first_failure_n`은 그 요청의 N이며, 실패가 없거나 health 검사라면 0이다. 예열·관찰·중복 오류·재조회 실패도 이 두 지표에 포함한다. 검사는 [7], [8]의 순서로 수행하며 동시 전송한 요청은 전송 예정 순서로 검사한다. 응답 형식을 먼저 확인한 뒤 rows, ids, ordinals, bad_rows 순으로 검사하고, 정상 삽입은 singles, batch_rows, batches, max_managed, elapsed_ms 순으로 이어서 검사한다. 관찰 요청은 singles, batches, batch_rows, elapsed_ms 순으로 이어서 검사한다. 집계 호출 수가 작아도 요청별 상한 위반을 상쇄하지 못한다.

[11] 바꿀 수 있는 파일은 `src/main/kotlin/lab/ImportRow.kt`와 `Importer.kt`뿐이다. ImportRow의 테이블·컬럼·유일 제약과 데이터 의미는 유지하고 ID 생성 매핑은 바꿔도 된다. Importer는 주어진 모든 입력을 `ImportSession.persist`로 저장하고 `flush`, `clear`로 컨텍스트를 관리해라. 외부 DB·직접 JDBC·StatelessSession·새 스레드는 사용하지 않는다. ImportSession은 트랜잭션을 시작하거나 커밋하지 않는다. commit과 rollback은 바깥 서비스가 소유한다. 공통 측정 코드·관찰 엔티티·HTTP 계약·빌드 파일을 바꾸거나 지표를 상수로 대체하지 마라.

[12] 예측 문항에 답한 뒤 시작 코드의 측정값을 관찰하고 수정해라. ID 생성과 세션 동작을 확인할 자료는 [Hibernate 7.4 User Guide, Session batching](https://docs.hibernate.org/orm/7.4/userguide/html_single/#batch-session-batch), [Batch inserts](https://docs.hibernate.org/orm/7.4/userguide/html_single/#batch-session-batch-insert), [Using sequences](https://docs.hibernate.org/orm/7.4/userguide/html_single/#identifiers-generators-sequence), [Flushing](https://docs.hibernate.org/orm/7.4/userguide/html_single/#flushing)이다.
