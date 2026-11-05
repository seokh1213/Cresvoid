# nori 분석기와 상품명

[1] 상품명 검색을 운영한다고 가정해라. 이 단계는 Nori 설정 입문 실험이다. 부분어로 검색해도 상품을 빠뜨리지 않고 브랜드와 모델 번호가 다른 상품은 섞지 않아야 한다. 먼저 토큰을 예측하고 관찰한 뒤 `AnalysisSettings.kt`의 분석기 설정을 조정해라. 이 단계는 Docker의 고정 Elasticsearch 9.5.5 nori 이미지, JDK 21, Kotlin만 사용한다. 단어를 Kotlin에서 나누거나 검색 결과를 보정하지 마라.

[2] 관찰 전에 `초경량패딩`을 none, discard, mixed로 분석할 때의 토큰과 순서를 적어라. 관찰용 세 프로필은 모두 사용자 사전 규칙 `초경량패딩 초경량 패딩`, `노바핏`을 사용하며 필터는 사용하지 않는다. none은 복합어를 분해하지 않고, discard는 분해 후 원형을 버리며, mixed는 원형도 남긴다. mixed의 원형은 여러 위치를 가로지를 수 있으므로 `_analyze` 응답의 `position`과 `positionLength`도 비교해라. 문자열 목록만 같다고 위치 구조도 같다고 판단하지 마라.

[3] 이어서 `노바핏`, `AB12`, `패딩은`, `은`의 토큰을 예측해라. plain 프로필은 사전 없이 mixed tokenizer만 사용한다. 이를 사전을 쓰는 mixed 프로필 및 실제 검색용 catalog 프로필과 비교해라. 사용자 사전에 없는 상품명 전체가 한 토큰이 된다고 가정하지 마라. 복합어의 분해 항목과 같은 문자열을 단독으로 분석한 결과도 직접 확인해라. 숫자 토큰을 제거했을 때 AB12와 AB13을 구별할 수 있는지 검색으로 확인해라.

[4] 실행기가 Elasticsearch를 한 번 띄우고 임의 루프백 포트를 `ES_URL`로 전달한다. 프로그램은 `build.sh`로 실행 파일을 만들고 `PORT`의 `127.0.0.1`에서 `run.sh`로 HTTP 서비스를 시작해야 한다. `GET /health`는 분석기와 색인을 준비한 뒤 HTTP 200을 반환한다. 직접 토큰을 관찰하려면 lab 환경 안내(`content/lab-env/README.md`)의 nori 서비스에 연결하여 `./build.sh`를 실행하고 `PORT=<빈 포트> ES_URL=http://127.0.0.1:<ES 포트> ./run.sh`를 실행해라. 다른 터미널에서 아래처럼 요청하고, 관찰이 끝나면 서비스와 직접 띄운 Docker 자원을 종료해라. 채점 실험에서는 실행기가 이 과정을 소유한다.

```sh
curl -sS -X POST "http://127.0.0.1:<PORT>/analyze?profile=mixed" --data-binary '초경량패딩'
curl -sS -X POST "http://127.0.0.1:<PORT>/analyze?profile=plain" --data-binary '노바핏'
curl -sS -X POST "http://127.0.0.1:<PORT>/analyze?profile=catalog" --data-binary 'AB12'
```

[4.1] 집계 지표가 실패하면 입력별로 좁혀라. token_mismatches가 0보다 크면 [8]의 여섯 입력을 catalog로 하나씩 분석하여 토큰의 순서와 중복을 비교해라. missing_hits나 extra_hits가 0보다 크면 [7]의 상품을 `/catalog`에 넣고 [9]의 검색을 하나씩 재현하여 빠진 ID와 섞인 ID를 찾아라. 토큰 목표는 맞지만 검색이 실패하면 해당 상품명 전체와 검색어를 각각 catalog로 분석하여 토큰 문자열, `position`, `positionLength`를 비교해라. 복합어의 분해 항목과 단독 검색어가 같은 토큰이 되는지, 숫자와 브랜드가 구별되는지 확인해라. 설정을 바꾼 뒤에는 [12]대로 재시작·재색인하고 같은 입력으로 다시 확인해라.

[5] 공개 서비스 계약은 다음과 같다. `POST /analyze?profile=<프로필>`은 UTF-8 본문 전체를 분석할 문자열로 받고 Elasticsearch `_analyze` JSON을 그대로 반환한다. 프로필은 none, discard, mixed, plain, catalog이며 생략하면 catalog다. `POST /catalog`는 `상품ID<TAB>상품명`을 한 줄씩 받아 기존 상품을 모두 교체하고, refresh가 끝난 뒤 `{"indexed":건수}`를 반환한다. 상품ID는 영문 소문자·숫자·하이픈 1~40자, 상품명은 1~200자, 상품 수는 1~100개다. `POST /search`는 UTF-8 본문을 검색어로 받고 `hits.hits` 안의 `_id`만 남긴 Elasticsearch JSON을 반환한다. 결과가 없으면 `{}`일 수 있다. 검색과 분석의 정상 요청은 모두 HTTP 200이어야 한다. 본문 길이는 1~32768자다.

[6] 검색은 title의 색인·검색에 동일한 catalog 분석기를 쓰는 match 쿼리다. `operator=and`, `zero_terms_query=none`, `size=100`을 유지해라. 토큰 그래프는 Elasticsearch가 해석하며 결과 순위는 판정하지 않는다. 고유 상품ID 집합을 비교하고 중복 ID도 실패로 센다. 상품ID를 검색어에 대응시키거나, 본문을 다른 문자열로 치환하거나, 질의마다 분석기를 바꾸지 마라.

