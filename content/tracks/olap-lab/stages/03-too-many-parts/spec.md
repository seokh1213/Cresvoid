# 작은 삽입과 파트 폭증

[1] 분석 이벤트 수집기의 저장 방식을 조정하라. 먼저 작은 요청을 그대로 삽입하는 시작 코드를 실행하고 요청 오류, 활성 파트 수, 실제 저장 행 수를 관찰하라. 같은 입력을 유지하면서 저장 방식을 고친 뒤 다시 실행하라. 이 단계는 배치 삽입과 flush의 입문 실험이다. 목표는 삽입 오류 없이 모든 행을 보존하면서 파트 수를 제한하는 것이다.

[2] MergeTree는 동기 삽입의 블록을 정렬하고 디스크 파트로 기록한다. 서로 다른 INSERT는 기존 파트에 행을 덧붙이지 않는다. 백그라운드 병합은 파트를 합치지만 삽입 속도를 항상 따라간다고 보장하지 않는다. 작은 요청이 많은 수집기는 여러 요청의 행을 모아 삽입할 수 있다. 이때 요청 접수와 디스크 저장 완료의 의미를 구분하고, 마지막에 남은 행도 처리해야 한다.

[3] 실행기는 매 실험마다 고정 이미지의 ClickHouse 26.8 LTS와 빈 `default.events` 테이블을 준비한다. 스키마는 `(id UInt64, value UInt64) ENGINE = MergeTree ORDER BY id`이며 파티션 키는 없다. `parts_to_delay_insert=48`, `parts_to_throw_insert=48`, `max_parts_in_total=96`, `max_avg_part_size_for_too_many_parts=1073741824`를 명시한다. 작은 파트가 파티션당 48개에 이르면 다음 삽입은 거절된다. 이는 이 실험의 설정이며 운영 권장값이 아니다. 실험기는 부하 전에 `SYSTEM STOP MERGES default.events`를 실행하고 종료까지 병합을 멈춘다. 병합이 따라오지 못하는 구간을 기계 속도와 무관하게 재현하기 위한 장치다.

[4] `build.sh`로 빌드하고 `run.sh`로 HTTP 서비스를 실행하라. 서비스는 `PORT` 환경 변수의 `127.0.0.1` 포트에 바인딩해야 한다. `CLICKHOUSE_URL`은 HTTP 원점 주소, `CLICKHOUSE_TABLE`은 `default.events`로 전달된다. `GET /health`는 HTTP 200과 `ready`를 반환한다. 서비스가 준비되기 전에 테이블에 행을 넣지 마라. 실험기는 서비스와 ClickHouse를 시작하고 준비를 확인하며 종료 때 모두 정리한다.

[5] `POST /events`는 UTF-8 TSV 본문을 받는다. 각 행은 `id`, 탭, `value` 순서이며 두 값은 모두 64비트 부호 있는 정수 범위의 양수다. 한 요청에는 한 행 또는 두 행이 있다. 마지막 줄바꿈은 있어도 된다. 정상 요청은 HTTP 202와 `accepted`를 반환한다. 이 응답은 프로세스 내부 접수를 뜻하며 아직 ClickHouse에 저장되지 않아도 된다. 잘못된 입력은 HTTP 400, ClickHouse 삽입 실패는 HTTP 503으로 응답하라. 입력 검증을 통과한 행은 삽입을 시도하기 전에 모두 보관하라. 삽입이 저장 전에 거절되면 503 요청의 신규 행과 이미 202로 접수한 미저장 행을 모두 보존하고 이후 flush에 포함하라. 400 요청의 행은 보관하지 마라. 이 실험은 유효하고 서로 다른 id만 보내며 요청을 재시도하지 않는다.

[6] 빈 본문의 `POST /flush`는 앞서 접수한 모든 행의 ClickHouse 저장을 완료한 뒤 HTTP 200과 `flushed`를 반환해야 한다. 요청들이 모두 응답한 뒤에만 flush를 호출하므로 진행 중인 접수와의 경합은 없다. 반복 flush는 행이나 파트를 추가하지 않아야 한다. 저장 오류는 HTTP 503으로 응답하라. 저장 전에 거절된 flush의 미저장 행도 보존하고 다음 flush에서 저장하라. 여러 삽입 중 일부가 성공했다면 성공이 확인된 행은 다시 삽입하지 마라. 저장 여부를 알 수 없는 통신 실패에도 미확인 행을 버리지 말고 503으로 응답하되, 이후 flush의 중복 없는 저장은 보장 범위에서 제외한다. 실험 중 프로세스 장애 복구와 재전송의 중복 제거는 다루지 않는다.

