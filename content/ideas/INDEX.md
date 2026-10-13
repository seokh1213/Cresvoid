# 카드 색인

`kotlin content/ideas/index.main.kts` 가 카드 front matter 로 만든 파일이다. 손으로 고치지 않는다. 카드 형식과 차수의 뜻은 [README.md](README.md) 에 있다.

## 요약

| 도메인 | 카드 | 후보 | 제작 중 | 완료 | 보류 |
|---|---|---|---|---|---|
| [읽기](reading/README.md) | 15 | 12 | 0 | 3 | 0 |
| [구현](implementation/README.md) | 28 | 22 | 0 | 6 | 0 |
| [AI 에이전트](backend/ai-agent/README.md) | 5 | 3 | 0 | 2 | 0 |
| [Kotlin 코루틴](backend/coroutines/README.md) | 8 | 0 | 0 | 8 | 0 |
| [데이터베이스](backend/database/README.md) | 8 | 6 | 0 | 2 | 0 |
| [분산 시스템](backend/distributed/README.md) | 5 | 3 | 0 | 2 | 0 |
| [HDFS](backend/hdfs/README.md) | 2 | 0 | 0 | 2 | 0 |
| [장애 보고서 독해](backend/incidents/README.md) | 7 | 7 | 0 | 0 | 0 |
| [JVM](backend/jvm/README.md) | 4 | 4 | 0 | 0 | 0 |
| [레이크하우스: 파일 포맷과 테이블 포맷](backend/lakehouse/README.md) | 3 | 1 | 0 | 2 | 0 |
| [부하 엣지 케이스](backend/load/README.md) | 12 | 10 | 0 | 0 | 2 |
| [메시징: Kafka](backend/messaging/README.md) | 5 | 3 | 0 | 2 | 0 |
| [네트워크](backend/network/README.md) | 2 | 2 | 0 | 0 | 0 |
| [관측성](backend/observability/README.md) | 2 | 0 | 0 | 2 | 0 |
| [OLAP](backend/olap/README.md) | 6 | 4 | 0 | 2 | 0 |
| [검색: Elasticsearch](backend/search/README.md) | 8 | 6 | 0 | 2 | 0 |
| [Spark](backend/spark/README.md) | 7 | 5 | 0 | 2 | 0 |
| [프레임워크 심화와 튜닝](backend/spring/README.md) | 10 | 10 | 0 | 0 | 0 |
| [시스템 설계](backend/system-design/README.md) | 16 | 14 | 0 | 2 | 0 |
| 합계 | 153 | 112 | 0 | 39 | 2 |

## 다음 후보

상태가 후보인 카드를 차수 순서로 늘어놓았다. 같은 차수에서는 추가 환경이 필요 없는 카드가 먼저다.

