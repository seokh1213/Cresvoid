# 깊은 페이지 넘기기

[1] 상품 500,000개를 다른 색인 작업과 동시에 내보내라. 내보내기 시작 시 검색에 보이던 상품의 ID, revision, payload를 정확히 한 번씩 출력하라. 시작 뒤 추가한 상품은 제외하고, 시작 뒤 수정하거나 삭제한 상품도 시작 시점의 값으로 출력하라. 여기서 시작 시점은 첫 검색 결과를 얻기 전이며, 실험기는 첫 검색 응답이 확정된 뒤에 변경 작업을 시작한다. 이 단계는 implement 모드다.

[2] 먼저 `./build.sh`로 시작 코드를 빌드하고 실험을 실행해 `missing`, `search_requests`, `max_window`를 관찰하라. 시작 코드가 몇 페이지까지 읽는지 HTTP 오류와 함께 확인하라. 서비스는 안전한 오류 요약을 stderr와 작업 공간의 `build/export-errors.log`에 남긴다. 실험 뒤 작업 공간에서 `cat build/export-errors.log`로 HTTP 상태를 확인하라. 로그는 서버를 시작할 때 비운다. 그다음 정렬값이 같은 문서, 페이지 사이 refresh, 수정·삭제·추가가 내보내기에 주는 영향을 구분해서 구현하라. 첫 관찰을 마친 뒤 막히면 이 문서 옆의 [도움말](help.md)을 읽어라. 마지막에는 전체 실험을 다시 실행해 모든 불변식을 확인하라. 실험은 Docker Desktop이 켜진 상태에서 Cresvoid의 lab 실행으로 수행한다.

[3] 서비스는 JDK 21의 HTTP 서버로 동작한다. `run.sh`는 `PORT` 환경 변수의 루프백 주소에서 서버를 시작하며 `GET /health`는 HTTP 200과 `OK`를 반환한다. `ES_URL`은 실험기가 제공하는 Elasticsearch HTTP 주소이고 `ES_INDEX`는 상품 색인 이름이다. `GET /export`는 HTTP 200으로 UTF-8 TSV 스트림을 반환한다. 한 줄은 `id<TAB>revision<TAB>payload<LF>`다. 헤더·요약·빈 줄을 넣지 마라. 내보내기 순서는 자유이며 한 실험은 내보내기 요청 하나만 보낸다. 실패를 정상 완료로 바꾸지 마라. 제공된 서비스 틀은 실패 시 `ERROR` 줄을 출력하므로 이를 관찰할 수 있다.

[4] 상품 필드는 숫자 `id`, 숫자 `rank`, 숫자 `revision`, 문자열 `payload`다. 초기 ID는 0 이상 500,000 미만이고 revision은 0이며 rank는 ID를 10으로 정수 나눈 값이다. payload는 `product-<id>-` 뒤에 소문자 x 256개를 붙인 문자열이다. 예를 들어 ID 42의 줄은 `42<TAB>0<TAB>product-42-` 뒤에 x 256개와 줄바꿈이 온다. rank가 같아도 상품은 서로 다르다. 실제 검색 응답의 `_source`를 읽어 출력하라. ID 범위나 payload 생성 규칙으로 결과를 합성하지 마라.

[5] `catalog-export` 시나리오는 주 샤드 3개, 복제본 0개의 색인에 초기 상품을 적재하고 refresh한 뒤 서비스를 시작한다. 1번째, 100번째, 200번째 검색 응답이 확정된 직후, 응답을 서비스로 넘기기 전에 변경 구간을 실행한다. 각 구간은 예정 시각 0·250·500·750ms에 응답 속도와 독립적으로 bulk 요청 네 개를 보내고 모두 끝날 때까지 해당 검색 응답을 보류한다. 각 bulk는 기존 상품 250개의 rank를 -1, revision을 1로 바꾸고, 다른 기존 상품 250개를 삭제하고, 새 ID의 상품 250개를 추가한다. 모든 bulk는 `refresh=true`다. 세 구간에서 수정·삭제·추가 대상은 각각 겹치지 않는다. 내보내기 시작 시점의 데이터 집합과 값을 끝까지 보존하라.

[6] Elasticsearch에는 검색과 검색 시점의 상태를 유지·해제하는 API만 호출하라. 제공된 주소에서는 `/<ES_INDEX>/_search`, `/_search`, `/<ES_INDEX>/_pit?keep_alive=...`, `/_pit` 경로만 허용한다. 색인 설정·매핑·문서를 바꾸거나 다른 주소로 우회하지 마라. 기본 `index.max_result_window` 10,000을 유지하라. 시작 코드의 `rank`, `id` 정렬을 유지해도 되고 전체 순회에 맞는 정렬로 바꿔도 된다.

[7] 메모리는 서비스 JVM의 최대 힙 192MiB로 제한한다. 한 검색 응답의 상품은 최대 1,000개이며 `from + size`는 10,000 이하여야 한다. 전체 결과를 모아 두지 말고 페이지를 읽을 때마다 스트림에 쓰라. 전체 검색 요청 수는 500회 이상 520회 이하여야 한다. 마지막 빈 페이지 요청도 센다. 이 요청 수와 페이지 크기 제한은 큰 페이지로 한도를 우회하거나 지나치게 작은 페이지로 반복 비용을 늘리는 구현을 막는다. 전체 내보내기는 240초 안에 끝내라. 적재와 컨테이너 준비 시간은 이 시간에 포함하지 않는다.

[8] 합격하려면 `missing=0`, `duplicates=0`, `invalid_rows=0`이어야 한다. 실험기는 ID 비트 집합으로 빠짐과 겹침을 확인하고 각 줄의 revision·payload를 초기 값과 대조한다. `invalid_rows`에는 형식 오류, 시작 뒤 생긴 ID, 값 불일치, HTTP 200 이외 응답을 센다. `search_requests`는 500~520, `max_window`는 10,000 이하, `max_page_hits`는 1,000 이하여야 한다. `write_batches`는 12여야 하고 `forbidden_requests`는 0이어야 한다. 시간 지표 `export_ms`는 240,000 이하여야 한다. 모든 불변식을 동시에 만족하라.

[9] 검색 상태를 유지하는 자원은 성공·실패와 관계없이 해제하라. 내보내기 응답을 다 읽은 뒤 최대 5초 동안 Elasticsearch 노드 통계를 확인해 `open_contexts=0`인지 검사한다. 단순히 만료를 기다리지 말고 명시적으로 닫아라. 실험기는 종료 시 서비스와 Docker 컨테이너·볼륨을 정리한다. Docker 준비 실패, 적재 실패, 부하 발생기 오류는 불변식 실패와 구분한다.

[10] 수정 범위는 `src/main/kotlin/Exporter.kt`다. 공개 함수 `export(elastic: Elastic, index: String, output: BufferedWriter)`를 유지하라. 같은 파일 안에 함수를 추가해도 된다. `Elastic.kt`의 HTTP·JSON 함수, `Main.kt`의 서버·출력 틀, `Json.kt`의 JSON 파서를 활용하라. 빌드·실행 파일, 환경 변수, 관찰 장치와 숨은 파일은 바꾸지 마라. JDK 21과 제공된 Kotlin 템플릿만 사용하며 새 의존성을 추가하지 마라.

[11] 공식 근거는 Elastic의 [Paginate search results](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/paginate-search-results)와 [Open a point in time](https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-open-point-in-time)이다. 관찰한 실패 원인과 선택한 검색 방식의 제약을 문서와 대조하라. 500,000개 데이터, HTTP 계약, 192MiB·1,000개·520회·240초 기준은 이 실험에서 정한 조건이다.