[7] 두 시나리오는 각각 총 103개 요청, 155행을 보낸다. 요청 번호는 0부터 102까지다. 짝수 번호의 요청에는 두 행, 홀수 번호에는 한 행을 넣는다. id는 1부터 155까지 순서대로 배정하고 value는 `id * 17 + 3`으로 정한다. `weekday-flow`는 103개 요청을 한 구간으로 보내고 `evening-flow`는 앞의 60개와 뒤의 43개 요청을 두 구간으로 보낸다. 각 구간은 이전 요청의 완료 여부와 무관하게 초당 40개, 25ms 간격으로 도착시킨다. 각 구간의 요청이 모두 완료되면 flush, 저장 검사, 다시 flush, 저장 검사를 수행한다. 다음 구간은 이 검사가 끝난 뒤 시작한다.

[8] 지표는 `requests`(보낸 접수 요청 수), `request_errors`(202와 `accepted`가 아니거나 통신이 실패한 접수 요청 수), `flush_errors`(200과 `flushed`가 아니거나 통신이 실패한 flush 수), `peak_parts`(각 저장 검사에서 조회한 활성 파트 수의 최댓값), `stored_rows`(마지막 검사에서 읽은 행 수), `row_mismatches`(모든 저장 검사에서 기대한 행의 다중집합과 실제 행의 다중집합 사이 개수 차이의 절댓값 합을 누적한 값), `duplicate_rows`(마지막 저장 행 수에서 서로 다른 id 수를 뺀 값), `repeat_parts_growth`(각 구간의 첫 flush 직후와 반복 flush 직후 파트 수 차이의 절댓값 합), `p95_ms`(접수 요청 응답 시간의 최근접 순위 95백분위수)다. 파트는 `system.parts`에서 database, table, active로 필터링해 세고 행은 테이블을 직접 조회한다. 병합과 삭제가 없으므로 최종 파트 수가 실행 중 최대 파트 수도 나타낸다.

[9] 합격하려면 requests=103, request_errors=0, flush_errors=0, peak_parts≤8, stored_rows=155, row_mismatches=0, duplicate_rows=0, repeat_parts_growth=0을 모두 만족해야 한다. 각 구간의 flush 직후에도 그때까지 보낸 모든 행이 정확히 한 번씩 원래 값으로 존재해야 한다. 접수·flush 요청의 개별 통신 제한은 15초이며 시간 초과는 해당 오류 수에 포함한다. p95_ms는 관찰용이며 합격 임계값은 없다. 파트 제한만 맞추고 잔여 행을 버리거나 모든 오류를 성공으로 응답해서는 통과할 수 없다.

[10] 수정 대상은 `src/main/kotlin/Ingestor.kt`다. HTTP 계약, 행 검증, ClickHouse 클라이언트, 빌드 파일은 유지하라. 클라이언트의 동기 삽입을 사용해 메모리에서 행을 묶는 정책을 조정하라. 비동기 삽입 설정, 테이블 구조나 설정 변경, 병합 재개, OPTIMIZE, 삭제, 중복 삽입, 가짜 통계 반환은 허용하지 않는다. 새 의존성을 추가하지 마라. 실험 데이터는 작으므로 이 단계에서는 디스크 큐나 주기적인 백그라운드 flush가 필요하지 않다.

[11] 동기 삽입과 클라이언트 묶음 삽입의 근거는 [ClickHouse 삽입 전략 문서](https://clickhouse.com/docs/best-practices/selecting-an-insert-strategy)다. 26.8에서 쓰는 파트 제한 설정과 비교 조건은 [26.8 MergeTreeSettings.cpp](https://github.com/ClickHouse/ClickHouse/blob/26.8/src/Storages/MergeTree/MergeTreeSettings.cpp)와 [26.8 MergeTreeData.cpp의 delayInsertOrThrowIfNeeded](https://github.com/ClickHouse/ClickHouse/blob/26.8/src/Storages/MergeTree/MergeTreeData.cpp)로 확인하라. 파트 관찰에는 [system.parts 문서](https://clickhouse.com/docs/operations/system-tables/parts)를 참고하라. 운영에서는 병합을 멈추지 않으며 실제 배치 크기와 flush 주기는 처리량, 지연 허용치, 장애 복구 요구를 함께 고려해 정한다.

[12] 삽입 거절의 진단 정보는 서비스의 503 응답 헤더에 있다. `X-Storage-Http-Status`는 ClickHouse의 HTTP 상태, `X-Storage-Error-Code`는 DB 오류 코드다. DB 코드가 없으면 0이며, 응답을 받지 못한 통신 실패에는 두 헤더가 없다. 본문은 기존대로 `storage unavailable`이며 DB 오류 본문은 노출하지 않는다. 수동 재현 시 [3]의 빈 테이블과 병합 중단 조건으로 시작 코드를 [4]대로 실행하라. 서로 다른 id의 한 행 요청을 49개 순서대로 보내고 각 응답 헤더를 확인하라. 49번째는 서비스 503과 DB 오류 코드 252(TOO_MANY_PARTS)를 반환한다. 같은 방법으로 수정본을 비교하라. 부하 도구는 추가하지 말고 JDK HTTP 클라이언트나 `curl -i`로 응답 헤더를 확인하라. 자동 실험의 지표는 [8]을 따른다.