| 차수 | 카드 | 제목 | 종류 | 트랙 | 단계 | 환경 |
|---|---|---|---|---|---|---|
| 1 | [C-kotlin-20](implementation/cards/C-kotlin-20-stock-reservation.md) | 재고 예약과 만료 | code | code-reading | stock-reservation | 없음 |
| 1 | [F-kotlin-20](implementation/cards/F-kotlin-20-point-accrual.md) | 포인트 적립 계산 | fix | bug-fix | point-accrual | 없음 |
| 1 | [C-kotlin-30](implementation/cards/C-kotlin-30-order-events.md) | 주문 이벤트 처리 | code | code-reading | order-events | 없음 |
| 1 | [F-kotlin-30](implementation/cards/F-kotlin-30-daily-limit.md) | 일일 사용 한도 | fix | bug-fix | daily-limit | 없음 |
| 2 | [B-ai-agent-30](backend/ai-agent/cards/B-ai-agent-30-rag.md) | 검색 증강 파이프라인 만들기 | build | rag | 01-chunking, 02-retrieval, 03-hybrid, 04-eval | 없음 |
| 3 | [B-ai-agent-40](backend/ai-agent/cards/B-ai-agent-40-mcp-server.md) | MCP 서버 만들기 | build | mcp-server | 01-tools, 02-resources-prompts, 03-errors-and-versions | 없음 |
| 3 | [R-incidents-10](backend/incidents/cards/R-incidents-10-cloudflare-regex.md) | 정규식 하나가 멈춘 전 세계 프록시 | reading | incident-reading | cloudflare-regex | 없음 |
| 3 | [R-incidents-20](backend/incidents/cards/R-incidents-20-github-split-writes.md) | 43초 분할이 만든 하루 동안의 장애 | reading | incident-reading | github-split-writes | 없음 |
| 3 | [R-incidents-30](backend/incidents/cards/R-incidents-30-s3-capacity-removal.md) | 잘못 입력한 용량 제거 명령 | reading | incident-reading | s3-capacity-removal | 없음 |
| 3 | [R-incidents-50](backend/incidents/cards/R-incidents-50-crowdstrike-channel-file.md) | 필드 하나가 모자랐던 콘텐츠 갱신 | reading | incident-reading | crowdstrike-channel-file | 없음 |
| 3 | [R-incidents-60](backend/incidents/cards/R-incidents-60-gcp-service-control.md) | 기능 플래그 없는 코드 경로와 빈 필드 | reading | incident-reading | gcp-service-control | 없음 |
| 3 | [B-olap-30](backend/olap/cards/B-olap-30-sketch-and-sparse-index.md) | 근사 집계와 희소 인덱스 | build | mini-olap | 04-sketch, 05-sparse-index | 없음 |
| 3 | [B-search-30](backend/search/cards/B-search-30-nrt-index.md) | 세그먼트와 translog 시뮬레이터 만들기 | build | nrt-index | 01-refresh, 02-durability, 03-concurrency | 없음 |
| 3 | [B-system-design-30](backend/system-design/cards/B-system-design-30-id-generator.md) | 분산 고유 ID 생성기 만들기 | build | id-generator | 01-snowflake, 02-sharded-ids, 03-ordering | 없음 |
| 3 | [B-system-design-40](backend/system-design/cards/B-system-design-40-ledger.md) | 결제 원장 만들기 | build | ledger | 01-double-entry, 02-psp-idempotency, 03-reconciliation | 없음 |
| 3 | [B-system-design-50](backend/system-design/cards/B-system-design-50-matching-engine.md) | 주문 매칭 엔진 만들기 | build | matching-engine | 01-order-book, 02-order-types, 03-replay | 없음 |
| 3 | [L-load-10](backend/load/cards/L-load-10-stock-race.md) | 재고 차감과 동시 주문 | lab | backend-lab | stock-race | k6 |
| 3 | [L-load-20](backend/load/cards/L-load-20-idempotency.md) | 중복 요청과 멱등 키 | lab | backend-lab | idempotency | k6 |
| 3 | [L-load-30](backend/load/cards/L-load-30-cache-stampede.md) | 캐시 스탬피드 | lab | backend-lab | cache-stampede | k6, redis |
| 3 | [L-load-40](backend/load/cards/L-load-40-penetration-avalanche.md) | 캐시 관통과 동시 만료 | lab | backend-lab | penetration-avalanche | k6, redis |
| 3 | [L-load-50](backend/load/cards/L-load-50-stale-set.md) | 캐시와 원천의 불일치 | lab | backend-lab | stale-set | k6, redis |
| 3 | [L-load-60](backend/load/cards/L-load-60-cache-outage.md) | 캐시 장애와 원천 보호 | lab | backend-lab | cache-outage | k6, redis |
| 4 | [B-database-30](backend/database/cards/B-database-30-lsm.md) | LSM 저장소 만들기 | build | lsm | 01-memtable, 02-compaction, 03-bloom-and-range | 없음 |
| 4 | [R-database-40](backend/database/cards/R-database-40-online-resharding.md) | 서비스를 멈추지 않는 데이터 이전과 샤딩 | reading | tech-reading | online-resharding | 없음 |
| 4 | [B-distributed-40](backend/distributed/cards/B-distributed-40-consistent-hashing.md) | 일관 해싱 만들기 | build | consistent-hashing | 01-ring, 02-rebalance, 03-replication | 없음 |
| 4 | [R-humanities-10](reading/cards/R-humanities-10-saussure.md) | 소쉬르의 기호 이론 | reading | reading | saussure | 없음 |
| 4 | [R-incidents-40](backend/incidents/cards/R-incidents-40-meta-backbone.md) | 스스로 사라진 DNS | reading | incident-reading | meta-backbone | 없음 |
| 4 | [R-incidents-70](backend/incidents/cards/R-incidents-70-slack-cold-start.md) | 연휴 뒤 첫 월요일의 급증 | reading | incident-reading | slack-cold-start | 없음 |
| 4 | [L-jvm-10](backend/jvm/cards/L-jvm-10-container-heap.md) | 메모리 한도와 기본 힙 크기 | lab | jvm-lab | container-heap | 없음 |
| 4 | [L-jvm-20](backend/jvm/cards/L-jvm-20-threadlocal-leak.md) | 스레드 풀과 ThreadLocal | lab | jvm-lab | threadlocal-leak | 없음 |
| 4 | [L-jvm-30](backend/jvm/cards/L-jvm-30-thread-dump.md) | 스레드 덤프로 교착 찾기 | lab | jvm-lab | thread-dump | 없음 |
| 4 | [C-kotlin-40](implementation/cards/C-kotlin-40-lru-cache.md) | 상품 상세 로컬 캐시 | code | code-reading | lru-cache | 없음 |
| 4 | [F-kotlin-40](implementation/cards/F-kotlin-40-option-equality.md) | 장바구니 상품 옵션 비교 | fix | bug-fix | option-equality | 없음 |
| 4 | [C-kotlin-50](implementation/cards/C-kotlin-50-retry-policy.md) | 외부 결제 호출 재시도 정책 | code | code-reading | retry-policy | 없음 |
| 4 | [F-kotlin-50](implementation/cards/F-kotlin-50-comparator-contract.md) | 추천 점수 정렬 | fix | bug-fix | comparator-contract | 없음 |
| 4 | [B-kv-store-10](implementation/cards/B-kv-store-10-kv-store.md) | 키-값 저장소 | build | kv-store | 01-basic, 02-log, 03-transactions | 없음 |
| 4 | [B-lakehouse-20](backend/lakehouse/cards/B-lakehouse-20-columnar.md) | 열 지향 파일 만들기 | build | columnar | 01-encode, 02-pruning, 03-nested | 없음 |
| 4 | [C-messaging-30](backend/messaging/cards/C-messaging-30-consumer-commit.md) | 오프셋 커밋 시점과 중복 처리 | code | code-reading | consumer-commit | 없음 |
| 4 | [L-network-10](backend/network/cards/L-network-10-keepalive-race.md) | keep-alive 수명의 경합 | lab | net-lab | keepalive-race | 없음 |
| 4 | [R-science-10](reading/cards/R-science-10-enzyme.md) | 효소 반응 속도와 저해 | reading | reading | enzyme | 없음 |
| 4 | [F-search-40](backend/search/cards/F-search-40-hangul-normalization.md) | 한글 정규화가 어긋난 상품 검색 | fix | bug-fix | hangul-normalization | 없음 |
| 4 | [B-system-design-60](backend/system-design/cards/B-system-design-60-crawler.md) | 웹 크롤러 만들기 | build | crawler | 01-frontier, 02-dedup, 03-politeness | 없음 |
| 4 | [B-system-design-70](backend/system-design/cards/B-system-design-70-news-feed.md) | 뉴스 피드 팬아웃 만들기 | build | news-feed | 01-fanout-on-write, 02-fanout-on-read, 03-hybrid | 없음 |
| 4 | [B-system-design-80](backend/system-design/cards/B-system-design-80-chat.md) | 채팅 순서와 동기화 만들기 | build | chat | 01-sequencing, 02-sync, 03-presence | 없음 |
| 4 | [B-system-design-90](backend/system-design/cards/B-system-design-90-autocomplete.md) | 검색어 자동완성 만들기 | build | autocomplete | 01-trie, 02-cached-top-k, 03-filters-and-shards | 없음 |
| 4 | [B-system-design-100](backend/system-design/cards/B-system-design-100-geo-index.md) | 근처 장소 검색 만들기 | build | geo-index | 01-geohash, 02-radius-search, 03-quadtree | 없음 |
| 4 | [B-system-design-110](backend/system-design/cards/B-system-design-110-ad-click-aggregator.md) | 광고 클릭 집계기 만들기 | build | ad-click-aggregator | 01-windows, 02-late-events, 03-exactly-once | 없음 |
| 4 | [B-system-design-120](backend/system-design/cards/B-system-design-120-reservation.md) | 호텔 예약 재고 만들기 | build | reservation | 01-inventory, 02-idempotency, 03-concurrency | 없음 |
| 4 | [B-system-design-130](backend/system-design/cards/B-system-design-130-leaderboard.md) | 실시간 순위표 만들기 | build | leaderboard | 01-sorted-set, 02-range-queries, 03-sharding | 없음 |
| 4 | [R-technology-10](reading/cards/R-technology-10-key-exchange.md) | 공개키 암호와 디피-헬먼 키 교환 | reading | reading | key-exchange | 없음 |
| 4 | [L-database-50](backend/database/cards/L-database-50-gap-lock.md) | 갭 잠금과 삽입 교착 | lab | db-lab | gap-lock | docker, maven |
| 4 | [L-database-51](backend/database/cards/L-database-51-metadata-lock.md) | 긴 트랜잭션과 스키마 변경 대기 | lab | db-lab | metadata-lock | docker, maven, k6 |
| 4 | [L-olap-40](backend/olap/cards/L-olap-40-sorting-key.md) | 정렬 키와 읽는 그래뉼 수 | lab | olap-lab | sorting-key | docker |
| 4 | [L-olap-41](backend/olap/cards/L-olap-41-replacing-merge.md) | 병합 때만 지워지는 중복 | lab | olap-lab | replacing-merge | docker |
| 4 | [L-search-50](backend/search/cards/L-search-50-mapping-explosion.md) | 동적 매핑과 필드 폭증 | lab | es-lab | mapping-explosion | docker |
| 4 | [L-search-51](backend/search/cards/L-search-51-deep-paging.md) | 깊은 페이지 넘기기 | lab | es-lab | deep-paging | docker |
| 4 | [L-search-52](backend/search/cards/L-search-52-nori-analysis.md) | nori 분석기와 상품명 | lab | es-lab | nori-analysis | docker |
| 4 | [L-spark-30](backend/spark/cards/L-spark-30-null-semantics.md) | NULL, 중복, 열 순서의 의미론 | lab | spark-lab | null-semantics | spark |
| 4 | [L-spark-31](backend/spark/cards/L-spark-31-skew-and-aqe.md) | 데이터 쏠림과 적응 실행 | lab | spark-lab | skew-and-aqe | spark |
| 4 | [L-spark-32](backend/spark/cards/L-spark-32-overwrite-and-small-files.md) | 파티션 덮어쓰기와 작은 파일 | lab | spark-lab | overwrite-and-small-files | spark |
| 4 | [L-spring-10](backend/spring/cards/L-spring-10-pool-deadlock.md) | REQUIRES_NEW 와 풀 교착 | lab | spring-lab | pool-deadlock | maven |
| 4 | [L-spring-20](backend/spring/cards/L-spring-20-open-in-view.md) | OSIV 와 느린 외부 호출 | lab | spring-lab | open-in-view | gradle, maven, k6 |
| 4 | [L-spring-30](backend/spring/cards/L-spring-30-proxy-pitfalls.md) | 트랜잭션 프록시의 함정 | lab | spring-lab | proxy-pitfalls | maven |
| 4 | [L-spring-40](backend/spring/cards/L-spring-40-async-executor.md) | @Async 실행기와 큐 | lab | spring-lab | async-executor | maven |
| 4 | [L-spring-50](backend/spring/cards/L-spring-50-http-client-pool.md) | HTTP 클라이언트 커넥션 풀 | lab | spring-lab | http-client-pool | gradle, maven, k6 |
| 5 | [R-art-10](reading/cards/R-art-10-perspective.md) | 원근법과 시점 | reading | reading | perspective | 없음 |
| 5 | [B-distributed-30](backend/distributed/cards/B-distributed-30-crdt.md) | 복제 자료형 만들기 | build | crdt | 01-counters, 02-sets-and-registers, 03-convergence | 없음 |
| 5 | [B-distributed-50](backend/distributed/cards/B-distributed-50-simulation-testing.md) | 결정적 시뮬레이션 시험 만들기 | build | sim-testing | 01-scheduler, 02-faults, 03-shrink | 없음 |
| 5 | [R-humanities-20](reading/cards/R-humanities-20-source-criticism.md) | 사료 비판 | reading | reading | source-criticism | 없음 |
| 5 | [R-interdisciplinary-10](reading/cards/R-interdisciplinary-10-falsification.md) | 반증주의와 뒤엠-콰인 논제 | reading | reading | falsification | 없음 |
| 5 | [L-jvm-40](backend/jvm/cards/L-jvm-40-jit-warmup.md) | JIT 예열과 측정 함정 | lab | jvm-lab | jit-warmup | 없음 |
| 5 | [C-kotlin-60](implementation/cards/C-kotlin-60-search-query-parser.md) | 검색어 파서 | code | code-reading | search-query-parser | 없음 |
| 5 | [F-kotlin-60](implementation/cards/F-kotlin-60-sublist-view.md) | 페이지 단위 일괄 처리 | fix | bug-fix | sublist-view | 없음 |
| 5 | [C-kotlin-70](implementation/cards/C-kotlin-70-settlement-batch.md) | 정산 배치 | code | code-reading | settlement-batch | 없음 |
| 5 | [F-kotlin-70](implementation/cards/F-kotlin-70-locale-format.md) | 가격 문자열 출력 | fix | bug-fix | locale-format | 없음 |
| 5 | [C-kotlin-80](implementation/cards/C-kotlin-80-csv-import.md) | 상품 일괄 등록 | code | code-reading | csv-import | 없음 |
| 5 | [F-kotlin-80](implementation/cards/F-kotlin-80-code-pattern.md) | 상품 코드 검증 | fix | bug-fix | code-pattern | 없음 |
| 5 | [F-kotlin-90](implementation/cards/F-kotlin-90-retry-duplicate.md) | 주문 상태 변경 기록 | fix | bug-fix | retry-duplicate | 없음 |
| 5 | [F-kotlin-100](implementation/cards/F-kotlin-100-cache-key.md) | 회원 등급별 가격 캐시 | fix | bug-fix | cache-key | 없음 |
| 5 | [F-kotlin-110](implementation/cards/F-kotlin-110-float-threshold.md) | 무료 배송 기준 금액 | fix | bug-fix | float-threshold | 없음 |
| 5 | [F-kotlin-120](implementation/cards/F-kotlin-120-sale-window.md) | 타임세일 시작과 끝 | fix | bug-fix | sale-window | 없음 |
| 5 | [B-mini-sql-10](implementation/cards/B-mini-sql-10-mini-sql.md) | 작은 SQL 엔진 | build | mini-sql | 01-select, 02-group, 03-join | 없음 |
| 5 | [L-network-20](backend/network/cards/L-network-20-timeout-layers.md) | 타임아웃의 층 | lab | net-lab | timeout-layers | 없음 |
| 5 | [B-regex-10](implementation/cards/B-regex-10-regex.md) | 정규식 엔진 | build | regex | 01-basic, 02-classes, 03-linear | 없음 |
| 5 | [R-science-20](reading/cards/R-science-20-carnot.md) | 카르노 기관과 열역학 제2법칙 | reading | reading | carnot | 없음 |
| 5 | [R-science-30](reading/cards/R-science-30-plate-tectonics.md) | 판 구조론 | reading | reading | plate-tectonics | 없음 |
| 5 | [R-society-10](reading/cards/R-society-10-rescission.md) | 계약의 해제와 원상회복 | reading | reading | rescission | 없음 |
| 5 | [R-society-20](reading/cards/R-society-20-monetary-policy.md) | 통화정책의 전달 경로와 테일러 준칙 | reading | reading | monetary-policy | 없음 |
| 5 | [R-society-30](reading/cards/R-society-30-prospect-theory.md) | 전망 이론과 프레이밍 | reading | reading | prospect-theory | 없음 |
| 5 | [B-spreadsheet-10](implementation/cards/B-spreadsheet-10-spreadsheet.md) | 스프레드시트 계산기 | build | spreadsheet | 01-formula, 02-recalc, 03-cycles | 없음 |
| 5 | [B-system-design-140](backend/system-design/cards/B-system-design-140-url-shortener.md) | URL 단축기 만들기 | build | url-shortener | 01-base62, 02-collisions, 03-expiry | 없음 |
| 5 | [B-system-design-150](backend/system-design/cards/B-system-design-150-notifier.md) | 알림 발송기 만들기 | build | notifier | 01-dispatch, 02-reliability, 03-throttling | 없음 |
| 5 | [B-system-design-160](backend/system-design/cards/B-system-design-160-object-store.md) | 객체 저장소 만들기 | build | object-store | 01-versioning, 02-multipart, 03-durability | 없음 |
| 5 | [R-technology-20](reading/cards/R-technology-20-congestion-control.md) | TCP 혼잡 제어 | reading | reading | congestion-control | 없음 |
| 5 | [L-ai-agent-50](backend/ai-agent/cards/L-ai-agent-50-real-model.md) | 실제 모델로 도구 루프 평가하기 | lab | agent-lab | real-model | llm-api |
| 5 | [L-database-52](backend/database/cards/L-database-52-index-usage.md) | 인덱스를 쓰지 못하는 질의 | lab | db-lab | index-usage | docker, maven |
| 5 | [L-database-53](backend/database/cards/L-database-53-serializable-retry.md) | 직렬화 실패와 재시도 | lab | db-lab | serializable-retry | docker, maven |
| 5 | [L-load-70](backend/load/cards/L-load-70-retry-storm.md) | 타임아웃, 재시도, 백오프 | lab | backend-lab | retry-storm | k6 |
| 5 | [L-load-80](backend/load/cards/L-load-80-load-shedding.md) | 과부하와 load shedding | lab | backend-lab | load-shedding | k6 |
| 5 | [L-load-90](backend/load/cards/L-load-90-rate-limit.md) | 속도 제한 | lab | backend-lab | rate-limit | k6, redis |
| 5 | [L-load-120](backend/load/cards/L-load-120-adaptive-concurrency.md) | 적응형 동시성 제한 | lab | backend-lab | adaptive-concurrency | k6 |
| 5 | [L-messaging-40](backend/messaging/cards/L-messaging-40-rebalance.md) | 느린 처리와 재조정 폭풍 | lab | kafka-lab | rebalance | docker, maven |
| 5 | [L-messaging-41](backend/messaging/cards/L-messaging-41-durability.md) | acks 와 최소 동기 복제본 | lab | kafka-lab | durability | docker, maven |
| 5 | [L-olap-42](backend/olap/cards/L-olap-42-too-many-parts.md) | 작은 삽입과 파트 폭증 | lab | olap-lab | too-many-parts | docker, k6 |
| 5 | [L-search-53](backend/search/cards/L-search-53-terms-agg-accuracy.md) | terms 집계의 오차 | lab | es-lab | terms-agg-accuracy | docker |
| 5 | [L-spark-33](backend/spark/cards/L-spark-33-retries-and-side-effects.md) | 재실행과 부수효과 | lab | spark-lab | retries-and-side-effects | spark |
| 5 | [L-spark-34](backend/spark/cards/L-spark-34-streaming-watermark.md) | 워터마크와 늦은 이벤트 | lab | spark-lab | streaming-watermark | spark |
| 5 | [L-spring-60](backend/spring/cards/L-spring-60-max-lifetime.md) | maxLifetime 과 끊긴 연결 | lab | spring-lab | max-lifetime | maven |
| 5 | [L-spring-70](backend/spring/cards/L-spring-70-virtual-threads.md) | 가상 스레드와 풀 | lab | spring-lab | virtual-threads | gradle, maven, k6 |
| 5 | [L-spring-80](backend/spring/cards/L-spring-80-n-plus-one.md) | N+1 과 페이징 | lab | spring-lab | n-plus-one | gradle, maven, k6 |
| 5 | [L-spring-90](backend/spring/cards/L-spring-90-batch-insert.md) | IDENTITY 와 배치 삽입 | lab | spring-lab | batch-insert | maven |
| 5 | [L-spring-100](backend/spring/cards/L-spring-100-scheduler.md) | @Scheduled 단일 스레드 | lab | spring-lab | scheduler | maven |

