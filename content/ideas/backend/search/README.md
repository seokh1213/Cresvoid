# 검색: Elasticsearch

Elasticsearch 의 색인과 검색이 분산된 샤드 위에서 어떻게 동작하는지, 무엇이 근사이고 언제 보이고 언제 사라지지 않는지를 익힌다. 검색 결과가 틀리거나 느리거나 색인이 막히는 원인을 설명하는 힘을 기른다. 역색인과 BM25 자체는 구현 트랙 `search-engine` 에서 다룬다.

## 1. 개념 지도

기본값은 2026-10-08 에 Elastic 문서로 확인한 값이다.

### 1.1 색인과 샤드

- 주 샤드 수(`index.number_of_shards`)의 기본값은 1이고 색인을 만들 때만 정할 수 있다. 복제본 수(`index.number_of_replicas`)의 기본값은 1이다.
- 문서는 라우팅 값의 해시로 주 샤드가 정해진다. 그래서 주 샤드 수를 나중에 바꾸려면 다시 색인하거나 나누기·줄이기 API 를 쓴다.
- 샤드 크기는 10~50GB, 샤드당 문서 2억 개 미만을 권한다. 작은 샤드 여러 개보다 큰 샤드 몇 개가 자원을 덜 쓴다. 노드당 동결되지 않은 샤드는 1,000개까지다.

### 1.2 거의 실시간 검색과 내구성

- 색인한 문서는 메모리 버퍼에 있다가 refresh 때 새 세그먼트로 쓰이고 열리면서 검색된다. Lucene 커밋 없이 보이게 하는 가벼운 작업이다.
- refresh 는 1초(`index.refresh_interval`)마다 일어난다. 30초(`index.search.idle.after`) 동안 검색이 없던 샤드는 다음 검색이 올 때까지 refresh 를 건너뛴다.
- translog 는 Lucene 커밋에 아직 들어가지 않은 연산을 담는다. 기본 내구성(`index.translog.durability`)은 request 다. 주 샤드와 할당된 모든 복제본에서 translog 를 fsync 한 뒤에 성공을 알린다. async 면 5초(`sync_interval`)마다 fsync 한다.
- flush 는 Lucene 커밋을 하고 새 translog 세대를 시작한다. translog 가 10GB(`flush_threshold_size`)가 되면 flush 한다.

### 1.3 매핑

- 필드 수 1,000(`index.mapping.total_fields.limit`), 깊이 20, nested 필드 100, 문서 하나의 nested 객체 10,000 이 기본 한도다. 객체 매핑, 별칭, 런타임 필드도 필드 수에 들어간다.
- 동적 매핑으로 키가 계속 새로 생기는 문서를 넣으면 매핑이 폭증한다.
- 한글 분석: nori_tokenizer 의 `decompound_mode` 는 none, discard(기본, 복합어를 나누고 원형을 버림), mixed(나누고 원형도 남김)다. `discard_punctuation` 기본값은 true 다. 사용자 사전에 명사를 더할 수 있다.

### 1.4 검색과 페이지 넘기기

- from + size 는 10,000(`index.max_result_window`)을 넘을 수 없다. 샤드마다 앞 페이지까지의 결과를 모두 올려야 해서 깊이 넘길수록 메모리와 CPU 를 많이 쓴다.
- 깊은 페이지는 search_after 와 PIT(point in time)로 넘긴다. 페이지 사이에 refresh 가 끼면 순서가 바뀌므로 PIT 로 색인 상태를 고정한다. PIT 를 쓰면 `_shard_doc` 동점 해소 필드가 자동으로 더해진다. PIT 가 없으면 고유한 동점 해소 필드를 정렬에 넣어야 결과가 빠지거나 겹치지 않는다.
- scroll API 는 깊은 페이지 넘기기에 더는 권하지 않는다.
- 전체 일치 수(`track_total_hits`)는 기본으로 10,000 까지만 정확히 센다.

### 1.5 집계

- terms 집계는 샤드마다 상위 shard_size(기본 size × 1.5 + 10)개를 보내고 조정 노드가 합친다. 그래서 근사다. 기본 size 는 10이다.
- `doc_count_error_upper_bound` 는 샤드마다 shard_size 안에 못 든 가장 큰 버킷 크기를 더한 값이다. `sum_other_doc_count` 는 상위 size 개에 못 든 문서 수다.
- 개수 오름차순 정렬은 결과가 부정확해지기 쉬워 rare_terms 집계를 권한다.
- cardinality 집계는 HyperLogLog++ 근사다. precision_threshold 기본값은 3,000, 최댓값은 40,000 이다.