[7] 공개 상품 묶음은 1 `초경량패딩 AB12`, 2 `초경량패딩 AB13`, 3 `패딩 AB12`, 4 `초경량 가방 AB12`, 5 `노바핏 운동화 AB12`, 6 `노바 핏 운동화 AB12`, 7 `노바핏 운동화 AB13`, 8 `가방 AB12`다. 번호는 설명용이며 실행기는 매번 별도의 상품ID를 부여한다. 노바핏은 이 상점에서 하나의 브랜드 이름이고 노바와 핏을 띄어 쓴 상품은 그 브랜드가 아니다. AB 뒤 숫자도 모델 정체성에 포함한다.

[8] catalog의 토큰 목록은 순서와 중복까지 다음 목표를 만족해야 한다. `초경량패딩`은 `[초경량패딩, 초경량, 패딩]`, `노바핏`은 `[노바핏]`, `AB12`는 `[ab, 12]`, `AB13`은 `[ab, 13]`, `AB99`는 `[ab, 99]`, `패딩은`은 `[패딩]`이어야 한다. 조사·어미를 지우더라도 숫자와 영문 모델 토큰은 남겨라. 관찰용 네 프로필은 수정하지 마라. 상품명과 검색어의 분해 결과가 맞지 않으면 토큰을 남기는 것만으로 재현율을 보장할 수 없다.

[9] 검색 결과에는 기대한 상품이 모두 있고 다른 상품은 없어야 한다. 16개 검색어의 기대 결과를 [7]의 번호 집합으로 적으면 `초경량`→{1,2,4}, `패딩`→{1,2,3}, `초경량패딩`→{1,2}, `AB12`→{1,3,4,5,6,8}, `AB13`→{2,7}, `초경량패딩 AB12`→{1}, `패딩은 AB12`→{1,3}, `노바핏`→{5,7}, `노바핏 AB12`→{5}, `노바 핏 AB12`→{6}, `운동화 AB12`→{5,6}, `미등록 AB99`→{}, `은`→{}, `AB99`→{}, `노바핏 AB13`→{7}, `ab12`→{1,3,4,5,6,8}이다. `은` 단독은 명사로 분석될 수 있다. 이 묶음에서 검색 결과가 없는 이유를 조사 제거와 혼동하지 마라.

[10] catalog-session은 상품 8개를 적재한 뒤 한 서버에서 총 104개의 요청을 초당 10개, 100ms 간격으로 예약한다. 앞 요청의 완료를 기다리지 않는 개방형 부하다. 26개 요청 묶음을 4회 반복한다. 묶음은 관찰 4개(none·discard·mixed의 초경량패딩과 mixed의 노바핏), [8]의 catalog 분석 6개, [9]의 검색 16개다. 요청당 제한은 15초다. 준비·색인 요청은 부하 지표에서 제외한다. Docker 준비는 240초, 프로그램 준비는 30초, 시나리오 전체는 360초 이내여야 한다. 시간 초과나 빌드 실패는 의도한 불변식 실패로 인정하지 않는다.

[11] requests는 예정 요청 수 104, completed는 HTTP 200으로 완료한 부하 요청 수다. probe_mismatches는 관찰 16건 중 예상 토큰 목록과 다른 응답 수, token_mismatches는 catalog 분석 24건 중 [8]과 다른 응답 수다. missing_hits는 각 검색의 기대 ID 집합에서 실제 ID 집합을 뺀 크기의 합이다. extra_hits는 반대 차집합 크기에 중복 ID 수를 더한 합이다. exact_searches는 HTTP 200이고 기대 ID와 정확히 일치한 검색 수이며 search_requests는 64다. 합격하려면 requests=completed=104, probe_mismatches=token_mismatches=missing_hits=extra_hits=0, exact_searches=search_requests=64여야 한다. p95_ms는 HTTP 전송 시작부터 응답 본문 수신까지 걸린 시간 104개를 오름차순으로 놓았을 때 99번째 값이다. p95_ms는 관찰용이며 합격 임계값으로 쓰지 않는다. 채점 시나리오는 한 번 실행한다. 제작 검증에서는 세 번 반복해 모두 통과하는지 확인한다.

[12] 수정 범위는 `src/main/kotlin/AnalysisSettings.kt` 안의 `catalogAnalysis()`가 반환하는 Elasticsearch analysis 설정뿐이다. tokenizer, 사용자 사전 규칙, 품사 필터, lowercase 등 필터 조합을 조정해라. 이름 catalog인 분석기를 유지하고, 모든 상품명과 검색어에 같은 설정을 적용해라. Main.kt, ElasticCatalog.kt, 빌드·실행 파일, 관찰 프로필, HTTP 계약, 부하, 상품 묶음은 바꾸지 마라. 분석기 변경 뒤에는 프로그램을 다시 실행하여 새 색인에 상품을 다시 넣어라. 종료 시 이 프로그램의 고유 색인을 지우고 실행기는 서버·컨테이너·볼륨·네트워크를 정리한다.

[13] API 근거는 Elastic의 [nori_tokenizer](https://www.elastic.co/docs/reference/elasticsearch/plugins/analysis-nori-tokenizer), [nori_part_of_speech](https://www.elastic.co/docs/reference/elasticsearch/plugins/analysis-nori-speech), [match query](https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-match-query)다. 앞의 두 문서에서 분해 모드, 인라인 사용자 사전, 제거할 품사 지정법을 확인하고 match 문서에서 AND 및 분석 결과가 비었을 때의 동작을 확인해라. 품사 태그는 JX·JKS·ETM처럼 실제 태그 이름을 지정한다. 이 단계의 상품·브랜드 의미와 정확한 기대 결과는 [7]~[9]에서 정한 상점 계약이다.