## 트랙

컨텐츠 칸은 `content/tracks/<트랙>/track.json` 이 있는지를 보여 준다. 다음 번호는 단계 하나짜리 카드를 제작 중으로 바꿀 때 붙일 번호다. 예정 단계는 차수 순서다.

| 트랙 | 종류 | 컨텐츠 | 다음 번호 | 만든 단계 | 예정 단계 |
|---|---|---|---|---|---|
| ad-click-aggregator | build | 없음 | 04 |  | 01-windows(B-system-design-110 4차), 02-late-events(B-system-design-110 4차), 03-exactly-once(B-system-design-110 4차) |
| agent-lab | lab | 없음 | 01 |  | real-model(L-ai-agent-50 5차) |
| agent-runtime | build | 있음 | 06 | 01-loop(B-ai-agent-20 완료), 02-schema(B-ai-agent-20 완료), 03-parallel(B-ai-agent-20 완료), 04-context(B-ai-agent-20 완료), 05-guard(B-ai-agent-20 완료) |  |
| autocomplete | build | 없음 | 04 |  | 01-trie(B-system-design-90 4차), 02-cached-top-k(B-system-design-90 4차), 03-filters-and-shards(B-system-design-90 4차) |
| backend-lab | lab | 없음 | 01 |  | stock-race(L-load-10 3차), idempotency(L-load-20 3차), cache-stampede(L-load-30 3차), penetration-avalanche(L-load-40 3차), stale-set(L-load-50 3차), cache-outage(L-load-60 3차), retry-storm(L-load-70 5차), load-shedding(L-load-80 5차), rate-limit(L-load-90 5차), idempotent-consumer(L-load-100 5차), outbox(L-load-110 5차), adaptive-concurrency(L-load-120 5차) |
| bug-fix | fix | 있음 | 03 | 01-cursor-pagination(F-kotlin-10 완료), 02-order-deadline(F-coroutines-31 완료) | point-accrual(F-kotlin-20 1차), daily-limit(F-kotlin-30 1차), option-equality(F-kotlin-40 4차), comparator-contract(F-kotlin-50 4차), hangul-normalization(F-search-40 4차), sublist-view(F-kotlin-60 5차), locale-format(F-kotlin-70 5차), code-pattern(F-kotlin-80 5차), retry-duplicate(F-kotlin-90 5차), cache-key(F-kotlin-100 5차), float-threshold(F-kotlin-110 5차), sale-window(F-kotlin-120 5차) |
| chat | build | 없음 | 04 |  | 01-sequencing(B-system-design-80 4차), 02-sync(B-system-design-80 4차), 03-presence(B-system-design-80 4차) |
| code-reading | code | 있음 | 04 | 01-price-rules(C-kotlin-10 완료), 02-coroutine-order(C-coroutines-30 완료), 03-flow-semantics(C-coroutines-32 완료) | stock-reservation(C-kotlin-20 1차), order-events(C-kotlin-30 1차), lru-cache(C-kotlin-40 4차), retry-policy(C-kotlin-50 4차), consumer-commit(C-messaging-30 4차), search-query-parser(C-kotlin-60 5차), settlement-batch(C-kotlin-70 5차), csv-import(C-kotlin-80 5차) |
| columnar | build | 없음 | 04 |  | 01-encode(B-lakehouse-20 4차), 02-pruning(B-lakehouse-20 4차), 03-nested(B-lakehouse-20 4차) |
| consistent-hashing | build | 없음 | 04 |  | 01-ring(B-distributed-40 4차), 02-rebalance(B-distributed-40 4차), 03-replication(B-distributed-40 4차) |
| coroutine-lab | lab | 있음 | 02 | 01-dispatcher-starvation(L-coroutines-50 완료) |  |
| coroutine-patterns | build | 있음 | 07 | 01-fan-out(B-coroutines-20 완료), 02-limits(B-coroutines-20 완료), 03-channels(B-coroutines-20 완료), 04-flow(B-coroutines-20 완료), 05-batching(B-coroutines-35 완료), 06-single-flight(B-coroutines-35 완료) |  |
| coroutine-runtime | build | 있음 | 08 | 01-event-loop(B-coroutines-40 완료), 02-structured(B-coroutines-40 완료), 03-cancellation(B-coroutines-40 완료), 04-exceptions(B-coroutines-40 완료), 05-channels(B-coroutines-40 완료), 06-flow(B-coroutines-40 완료), 07-sequence(B-coroutines-40 완료) |  |
| crawler | build | 없음 | 04 |  | 01-frontier(B-system-design-60 4차), 02-dedup(B-system-design-60 4차), 03-politeness(B-system-design-60 4차) |
| crdt | build | 없음 | 04 |  | 01-counters(B-distributed-30 5차), 02-sets-and-registers(B-distributed-30 5차), 03-convergence(B-distributed-30 5차) |
| dataflow | build | 있음 | 05 | 01-plan(B-spark-20 완료), 02-shuffle(B-spark-20 완료), 03-join(B-spark-20 완료), 04-adaptive(B-spark-20 완료) |  |
| db-lab | lab | 없음 | 01 |  | gap-lock(L-database-50 4차), metadata-lock(L-database-51 4차), index-usage(L-database-52 5차), serializable-retry(L-database-53 5차) |
| es-cluster | build | 있음 | 04 | 01-routing(B-search-20 완료), 02-terms-agg(B-search-20 완료), 03-paging(B-search-20 완료) |  |
| es-lab | lab | 없음 | 01 |  | mapping-explosion(L-search-50 4차), deep-paging(L-search-51 4차), nori-analysis(L-search-52 4차), terms-agg-accuracy(L-search-53 5차) |
| geo-index | build | 없음 | 04 |  | 01-geohash(B-system-design-100 4차), 02-radius-search(B-system-design-100 4차), 03-quadtree(B-system-design-100 4차) |
| id-generator | build | 없음 | 04 |  | 01-snowflake(B-system-design-30 3차), 02-sharded-ids(B-system-design-30 3차), 03-ordering(B-system-design-30 3차) |
| incident-reading | reading | 없음 | 01 |  | cloudflare-regex(R-incidents-10 3차), github-split-writes(R-incidents-20 3차), s3-capacity-removal(R-incidents-30 3차), crowdstrike-channel-file(R-incidents-50 3차), gcp-service-control(R-incidents-60 3차), meta-backbone(R-incidents-40 4차), slack-cold-start(R-incidents-70 4차) |
| jvm-lab | lab | 없음 | 01 |  | container-heap(L-jvm-10 4차), threadlocal-leak(L-jvm-20 4차), thread-dump(L-jvm-30 4차), jit-warmup(L-jvm-40 5차) |
| kafka-lab | lab | 없음 | 01 |  | rebalance(L-messaging-40 5차), durability(L-messaging-41 5차) |
| kv-store | build | 없음 | 04 |  | 01-basic(B-kv-store-10 4차), 02-log(B-kv-store-10 4차), 03-transactions(B-kv-store-10 4차) |
| leaderboard | build | 없음 | 04 |  | 01-sorted-set(B-system-design-130 4차), 02-range-queries(B-system-design-130 4차), 03-sharding(B-system-design-130 4차) |
| ledger | build | 없음 | 04 |  | 01-double-entry(B-system-design-40 3차), 02-psp-idempotency(B-system-design-40 3차), 03-reconciliation(B-system-design-40 3차) |
| lsm | build | 없음 | 04 |  | 01-memtable(B-database-30 4차), 02-compaction(B-database-30 4차), 03-bloom-and-range(B-database-30 4차) |
| matching-engine | build | 없음 | 04 |  | 01-order-book(B-system-design-50 3차), 02-order-types(B-system-design-50 3차), 03-replay(B-system-design-50 3차) |
| mcp-server | build | 없음 | 04 |  | 01-tools(B-ai-agent-40 3차), 02-resources-prompts(B-ai-agent-40 3차), 03-errors-and-versions(B-ai-agent-40 3차) |
| metrics | build | 있음 | 04 | 01-counters(B-observability-20 완료), 02-histograms(B-observability-20 완료), 03-slo-alerts(B-observability-20 완료) |  |
| mini-dfs | build | 있음 | 04 | 01-namespace(B-hdfs-20 완료), 02-placement(B-hdfs-20 완료), 03-recovery(B-hdfs-20 완료) |  |
| mini-log | build | 있음 | 05 | 01-partitions(B-messaging-20 완료), 02-idempotence(B-messaging-20 완료), 03-groups(B-messaging-20 완료), 04-compaction(B-messaging-20 완료) |  |
| mini-olap | build | 있음 | 06 | 01-aggregate(B-olap-20 완료), 02-scale(B-olap-20 완료), 03-rollup(B-olap-20 완료) | 04-sketch(B-olap-30 3차), 05-sparse-index(B-olap-30 3차) |
| mini-sql | build | 없음 | 04 |  | 01-select(B-mini-sql-10 5차), 02-group(B-mini-sql-10 5차), 03-join(B-mini-sql-10 5차) |
| mvcc | build | 있음 | 04 | 01-snapshot(B-database-20 완료), 02-locking(B-database-20 완료), 03-serializable(B-database-20 완료) |  |
| net-lab | lab | 없음 | 01 |  | keepalive-race(L-network-10 4차), timeout-layers(L-network-20 5차) |
| news-feed | build | 없음 | 04 |  | 01-fanout-on-write(B-system-design-70 4차), 02-fanout-on-read(B-system-design-70 4차), 03-hybrid(B-system-design-70 4차) |
| notifier | build | 없음 | 04 |  | 01-dispatch(B-system-design-150 5차), 02-reliability(B-system-design-150 5차), 03-throttling(B-system-design-150 5차) |
| nrt-index | build | 없음 | 04 |  | 01-refresh(B-search-30 3차), 02-durability(B-search-30 3차), 03-concurrency(B-search-30 3차) |
| object-store | build | 없음 | 04 |  | 01-versioning(B-system-design-160 5차), 02-multipart(B-system-design-160 5차), 03-durability(B-system-design-160 5차) |
| olap-lab | lab | 없음 | 01 |  | sorting-key(L-olap-40 4차), replacing-merge(L-olap-41 4차), too-many-parts(L-olap-42 5차) |
| raft | build | 있음 | 04 | 01-election(B-distributed-20 완료), 02-replication(B-distributed-20 완료), 03-commit(B-distributed-20 완료) |  |
| rag | build | 없음 | 05 |  | 01-chunking(B-ai-agent-30 2차), 02-retrieval(B-ai-agent-30 2차), 03-hybrid(B-ai-agent-30 2차), 04-eval(B-ai-agent-30 2차) |
| rate-limiter | build | 있음 | 04 | 01-buckets(B-system-design-20 완료), 02-windows(B-system-design-20 완료), 03-distributed(B-system-design-20 완료) |  |
| reading | reading | 있음 | 04 | 01-auction(R-economics-10 완료), 02-agency(R-law-10 완료), 03-grue(R-philosophy-10 완료) | saussure(R-humanities-10 4차), enzyme(R-science-10 4차), key-exchange(R-technology-10 4차), perspective(R-art-10 5차), source-criticism(R-humanities-20 5차), falsification(R-interdisciplinary-10 5차), carnot(R-science-20 5차), plate-tectonics(R-science-30 5차), rescission(R-society-10 5차), monetary-policy(R-society-20 5차), prospect-theory(R-society-30 5차), congestion-control(R-technology-20 5차) |
| regex | build | 없음 | 04 |  | 01-basic(B-regex-10 5차), 02-classes(B-regex-10 5차), 03-linear(B-regex-10 5차) |
| reservation | build | 없음 | 04 |  | 01-inventory(B-system-design-120 4차), 02-idempotency(B-system-design-120 4차), 03-concurrency(B-system-design-120 4차) |
| search-engine | build | 있음 | 07 | 01-boolean(B-search-engine-10 완료), 02-ranking(B-search-engine-10 완료), 03-phrase(B-search-engine-10 완료), 04-fuzzy(B-search-engine-20 완료), 05-suggest(B-search-engine-30 완료), 06-snippet(B-search-engine-40 완료) |  |
| sim-testing | build | 없음 | 04 |  | 01-scheduler(B-distributed-50 5차), 02-faults(B-distributed-50 5차), 03-shrink(B-distributed-50 5차) |
| spark-lab | lab | 없음 | 01 |  | null-semantics(L-spark-30 4차), skew-and-aqe(L-spark-31 4차), overwrite-and-small-files(L-spark-32 4차), retries-and-side-effects(L-spark-33 5차), streaming-watermark(L-spark-34 5차) |
| spreadsheet | build | 없음 | 04 |  | 01-formula(B-spreadsheet-10 5차), 02-recalc(B-spreadsheet-10 5차), 03-cycles(B-spreadsheet-10 5차) |
| spring-lab | lab | 없음 | 01 |  | pool-deadlock(L-spring-10 4차), open-in-view(L-spring-20 4차), proxy-pitfalls(L-spring-30 4차), async-executor(L-spring-40 4차), http-client-pool(L-spring-50 4차), max-lifetime(L-spring-60 5차), virtual-threads(L-spring-70 5차), n-plus-one(L-spring-80 5차), batch-insert(L-spring-90 5차), scheduler(L-spring-100 5차) |
| table-format | build | 있음 | 04 | 01-snapshots(B-lakehouse-30 완료), 02-commits(B-lakehouse-30 완료), 03-deletes(B-lakehouse-30 완료) |  |
| tech-reading | reading | 있음 | 13 | 01-agent-loop(R-ai-agent-10 완료), 02-hdfs-write-path(R-hdfs-10 완료), 03-columnar-execution(R-olap-10 완료), 04-near-real-time-search(R-search-10 완료), 05-spark-stages(R-spark-10 완료), 06-coroutines(R-coroutines-10 완료), 07-capacity-estimation(R-system-design-10 완료), 08-mvcc-isolation(R-database-10 완료), 09-raft(R-distributed-10 완료), 10-table-format(R-lakehouse-10 완료), 11-kafka-replication(R-messaging-10 완료), 12-percentiles-and-slo(R-observability-10 완료) | online-resharding(R-database-40 4차) |
| url-shortener | build | 없음 | 04 |  | 01-base62(B-system-design-140 5차), 02-collisions(B-system-design-140 5차), 03-expiry(B-system-design-140 5차) |