### 1.6 동시성과 클러스터

- 낙관적 동시성 제어: 읽을 때 받은 시퀀스 번호와 주 임기(`if_seq_no`, `if_primary_term`)를 조건으로 쓰면 그 사이 바뀐 문서는 고치지 않는다. 복제 요청이 병렬로 가서 순서가 뒤바뀔 수 있어 시퀀스 번호가 필요하다.
- 디스크 워터마크: 85% 를 넘은 노드에는 샤드를 새로 두지 않는다. 90% 를 넘으면 샤드를 다른 노드로 옮긴다. 95% 를 넘으면 그 노드에 샤드가 있는 색인마다 `index.blocks.read_only_allow_delete` 를 걸고 90% 아래로 내려가면 자동으로 푼다.

## 2. 학습 경로

1. R-search-10 기술 지문으로 refresh, 세그먼트, translog, flush 를 익힌다.
2. B-search-20 `es-cluster` 트랙에서 라우팅, 샤드별 점수, terms 집계 근사, 깊은 페이지를 시뮬레이터로 구현한다.
3. B-search-30 `nrt-index` 트랙에서 보임과 내구성, 낙관적 동시성을 구현한다.
4. F-search-40 버그 수정으로 한글 정규화 문제를 만난다.
5. L-search-50~L-search-53 실험으로 실제 Elasticsearch 의 한도와 분석기를 확인한다. Docker 결정 뒤에 한다.

## 3. 환경

- reading, build, fix 카드는 추가 도구가 필요 없다.
- lab 카드는 Docker 의 Elasticsearch 이미지가 필요하다. 노드 하나에 힙 1~2GB 를 준다. 다중 샤드 동작은 노드 하나에서도 주 샤드 수를 늘려 볼 수 있다.

## 4. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 별칭으로 무중단 재색인 | lab | 새 매핑 색인, 이중 쓰기, 별칭 교체 |
| 동의어를 색인 때와 검색 때 넣는 차이 | lab | 동의어를 바꾼 뒤 재색인 필요 여부 |
| nested 와 object 배열 | code | 객체 배열이 평평해져 서로 다른 객체의 값이 함께 맞는 문제 |
| 벡터 검색과 하이브리드 | lab | kNN 후보 수와 정확도, RRF 결합. ai-agent 의 RAG 와 이어진다 |
| 대량 색인과 429 | lab | 쓰기 큐가 차서 거절될 때의 재시도와 지터 |
| 대량 색인 처리량 | lab | bulk 크기를 두 배씩 키워 처리량이 더 늘지 않는 지점 찾기, HTTP 200 응답 안의 `errors` 와 항목별 실패, 대량 적재 중 `number_of_replicas` 0 과 `refresh_interval` -1 을 쓰고 끝나면 되돌리기, 동시 요청을 하나로 두거나 외부 버전으로 문서 순서 지키기. '대량 색인과 429' 와 이어진다 |

## 5. 출처

- Index settings: https://www.elastic.co/docs/reference/elasticsearch/index-settings/index-modules
- Mapping limit settings: https://www.elastic.co/docs/reference/elasticsearch/index-settings/mapping-limit
- Translog settings: https://www.elastic.co/docs/reference/elasticsearch/index-settings/translog
- Near real-time search: https://www.elastic.co/docs/manage-data/data-store/near-real-time-search
- Paginate search results: https://www.elastic.co/docs/reference/elasticsearch/rest-apis/paginate-search-results
- Terms aggregation: https://www.elastic.co/docs/reference/aggregations/search-aggregations-bucket-terms-aggregation
- Cardinality aggregation: https://www.elastic.co/docs/reference/aggregations/search-aggregations-metrics-cardinality-aggregation
- Optimistic concurrency control: https://www.elastic.co/docs/reference/elasticsearch/rest-apis/optimistic-concurrency-control
- Disk-based shard allocation: https://www.elastic.co/docs/reference/elasticsearch/configuration-reference/cluster-level-shard-allocation-routing-settings
- Size your shards: https://www.elastic.co/docs/deploy-manage/production-guidance/optimize-performance/size-shards
- Tune for indexing speed: https://www.elastic.co/docs/deploy-manage/production-guidance/optimize-performance/indexing-speed
- nori_tokenizer: https://www.elastic.co/docs/reference/elasticsearch/plugins/analysis-nori-tokenizer