## 도메인별 카드

### 읽기

도메인 안내: [reading/README.md](reading/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-art-10](reading/cards/R-art-10-perspective.md) | 원근법과 시점 | reading | reading | perspective | 후보 | 5 | 없음 |  |
| [R-economics-10](reading/cards/R-economics-10-auction.md) | 경매의 설계 | reading | reading | 01-auction | 완료(2026-10-07) | 1 | 없음 |  |
| [R-humanities-10](reading/cards/R-humanities-10-saussure.md) | 소쉬르의 기호 이론 | reading | reading | saussure | 후보 | 4 | 없음 |  |
| [R-humanities-20](reading/cards/R-humanities-20-source-criticism.md) | 사료 비판 | reading | reading | source-criticism | 후보 | 5 | 없음 |  |
| [R-interdisciplinary-10](reading/cards/R-interdisciplinary-10-falsification.md) | 반증주의와 뒤엠-콰인 논제 | reading | reading | falsification | 후보 | 5 | 없음 |  |
| [R-law-10](reading/cards/R-law-10-agency.md) | 대리와 표현대리 | reading | reading | 02-agency | 완료(2026-10-07) | 1 | 없음 |  |
| [R-philosophy-10](reading/cards/R-philosophy-10-grue.md) | 귀납의 새로운 수수께끼 | reading | reading | 03-grue | 완료(2026-10-07) | 1 | 없음 |  |
| [R-science-10](reading/cards/R-science-10-enzyme.md) | 효소 반응 속도와 저해 | reading | reading | enzyme | 후보 | 4 | 없음 |  |
| [R-science-20](reading/cards/R-science-20-carnot.md) | 카르노 기관과 열역학 제2법칙 | reading | reading | carnot | 후보 | 5 | 없음 |  |
| [R-science-30](reading/cards/R-science-30-plate-tectonics.md) | 판 구조론 | reading | reading | plate-tectonics | 후보 | 5 | 없음 |  |
| [R-society-10](reading/cards/R-society-10-rescission.md) | 계약의 해제와 원상회복 | reading | reading | rescission | 후보 | 5 | 없음 |  |
| [R-society-20](reading/cards/R-society-20-monetary-policy.md) | 통화정책의 전달 경로와 테일러 준칙 | reading | reading | monetary-policy | 후보 | 5 | 없음 |  |
| [R-society-30](reading/cards/R-society-30-prospect-theory.md) | 전망 이론과 프레이밍 | reading | reading | prospect-theory | 후보 | 5 | 없음 |  |
| [R-technology-10](reading/cards/R-technology-10-key-exchange.md) | 공개키 암호와 디피-헬먼 키 교환 | reading | reading | key-exchange | 후보 | 4 | 없음 |  |
| [R-technology-20](reading/cards/R-technology-20-congestion-control.md) | TCP 혼잡 제어 | reading | reading | congestion-control | 후보 | 5 | 없음 |  |

### 구현

도메인 안내: [implementation/README.md](implementation/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [C-kotlin-10](implementation/cards/C-kotlin-10-price-rules.md) | 가격 규칙 계산 | code | code-reading | 01-price-rules | 완료(2026-10-08) | 1 | 없음 |  |
| [F-kotlin-10](implementation/cards/F-kotlin-10-cursor-pagination.md) | 커서 페이지네이션 | fix | bug-fix | 01-cursor-pagination | 완료(2026-10-08) | 1 | 없음 |  |
| [C-kotlin-20](implementation/cards/C-kotlin-20-stock-reservation.md) | 재고 예약과 만료 | code | code-reading | stock-reservation | 후보 | 1 | 없음 |  |
| [F-kotlin-20](implementation/cards/F-kotlin-20-point-accrual.md) | 포인트 적립 계산 | fix | bug-fix | point-accrual | 후보 | 1 | 없음 |  |
| [C-kotlin-30](implementation/cards/C-kotlin-30-order-events.md) | 주문 이벤트 처리 | code | code-reading | order-events | 후보 | 1 | 없음 |  |
| [F-kotlin-30](implementation/cards/F-kotlin-30-daily-limit.md) | 일일 사용 한도 | fix | bug-fix | daily-limit | 후보 | 1 | 없음 |  |
| [C-kotlin-40](implementation/cards/C-kotlin-40-lru-cache.md) | 상품 상세 로컬 캐시 | code | code-reading | lru-cache | 후보 | 4 | 없음 |  |
| [F-kotlin-40](implementation/cards/F-kotlin-40-option-equality.md) | 장바구니 상품 옵션 비교 | fix | bug-fix | option-equality | 후보 | 4 | 없음 |  |
| [C-kotlin-50](implementation/cards/C-kotlin-50-retry-policy.md) | 외부 결제 호출 재시도 정책 | code | code-reading | retry-policy | 후보 | 4 | 없음 |  |
| [F-kotlin-50](implementation/cards/F-kotlin-50-comparator-contract.md) | 추천 점수 정렬 | fix | bug-fix | comparator-contract | 후보 | 4 | 없음 |  |
| [C-kotlin-60](implementation/cards/C-kotlin-60-search-query-parser.md) | 검색어 파서 | code | code-reading | search-query-parser | 후보 | 5 | 없음 |  |
| [F-kotlin-60](implementation/cards/F-kotlin-60-sublist-view.md) | 페이지 단위 일괄 처리 | fix | bug-fix | sublist-view | 후보 | 5 | 없음 |  |
| [C-kotlin-70](implementation/cards/C-kotlin-70-settlement-batch.md) | 정산 배치 | code | code-reading | settlement-batch | 후보 | 5 | 없음 |  |
| [F-kotlin-70](implementation/cards/F-kotlin-70-locale-format.md) | 가격 문자열 출력 | fix | bug-fix | locale-format | 후보 | 5 | 없음 |  |
| [C-kotlin-80](implementation/cards/C-kotlin-80-csv-import.md) | 상품 일괄 등록 | code | code-reading | csv-import | 후보 | 5 | 없음 |  |
| [F-kotlin-80](implementation/cards/F-kotlin-80-code-pattern.md) | 상품 코드 검증 | fix | bug-fix | code-pattern | 후보 | 5 | 없음 |  |
| [F-kotlin-90](implementation/cards/F-kotlin-90-retry-duplicate.md) | 주문 상태 변경 기록 | fix | bug-fix | retry-duplicate | 후보 | 5 | 없음 |  |
| [F-kotlin-100](implementation/cards/F-kotlin-100-cache-key.md) | 회원 등급별 가격 캐시 | fix | bug-fix | cache-key | 후보 | 5 | 없음 |  |
| [F-kotlin-110](implementation/cards/F-kotlin-110-float-threshold.md) | 무료 배송 기준 금액 | fix | bug-fix | float-threshold | 후보 | 5 | 없음 |  |
| [F-kotlin-120](implementation/cards/F-kotlin-120-sale-window.md) | 타임세일 시작과 끝 | fix | bug-fix | sale-window | 후보 | 5 | 없음 |  |
| [B-kv-store-10](implementation/cards/B-kv-store-10-kv-store.md) | 키-값 저장소 | build | kv-store | 01-basic, 02-log, 03-transactions | 후보 | 4 | 없음 |  |
| [B-mini-sql-10](implementation/cards/B-mini-sql-10-mini-sql.md) | 작은 SQL 엔진 | build | mini-sql | 01-select, 02-group, 03-join | 후보 | 5 | 없음 |  |
| [B-regex-10](implementation/cards/B-regex-10-regex.md) | 정규식 엔진 | build | regex | 01-basic, 02-classes, 03-linear | 후보 | 5 | 없음 |  |
| [B-search-engine-10](implementation/cards/B-search-engine-10-search-engine.md) | 나만의 검색 엔진 | build | search-engine | 01-boolean, 02-ranking, 03-phrase | 완료(2026-10-07) | 1 | 없음 |  |
| [B-search-engine-20](implementation/cards/B-search-engine-20-fuzzy.md) | 오타 허용 검색 | build | search-engine | 04-fuzzy | 완료(2026-10-09) | 4 | 없음 |  |
| [B-search-engine-30](implementation/cards/B-search-engine-30-suggest.md) | 자동 완성 | build | search-engine | 05-suggest | 완료(2026-10-09) | 4 | 없음 | B-search-engine-20 |
| [B-search-engine-40](implementation/cards/B-search-engine-40-snippet.md) | 검색 결과 발췌 | build | search-engine | 06-snippet | 완료(2026-10-09) | 4 | 없음 | B-search-engine-30 |
| [B-spreadsheet-10](implementation/cards/B-spreadsheet-10-spreadsheet.md) | 스프레드시트 계산기 | build | spreadsheet | 01-formula, 02-recalc, 03-cycles | 후보 | 5 | 없음 |  |

### AI 에이전트

도메인 안내: [backend/ai-agent/README.md](backend/ai-agent/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-ai-agent-10](backend/ai-agent/cards/R-ai-agent-10-agent-loop.md) | 에이전트 루프와 도구 호출 | reading | tech-reading | 01-agent-loop | 완료(2026-10-09) | 2 | 없음 |  |
| [B-ai-agent-20](backend/ai-agent/cards/B-ai-agent-20-agent-runtime.md) | 에이전트 실행기 만들기 | build | agent-runtime | 01-loop, 02-schema, 03-parallel, 04-context, 05-guard | 완료(2026-10-10) | 2 | 없음 | R-ai-agent-10 |
| [B-ai-agent-30](backend/ai-agent/cards/B-ai-agent-30-rag.md) | 검색 증강 파이프라인 만들기 | build | rag | 01-chunking, 02-retrieval, 03-hybrid, 04-eval | 후보 | 2 | 없음 |  |
| [B-ai-agent-40](backend/ai-agent/cards/B-ai-agent-40-mcp-server.md) | MCP 서버 만들기 | build | mcp-server | 01-tools, 02-resources-prompts, 03-errors-and-versions | 후보 | 3 | 없음 | B-ai-agent-20 |
| [L-ai-agent-50](backend/ai-agent/cards/L-ai-agent-50-real-model.md) | 실제 모델로 도구 루프 평가하기 | lab | agent-lab | real-model | 후보 | 5 | llm-api | B-ai-agent-20 |

### Kotlin 코루틴

도메인 안내: [backend/coroutines/README.md](backend/coroutines/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-coroutines-10](backend/coroutines/cards/R-coroutines-10-coroutines.md) | 코루틴과 구조적 동시성 | reading | tech-reading | 06-coroutines | 완료(2026-10-09) | 3 | 없음 |  |
| [B-coroutines-20](backend/coroutines/cards/B-coroutines-20-coroutine-patterns.md) | 코루틴으로 동시 처리 짜기 | build | coroutine-patterns | 01-fan-out, 02-limits, 03-channels, 04-flow | 완료(2026-10-12) | 3 | kotlinx-coroutines | R-coroutines-10 |
| [C-coroutines-30](backend/coroutines/cards/C-coroutines-30-coroutine-order.md) | 코루틴 실행 순서와 디스패처 | code | code-reading | 02-coroutine-order | 완료(2026-10-12) | 3 | kotlinx-coroutines | B-coroutines-20 |
| [F-coroutines-31](backend/coroutines/cards/F-coroutines-31-order-deadline.md) | 주문 확정 제한 시간 | fix | bug-fix | 02-order-deadline | 완료(2026-10-12) | 3 | kotlinx-coroutines | B-coroutines-20 |
| [C-coroutines-32](backend/coroutines/cards/C-coroutines-32-flow-semantics.md) | StateFlow 와 SharedFlow 의 의미 | code | code-reading | 03-flow-semantics | 완료(2026-10-12) | 4 | kotlinx-coroutines | C-coroutines-30 |
| [B-coroutines-35](backend/coroutines/cards/B-coroutines-35-coroutine-patterns-advanced.md) | 코루틴 동시 처리 심화 | build | coroutine-patterns | 05-batching, 06-single-flight | 완료(2026-10-13) | 4 | kotlinx-coroutines | B-coroutines-20 |
| [B-coroutines-40](backend/coroutines/cards/B-coroutines-40-coroutine-runtime.md) | 작은 코루틴 실행기 만들기 | build | coroutine-runtime | 01-event-loop, 02-structured, 03-cancellation, 04-exceptions, 05-channels, 06-flow, 07-sequence | 완료(2026-10-13) | 4 | 없음 | B-coroutines-20 |
| [L-coroutines-50](backend/coroutines/cards/L-coroutines-50-dispatcher-starvation.md) | 블로킹 호출과 디스패처 고갈 | lab | coroutine-lab | 01-dispatcher-starvation | 완료(2026-10-13) | 4 | kotlinx-coroutines | B-coroutines-20 |

### 데이터베이스

도메인 안내: [backend/database/README.md](backend/database/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-database-10](backend/database/cards/R-database-10-mvcc-isolation.md) | MVCC 와 격리 수준 | reading | tech-reading | 08-mvcc-isolation | 완료(2026-10-10) | 4 | 없음 |  |
| [B-database-20](backend/database/cards/B-database-20-mvcc.md) | 트랜잭션 격리 엔진 만들기 | build | mvcc | 01-snapshot, 02-locking, 03-serializable | 완료(2026-10-10) | 3 | 없음 | R-database-10 |
| [B-database-30](backend/database/cards/B-database-30-lsm.md) | LSM 저장소 만들기 | build | lsm | 01-memtable, 02-compaction, 03-bloom-and-range | 후보 | 4 | 없음 |  |
| [R-database-40](backend/database/cards/R-database-40-online-resharding.md) | 서비스를 멈추지 않는 데이터 이전과 샤딩 | reading | tech-reading | online-resharding | 후보 | 4 | 없음 | R-database-10 |
| [L-database-50](backend/database/cards/L-database-50-gap-lock.md) | 갭 잠금과 삽입 교착 | lab | db-lab | gap-lock | 후보 | 4 | docker, maven | R-database-10 |
| [L-database-51](backend/database/cards/L-database-51-metadata-lock.md) | 긴 트랜잭션과 스키마 변경 대기 | lab | db-lab | metadata-lock | 후보 | 4 | docker, maven, k6 | R-database-10 |
| [L-database-52](backend/database/cards/L-database-52-index-usage.md) | 인덱스를 쓰지 못하는 질의 | lab | db-lab | index-usage | 후보 | 5 | docker, maven |  |
| [L-database-53](backend/database/cards/L-database-53-serializable-retry.md) | 직렬화 실패와 재시도 | lab | db-lab | serializable-retry | 후보 | 5 | docker, maven | B-database-20 |

### 분산 시스템

도메인 안내: [backend/distributed/README.md](backend/distributed/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-distributed-10](backend/distributed/cards/R-distributed-10-raft.md) | Raft 의 선출과 커밋 | reading | tech-reading | 09-raft | 완료(2026-10-10) | 4 | 없음 |  |
| [B-distributed-20](backend/distributed/cards/B-distributed-20-raft-build.md) | Raft 시뮬레이터 만들기 | build | raft | 01-election, 02-replication, 03-commit | 완료(2026-10-11) | 4 | 없음 | R-distributed-10 |
| [B-distributed-30](backend/distributed/cards/B-distributed-30-crdt.md) | 복제 자료형 만들기 | build | crdt | 01-counters, 02-sets-and-registers, 03-convergence | 후보 | 5 | 없음 |  |
| [B-distributed-40](backend/distributed/cards/B-distributed-40-consistent-hashing.md) | 일관 해싱 만들기 | build | consistent-hashing | 01-ring, 02-rebalance, 03-replication | 후보 | 4 | 없음 |  |
| [B-distributed-50](backend/distributed/cards/B-distributed-50-simulation-testing.md) | 결정적 시뮬레이션 시험 만들기 | build | sim-testing | 01-scheduler, 02-faults, 03-shrink | 후보 | 5 | 없음 | B-distributed-20 |

### HDFS

도메인 안내: [backend/hdfs/README.md](backend/hdfs/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-hdfs-10](backend/hdfs/cards/R-hdfs-10-hdfs-write-path.md) | HDFS 의 쓰기 경로와 복제 | reading | tech-reading | 02-hdfs-write-path | 완료(2026-10-09) | 2 | 없음 |  |
| [B-hdfs-20](backend/hdfs/cards/B-hdfs-20-mini-dfs.md) | 네임노드 시뮬레이터 만들기 | build | mini-dfs | 01-namespace, 02-placement, 03-recovery | 완료(2026-10-10) | 2 | 없음 | R-hdfs-10 |

### 장애 보고서 독해

도메인 안내: [backend/incidents/README.md](backend/incidents/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-incidents-10](backend/incidents/cards/R-incidents-10-cloudflare-regex.md) | 정규식 하나가 멈춘 전 세계 프록시 | reading | incident-reading | cloudflare-regex | 후보 | 3 | 없음 |  |
| [R-incidents-20](backend/incidents/cards/R-incidents-20-github-split-writes.md) | 43초 분할이 만든 하루 동안의 장애 | reading | incident-reading | github-split-writes | 후보 | 3 | 없음 |  |
| [R-incidents-30](backend/incidents/cards/R-incidents-30-s3-capacity-removal.md) | 잘못 입력한 용량 제거 명령 | reading | incident-reading | s3-capacity-removal | 후보 | 3 | 없음 |  |
| [R-incidents-40](backend/incidents/cards/R-incidents-40-meta-backbone.md) | 스스로 사라진 DNS | reading | incident-reading | meta-backbone | 후보 | 4 | 없음 |  |
| [R-incidents-50](backend/incidents/cards/R-incidents-50-crowdstrike-channel-file.md) | 필드 하나가 모자랐던 콘텐츠 갱신 | reading | incident-reading | crowdstrike-channel-file | 후보 | 3 | 없음 |  |
| [R-incidents-60](backend/incidents/cards/R-incidents-60-gcp-service-control.md) | 기능 플래그 없는 코드 경로와 빈 필드 | reading | incident-reading | gcp-service-control | 후보 | 3 | 없음 |  |
| [R-incidents-70](backend/incidents/cards/R-incidents-70-slack-cold-start.md) | 연휴 뒤 첫 월요일의 급증 | reading | incident-reading | slack-cold-start | 후보 | 4 | 없음 |  |

### JVM

도메인 안내: [backend/jvm/README.md](backend/jvm/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [L-jvm-10](backend/jvm/cards/L-jvm-10-container-heap.md) | 메모리 한도와 기본 힙 크기 | lab | jvm-lab | container-heap | 후보 | 4 | 없음 |  |
| [L-jvm-20](backend/jvm/cards/L-jvm-20-threadlocal-leak.md) | 스레드 풀과 ThreadLocal | lab | jvm-lab | threadlocal-leak | 후보 | 4 | 없음 |  |
| [L-jvm-30](backend/jvm/cards/L-jvm-30-thread-dump.md) | 스레드 덤프로 교착 찾기 | lab | jvm-lab | thread-dump | 후보 | 4 | 없음 |  |
| [L-jvm-40](backend/jvm/cards/L-jvm-40-jit-warmup.md) | JIT 예열과 측정 함정 | lab | jvm-lab | jit-warmup | 후보 | 5 | 없음 |  |

### 레이크하우스: 파일 포맷과 테이블 포맷

도메인 안내: [backend/lakehouse/README.md](backend/lakehouse/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-lakehouse-10](backend/lakehouse/cards/R-lakehouse-10-table-format.md) | 테이블 포맷과 원자적 커밋 | reading | tech-reading | 10-table-format | 완료(2026-10-10) | 4 | 없음 |  |
| [B-lakehouse-20](backend/lakehouse/cards/B-lakehouse-20-columnar.md) | 열 지향 파일 만들기 | build | columnar | 01-encode, 02-pruning, 03-nested | 후보 | 4 | 없음 |  |
| [B-lakehouse-30](backend/lakehouse/cards/B-lakehouse-30-table-format-build.md) | 테이블 포맷 만들기 | build | table-format | 01-snapshots, 02-commits, 03-deletes | 완료(2026-10-11) | 4 | 없음 | R-lakehouse-10 |

### 부하 엣지 케이스

도메인 안내: [backend/load/README.md](backend/load/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [L-load-10](backend/load/cards/L-load-10-stock-race.md) | 재고 차감과 동시 주문 | lab | backend-lab | stock-race | 후보 | 3 | k6 |  |
| [L-load-20](backend/load/cards/L-load-20-idempotency.md) | 중복 요청과 멱등 키 | lab | backend-lab | idempotency | 후보 | 3 | k6 |  |
| [L-load-30](backend/load/cards/L-load-30-cache-stampede.md) | 캐시 스탬피드 | lab | backend-lab | cache-stampede | 후보 | 3 | k6, redis |  |
| [L-load-40](backend/load/cards/L-load-40-penetration-avalanche.md) | 캐시 관통과 동시 만료 | lab | backend-lab | penetration-avalanche | 후보 | 3 | k6, redis |  |
| [L-load-50](backend/load/cards/L-load-50-stale-set.md) | 캐시와 원천의 불일치 | lab | backend-lab | stale-set | 후보 | 3 | k6, redis |  |
| [L-load-60](backend/load/cards/L-load-60-cache-outage.md) | 캐시 장애와 원천 보호 | lab | backend-lab | cache-outage | 후보 | 3 | k6, redis |  |
| [L-load-70](backend/load/cards/L-load-70-retry-storm.md) | 타임아웃, 재시도, 백오프 | lab | backend-lab | retry-storm | 후보 | 5 | k6 |  |
| [L-load-80](backend/load/cards/L-load-80-load-shedding.md) | 과부하와 load shedding | lab | backend-lab | load-shedding | 후보 | 5 | k6 |  |
| [L-load-90](backend/load/cards/L-load-90-rate-limit.md) | 속도 제한 | lab | backend-lab | rate-limit | 후보 | 5 | k6, redis |  |
| [L-load-100](backend/load/cards/L-load-100-idempotent-consumer.md) | 멱등 소비자와 순서 | lab | backend-lab | idempotent-consumer | 보류(메시지 브로커 실행 방식 결정 필요) | 5 | docker, k6 |  |
| [L-load-110](backend/load/cards/L-load-110-outbox.md) | 트랜잭셔널 아웃박스 | lab | backend-lab | outbox | 보류(L-load-100 과 같은 이유) | 5 | docker, k6 |  |
| [L-load-120](backend/load/cards/L-load-120-adaptive-concurrency.md) | 적응형 동시성 제한 | lab | backend-lab | adaptive-concurrency | 후보 | 5 | k6 | L-load-80 |

### 메시징: Kafka

도메인 안내: [backend/messaging/README.md](backend/messaging/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-messaging-10](backend/messaging/cards/R-messaging-10-kafka-replication.md) | 복제와 전달 보장 | reading | tech-reading | 11-kafka-replication | 완료(2026-10-10) | 4 | 없음 |  |
| [B-messaging-20](backend/messaging/cards/B-messaging-20-mini-log.md) | 파티션 로그와 컨슈머 그룹 만들기 | build | mini-log | 01-partitions, 02-idempotence, 03-groups, 04-compaction | 완료(2026-10-11) | 3 | 없음 | R-messaging-10 |
| [C-messaging-30](backend/messaging/cards/C-messaging-30-consumer-commit.md) | 오프셋 커밋 시점과 중복 처리 | code | code-reading | consumer-commit | 후보 | 4 | 없음 | R-messaging-10 |
| [L-messaging-40](backend/messaging/cards/L-messaging-40-rebalance.md) | 느린 처리와 재조정 폭풍 | lab | kafka-lab | rebalance | 후보 | 5 | docker, maven | B-messaging-20 |
| [L-messaging-41](backend/messaging/cards/L-messaging-41-durability.md) | acks 와 최소 동기 복제본 | lab | kafka-lab | durability | 후보 | 5 | docker, maven | R-messaging-10 |

### 네트워크

도메인 안내: [backend/network/README.md](backend/network/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [L-network-10](backend/network/cards/L-network-10-keepalive-race.md) | keep-alive 수명의 경합 | lab | net-lab | keepalive-race | 후보 | 4 | 없음 |  |
| [L-network-20](backend/network/cards/L-network-20-timeout-layers.md) | 타임아웃의 층 | lab | net-lab | timeout-layers | 후보 | 5 | 없음 |  |

### 관측성

도메인 안내: [backend/observability/README.md](backend/observability/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-observability-10](backend/observability/cards/R-observability-10-percentiles-and-slo.md) | 백분위수와 오류 예산 | reading | tech-reading | 12-percentiles-and-slo | 완료(2026-10-10) | 4 | 없음 |  |
| [B-observability-20](backend/observability/cards/B-observability-20-metrics.md) | 지표 엔진 만들기 | build | metrics | 01-counters, 02-histograms, 03-slo-alerts | 완료(2026-10-11) | 5 | 없음 | R-observability-10 |

### OLAP

도메인 안내: [backend/olap/README.md](backend/olap/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-olap-10](backend/olap/cards/R-olap-10-columnar-execution.md) | 열 저장과 분석 질의 | reading | tech-reading | 03-columnar-execution | 완료(2026-10-09) | 2 | 없음 |  |
| [B-olap-20](backend/olap/cards/B-olap-20-mini-olap.md) | 열 지향 집계 엔진 만들기 | build | mini-olap | 01-aggregate, 02-scale, 03-rollup | 완료(2026-10-10) | 2 | 없음 | R-olap-10 |
| [B-olap-30](backend/olap/cards/B-olap-30-sketch-and-sparse-index.md) | 근사 집계와 희소 인덱스 | build | mini-olap | 04-sketch, 05-sparse-index | 후보 | 3 | 없음 | B-olap-20 |
| [L-olap-40](backend/olap/cards/L-olap-40-sorting-key.md) | 정렬 키와 읽는 그래뉼 수 | lab | olap-lab | sorting-key | 후보 | 4 | docker | R-olap-10 |
| [L-olap-41](backend/olap/cards/L-olap-41-replacing-merge.md) | 병합 때만 지워지는 중복 | lab | olap-lab | replacing-merge | 후보 | 4 | docker | L-olap-40 |
| [L-olap-42](backend/olap/cards/L-olap-42-too-many-parts.md) | 작은 삽입과 파트 폭증 | lab | olap-lab | too-many-parts | 후보 | 5 | docker, k6 | L-olap-40 |

### 검색: Elasticsearch

도메인 안내: [backend/search/README.md](backend/search/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-search-10](backend/search/cards/R-search-10-near-real-time-search.md) | 거의 실시간 검색과 내구성 | reading | tech-reading | 04-near-real-time-search | 완료(2026-10-09) | 2 | 없음 |  |
| [B-search-20](backend/search/cards/B-search-20-es-cluster.md) | 분산 검색 조정 노드 만들기 | build | es-cluster | 01-routing, 02-terms-agg, 03-paging | 완료(2026-10-10) | 2 | 없음 | R-search-10 |
| [B-search-30](backend/search/cards/B-search-30-nrt-index.md) | 세그먼트와 translog 시뮬레이터 만들기 | build | nrt-index | 01-refresh, 02-durability, 03-concurrency | 후보 | 3 | 없음 | R-search-10 |
| [F-search-40](backend/search/cards/F-search-40-hangul-normalization.md) | 한글 정규화가 어긋난 상품 검색 | fix | bug-fix | hangul-normalization | 후보 | 4 | 없음 |  |
| [L-search-50](backend/search/cards/L-search-50-mapping-explosion.md) | 동적 매핑과 필드 폭증 | lab | es-lab | mapping-explosion | 후보 | 4 | docker | R-search-10 |
| [L-search-51](backend/search/cards/L-search-51-deep-paging.md) | 깊은 페이지 넘기기 | lab | es-lab | deep-paging | 후보 | 4 | docker | B-search-20 |
| [L-search-52](backend/search/cards/L-search-52-nori-analysis.md) | nori 분석기와 상품명 | lab | es-lab | nori-analysis | 후보 | 4 | docker | R-search-10 |
| [L-search-53](backend/search/cards/L-search-53-terms-agg-accuracy.md) | terms 집계의 오차 | lab | es-lab | terms-agg-accuracy | 후보 | 5 | docker | B-search-20 |

### Spark

도메인 안내: [backend/spark/README.md](backend/spark/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-spark-10](backend/spark/cards/R-spark-10-spark-stages.md) | Spark 의 실행 계획과 셔플 | reading | tech-reading | 05-spark-stages | 완료(2026-10-09) | 2 | 없음 |  |
| [B-spark-20](backend/spark/cards/B-spark-20-dataflow.md) | 데이터 흐름 엔진 만들기 | build | dataflow | 01-plan, 02-shuffle, 03-join, 04-adaptive | 완료(2026-10-10) | 2 | 없음 | R-spark-10 |
| [L-spark-30](backend/spark/cards/L-spark-30-null-semantics.md) | NULL, 중복, 열 순서의 의미론 | lab | spark-lab | null-semantics | 후보 | 4 | spark | R-spark-10 |
| [L-spark-31](backend/spark/cards/L-spark-31-skew-and-aqe.md) | 데이터 쏠림과 적응 실행 | lab | spark-lab | skew-and-aqe | 후보 | 4 | spark | B-spark-20 |
| [L-spark-32](backend/spark/cards/L-spark-32-overwrite-and-small-files.md) | 파티션 덮어쓰기와 작은 파일 | lab | spark-lab | overwrite-and-small-files | 후보 | 4 | spark | R-spark-10 |
| [L-spark-33](backend/spark/cards/L-spark-33-retries-and-side-effects.md) | 재실행과 부수효과 | lab | spark-lab | retries-and-side-effects | 후보 | 5 | spark | R-spark-10 |
| [L-spark-34](backend/spark/cards/L-spark-34-streaming-watermark.md) | 워터마크와 늦은 이벤트 | lab | spark-lab | streaming-watermark | 후보 | 5 | spark | R-spark-10 |

### 프레임워크 심화와 튜닝

도메인 안내: [backend/spring/README.md](backend/spring/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [L-spring-10](backend/spring/cards/L-spring-10-pool-deadlock.md) | REQUIRES_NEW 와 풀 교착 | lab | spring-lab | pool-deadlock | 후보 | 4 | maven |  |
| [L-spring-20](backend/spring/cards/L-spring-20-open-in-view.md) | OSIV 와 느린 외부 호출 | lab | spring-lab | open-in-view | 후보 | 4 | gradle, maven, k6 |  |
| [L-spring-30](backend/spring/cards/L-spring-30-proxy-pitfalls.md) | 트랜잭션 프록시의 함정 | lab | spring-lab | proxy-pitfalls | 후보 | 4 | maven |  |
| [L-spring-40](backend/spring/cards/L-spring-40-async-executor.md) | @Async 실행기와 큐 | lab | spring-lab | async-executor | 후보 | 4 | maven |  |
| [L-spring-50](backend/spring/cards/L-spring-50-http-client-pool.md) | HTTP 클라이언트 커넥션 풀 | lab | spring-lab | http-client-pool | 후보 | 4 | gradle, maven, k6 |  |
| [L-spring-60](backend/spring/cards/L-spring-60-max-lifetime.md) | maxLifetime 과 끊긴 연결 | lab | spring-lab | max-lifetime | 후보 | 5 | maven |  |
| [L-spring-70](backend/spring/cards/L-spring-70-virtual-threads.md) | 가상 스레드와 풀 | lab | spring-lab | virtual-threads | 후보 | 5 | gradle, maven, k6 |  |
| [L-spring-80](backend/spring/cards/L-spring-80-n-plus-one.md) | N+1 과 페이징 | lab | spring-lab | n-plus-one | 후보 | 5 | gradle, maven, k6 |  |
| [L-spring-90](backend/spring/cards/L-spring-90-batch-insert.md) | IDENTITY 와 배치 삽입 | lab | spring-lab | batch-insert | 후보 | 5 | maven |  |
| [L-spring-100](backend/spring/cards/L-spring-100-scheduler.md) | @Scheduled 단일 스레드 | lab | spring-lab | scheduler | 후보 | 5 | maven |  |

### 시스템 설계

도메인 안내: [backend/system-design/README.md](backend/system-design/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-system-design-10](backend/system-design/cards/R-system-design-10-capacity-estimation.md) | 규모 추정 | reading | tech-reading | 07-capacity-estimation | 완료(2026-10-10) | 3 | 없음 |  |
| [B-system-design-20](backend/system-design/cards/B-system-design-20-rate-limiter.md) | 처리율 제한기 만들기 | build | rate-limiter | 01-buckets, 02-windows, 03-distributed | 완료(2026-10-10) | 3 | 없음 |  |
| [B-system-design-30](backend/system-design/cards/B-system-design-30-id-generator.md) | 분산 고유 ID 생성기 만들기 | build | id-generator | 01-snowflake, 02-sharded-ids, 03-ordering | 후보 | 3 | 없음 |  |
| [B-system-design-40](backend/system-design/cards/B-system-design-40-ledger.md) | 결제 원장 만들기 | build | ledger | 01-double-entry, 02-psp-idempotency, 03-reconciliation | 후보 | 3 | 없음 |  |
| [B-system-design-50](backend/system-design/cards/B-system-design-50-matching-engine.md) | 주문 매칭 엔진 만들기 | build | matching-engine | 01-order-book, 02-order-types, 03-replay | 후보 | 3 | 없음 |  |
| [B-system-design-60](backend/system-design/cards/B-system-design-60-crawler.md) | 웹 크롤러 만들기 | build | crawler | 01-frontier, 02-dedup, 03-politeness | 후보 | 4 | 없음 |  |
| [B-system-design-70](backend/system-design/cards/B-system-design-70-news-feed.md) | 뉴스 피드 팬아웃 만들기 | build | news-feed | 01-fanout-on-write, 02-fanout-on-read, 03-hybrid | 후보 | 4 | 없음 |  |
| [B-system-design-80](backend/system-design/cards/B-system-design-80-chat.md) | 채팅 순서와 동기화 만들기 | build | chat | 01-sequencing, 02-sync, 03-presence | 후보 | 4 | 없음 |  |
| [B-system-design-90](backend/system-design/cards/B-system-design-90-autocomplete.md) | 검색어 자동완성 만들기 | build | autocomplete | 01-trie, 02-cached-top-k, 03-filters-and-shards | 후보 | 4 | 없음 |  |
| [B-system-design-100](backend/system-design/cards/B-system-design-100-geo-index.md) | 근처 장소 검색 만들기 | build | geo-index | 01-geohash, 02-radius-search, 03-quadtree | 후보 | 4 | 없음 |  |
| [B-system-design-110](backend/system-design/cards/B-system-design-110-ad-click-aggregator.md) | 광고 클릭 집계기 만들기 | build | ad-click-aggregator | 01-windows, 02-late-events, 03-exactly-once | 후보 | 4 | 없음 |  |
| [B-system-design-120](backend/system-design/cards/B-system-design-120-reservation.md) | 호텔 예약 재고 만들기 | build | reservation | 01-inventory, 02-idempotency, 03-concurrency | 후보 | 4 | 없음 |  |
| [B-system-design-130](backend/system-design/cards/B-system-design-130-leaderboard.md) | 실시간 순위표 만들기 | build | leaderboard | 01-sorted-set, 02-range-queries, 03-sharding | 후보 | 4 | 없음 |  |
| [B-system-design-140](backend/system-design/cards/B-system-design-140-url-shortener.md) | URL 단축기 만들기 | build | url-shortener | 01-base62, 02-collisions, 03-expiry | 후보 | 5 | 없음 | B-system-design-30 |
| [B-system-design-150](backend/system-design/cards/B-system-design-150-notifier.md) | 알림 발송기 만들기 | build | notifier | 01-dispatch, 02-reliability, 03-throttling | 후보 | 5 | 없음 |  |
| [B-system-design-160](backend/system-design/cards/B-system-design-160-object-store.md) | 객체 저장소 만들기 | build | object-store | 01-versioning, 02-multipart, 03-durability | 후보 | 5 | 없음 |  |

