# 카드 색인

`kotlin content/ideas/index.main.kts` 가 카드 front matter 로 만든 파일이다. 손으로 고치지 않는다. 카드 형식과 차수의 뜻은 [README.md](README.md) 에 있다.

## 요약

| 도메인 | 카드 | 후보 | 제작 중 | 완료 | 보류 |
|---|---|---|---|---|---|
| [읽기](reading/README.md) | 15 | 0 | 0 | 15 | 0 |
| [구현](implementation/README.md) | 32 | 6 | 0 | 26 | 0 |
| [AI 에이전트](backend/ai-agent/README.md) | 5 | 1 | 0 | 4 | 0 |
| [Kotlin 코루틴](backend/coroutines/README.md) | 9 | 0 | 0 | 9 | 0 |
| [데이터베이스](backend/database/README.md) | 8 | 0 | 0 | 8 | 0 |
| [설계 패턴](backend/design-patterns/README.md) | 11 | 1 | 0 | 10 | 0 |
| [분산 시스템](backend/distributed/README.md) | 8 | 3 | 0 | 5 | 0 |
| [HDFS](backend/hdfs/README.md) | 2 | 0 | 0 | 2 | 0 |
| [장애 보고서 독해](backend/incidents/README.md) | 7 | 0 | 0 | 7 | 0 |
| [JVM](backend/jvm/README.md) | 4 | 0 | 0 | 4 | 0 |
| [레이크하우스: 파일 포맷과 테이블 포맷](backend/lakehouse/README.md) | 3 | 1 | 0 | 2 | 0 |
| [부하 엣지 케이스](backend/load/README.md) | 12 | 0 | 0 | 10 | 2 |
| [메시징: Kafka](backend/messaging/README.md) | 5 | 0 | 0 | 5 | 0 |
| [네트워크](backend/network/README.md) | 2 | 0 | 0 | 2 | 0 |
| [관측성](backend/observability/README.md) | 2 | 0 | 0 | 2 | 0 |
| [OLAP](backend/olap/README.md) | 6 | 0 | 0 | 6 | 0 |
| [데이터 파이프라인](backend/pipeline/README.md) | 9 | 5 | 0 | 4 | 0 |
| [검색: Elasticsearch](backend/search/README.md) | 11 | 1 | 0 | 10 | 0 |
| [Spark](backend/spark/README.md) | 7 | 0 | 0 | 7 | 0 |
| [프레임워크 심화와 튜닝](backend/spring/README.md) | 10 | 0 | 0 | 10 | 0 |
| [시스템 설계](backend/system-design/README.md) | 17 | 7 | 0 | 10 | 0 |
| 합계 | 185 | 25 | 0 | 158 | 2 |

## 다음 후보

상태가 후보인 카드를 차수 순서로 늘어놓았다. 같은 차수에서는 추가 환경이 필요 없는 카드가 먼저다.

| 차수 | 카드 | 제목 | 종류 | 트랙 | 단계 | 환경 |
|---|---|---|---|---|---|---|
| 4 | [B-kv-store-10](implementation/cards/B-kv-store-10-kv-store.md) | 키-값 저장소 | build | kv-store | 01-basic, 02-log, 03-transactions | 없음 |
| 4 | [B-lakehouse-20](backend/lakehouse/cards/B-lakehouse-20-columnar.md) | 열 지향 파일 만들기 | build | columnar | 01-encode, 02-pruning, 03-nested | 없음 |
| 4 | [B-pipeline-10](backend/pipeline/cards/B-pipeline-10-history-rollover.md) | 현재 상태를 보존하는 이력 압축기 만들기 | build | history-rollover | 01-materialize, 02-baseline, 03-retention | 없음 |
| 4 | [B-pipeline-30](backend/pipeline/cards/B-pipeline-30-batch-workflow.md) | 실행 증거로 연결하는 배치 단계 만들기 | build | batch-workflow | 01-markers, 02-resume, 03-seal | 없음 |
| 4 | [B-pipeline-50](backend/pipeline/cards/B-pipeline-50-index-overlay.md) | 운영과 실험의 변경을 합치는 병합기 만들기 | build | index-overlay | 01-current-state, 02-round-merge, 03-replay | 없음 |
| 4 | [B-record-range-10](implementation/cards/B-record-range-10-record-range.md) | 레코드 경계를 지키는 파일 분할기 만들기 | build | record-range | 01-lines, 02-json-ranges, 03-range-read | 없음 |
| 4 | [B-search-60](backend/search/cards/B-search-60-result-placement.md) | 제약을 만족하는 검색 결과 배치기 만들기 | build | result-placement | 01-ordered, 02-constraints, 03-exact-search | 없음 |
| 4 | [B-system-design-60](backend/system-design/cards/B-system-design-60-crawler.md) | 웹 크롤러 만들기 | build | crawler | 01-frontier, 02-dedup, 03-politeness | 없음 |
| 4 | [B-system-design-80](backend/system-design/cards/B-system-design-80-chat.md) | 채팅 순서와 동기화 만들기 | build | chat | 01-sequencing, 02-sync, 03-presence | 없음 |
| 4 | [B-system-design-100](backend/system-design/cards/B-system-design-100-geo-index.md) | 근처 장소 검색 만들기 | build | geo-index | 01-geohash, 02-radius-search, 03-quadtree | 없음 |
| 4 | [B-system-design-110](backend/system-design/cards/B-system-design-110-ad-click-aggregator.md) | 광고 클릭 집계기 만들기 | build | ad-click-aggregator | 01-windows, 02-late-events, 03-exactly-once | 없음 |
| 5 | [B-algorithm-drills-10](implementation/cards/B-algorithm-drills-10-algorithm-drills.md) | 주제별 알고리즘 문제 묶음 | build | algorithm-drills | 01-strings, 02-arrays, 03-sort-search, 04-structures, 05-grids-recursion | 없음 |
| 5 | [B-design-patterns-80](backend/design-patterns/cards/B-design-patterns-80-doc-editor.md) | 되돌리기를 지원하는 문서 편집기 만들기 | build | doc-editor | 01-edit, 02-undo, 03-macro, 04-snapshot | 없음 |
| 5 | [B-distributed-30](backend/distributed/cards/B-distributed-30-crdt.md) | 복제 자료형 만들기 | build | crdt | 01-counters, 02-sets-and-registers, 03-convergence | 없음 |
| 5 | [B-distributed-50](backend/distributed/cards/B-distributed-50-simulation-testing.md) | 결정적 시뮬레이션 시험 만들기 | build | sim-testing | 01-scheduler, 02-faults, 03-shrink | 없음 |
| 5 | [B-distributed-80](backend/distributed/cards/B-distributed-80-fenced-writes.md) | 저장소가 검사하는 펜싱 토큰 만들기 | build | lease-lock | 03-fenced-writes | 없음 |
| 5 | [B-mini-sql-10](implementation/cards/B-mini-sql-10-mini-sql.md) | 작은 SQL 엔진 | build | mini-sql | 01-select, 02-group, 03-join | 없음 |
| 5 | [B-pipeline-70](backend/pipeline/cards/B-pipeline-70-experiment-recovery.md) | 실험 종료 뒤 운영 상태로 복구하는 도구 만들기 | build | index-overlay | 04-reconcile | 없음 |
| 5 | [B-pipeline-80](backend/pipeline/cards/B-pipeline-80-dedup-candidates.md) | 중복 후보 생성과 대표 선택기 만들기 | build | dedup-candidates | 01-exact, 02-candidates, 03-components | 없음 |
| 5 | [B-regex-10](implementation/cards/B-regex-10-regex.md) | 정규식 엔진 | build | regex | 01-basic, 02-classes, 03-linear | 없음 |
| 5 | [B-spreadsheet-10](implementation/cards/B-spreadsheet-10-spreadsheet.md) | 스프레드시트 계산기 | build | spreadsheet | 01-formula, 02-recalc, 03-cycles | 없음 |
| 5 | [B-system-design-140](backend/system-design/cards/B-system-design-140-url-shortener.md) | URL 단축기 만들기 | build | url-shortener | 01-base62, 02-collisions, 03-expiry | 없음 |
| 5 | [B-system-design-150](backend/system-design/cards/B-system-design-150-notifier.md) | 알림 발송기 만들기 | build | notifier | 01-dispatch, 02-reliability, 03-throttling | 없음 |
| 5 | [B-system-design-160](backend/system-design/cards/B-system-design-160-object-store.md) | 객체 저장소 만들기 | build | object-store | 01-versioning, 02-multipart, 03-durability | 없음 |
| 5 | [L-ai-agent-50](backend/ai-agent/cards/L-ai-agent-50-real-model.md) | 실제 모델로 도구 루프 평가하기 | lab | agent-lab | real-model | llm-api |

## 트랙

컨텐츠 칸은 `content/tracks/<트랙>/track.json` 이 있는지를 보여 준다. 다음 번호는 단계 하나짜리 카드를 제작 중으로 바꿀 때 붙일 번호다. 예정 단계는 차수 순서다.

| 트랙 | 종류 | 컨텐츠 | 다음 번호 | 만든 단계 | 예정 단계 |
|---|---|---|---|---|---|
| ad-click-aggregator | build | 없음 | 04 |  | 01-windows(B-system-design-110 4차), 02-late-events(B-system-design-110 4차), 03-exactly-once(B-system-design-110 4차) |
| agent-lab | lab | 없음 | 01 |  | real-model(L-ai-agent-50 5차) |
| agent-runtime | build | 있음 | 06 | 01-loop(B-ai-agent-20 완료), 02-schema(B-ai-agent-20 완료), 03-parallel(B-ai-agent-20 완료), 04-context(B-ai-agent-20 완료), 05-guard(B-ai-agent-20 완료) |  |
| algorithm-drills | build | 없음 | 06 |  | 01-strings(B-algorithm-drills-10 5차), 02-arrays(B-algorithm-drills-10 5차), 03-sort-search(B-algorithm-drills-10 5차), 04-structures(B-algorithm-drills-10 5차), 05-grids-recursion(B-algorithm-drills-10 5차) |
| autocomplete | build | 있음 | 04 | 01-trie(B-system-design-90 완료), 02-cached-top-k(B-system-design-90 완료), 03-filters-and-shards(B-system-design-90 완료) |  |
| backend-lab | lab | 있음 | 11 | 01-stock-race(L-load-10 완료), 02-idempotency(L-load-20 완료), 03-cache-stampede(L-load-30 완료), 04-penetration-avalanche(L-load-40 완료), 05-stale-set(L-load-50 완료), 06-cache-outage(L-load-60 완료), 07-retry-storm(L-load-70 완료), 08-load-shedding(L-load-80 완료), 09-rate-limit(L-load-90 완료), 10-adaptive-concurrency(L-load-120 완료) | idempotent-consumer(L-load-100 5차), outbox(L-load-110 5차) |
| batch-workflow | build | 없음 | 04 |  | 01-markers(B-pipeline-30 4차), 02-resume(B-pipeline-30 4차), 03-seal(B-pipeline-30 4차) |
| bug-fix | fix | 있음 | 27 | 01-cursor-pagination(F-kotlin-10 완료), 02-order-deadline(F-coroutines-31 완료), 03-skipped-subscriber(F-design-patterns-60 완료), 04-stale-redo(F-design-patterns-70 완료), 05-instant-finish(F-coroutines-34 완료), 06-point-accrual(F-kotlin-20 완료), 07-daily-limit(F-kotlin-30 완료), 08-option-equality(F-kotlin-40 완료), 09-comparator-contract(F-kotlin-50 완료), 10-byte-offset(F-kotlin-130 완료), 11-short-read(F-kotlin-140 완료), 12-late-lock-release(F-distributed-70 완료), 13-rollover-delete-scope(F-pipeline-20 완료), 14-missing-chunk(F-pipeline-40 완료), 15-replay-regression(F-pipeline-60 완료), 16-hangul-normalization(F-search-40 완료), 17-fallback-constraints(F-search-70 완료), 18-sublist-view(F-kotlin-60 완료), 19-locale-format(F-kotlin-70 완료), 20-code-pattern(F-kotlin-80 완료), 21-retry-duplicate(F-kotlin-90 완료), 22-cache-key(F-kotlin-100 완료), 23-float-threshold(F-kotlin-110 완료), 24-sale-window(F-kotlin-120 완료), 25-double-start(F-design-patterns-100 완료), 26-unchecked-wakeup(F-design-patterns-110 완료) |  |
| chat | build | 없음 | 04 |  | 01-sequencing(B-system-design-80 4차), 02-sync(B-system-design-80 4차), 03-presence(B-system-design-80 4차) |
| code-reading | code | 있음 | 16 | 01-price-rules(C-kotlin-10 완료), 02-coroutine-order(C-coroutines-30 완료), 03-flow-semantics(C-coroutines-32 완료), 04-stock-reservation(C-kotlin-20 완료), 05-order-events(C-kotlin-30 완료), 06-lru-cache(C-kotlin-40 완료), 07-retry-policy(C-kotlin-50 완료), 08-decorated-client(C-design-patterns-40 완료), 09-state-observers(C-design-patterns-50 완료), 10-consumer-commit(C-messaging-30 완료), 11-search-query-parser(C-kotlin-60 완료), 12-settlement-batch(C-kotlin-70 완료), 13-csv-import(C-kotlin-80 완료), 14-notification-queue(C-design-patterns-90 완료), 15-stable-diversity(C-search-80 완료) |  |
| columnar | build | 없음 | 04 |  | 01-encode(B-lakehouse-20 4차), 02-pruning(B-lakehouse-20 4차), 03-nested(B-lakehouse-20 4차) |
| consistent-hashing | build | 있음 | 04 | 01-ring(B-distributed-40 완료), 02-rebalance(B-distributed-40 완료), 03-replication(B-distributed-40 완료) |  |
| coroutine-lab | lab | 있음 | 02 | 01-dispatcher-starvation(L-coroutines-50 완료) |  |
| coroutine-patterns | build | 있음 | 07 | 01-fan-out(B-coroutines-20 완료), 02-limits(B-coroutines-20 완료), 03-channels(B-coroutines-20 완료), 04-flow(B-coroutines-20 완료), 05-batching(B-coroutines-35 완료), 06-single-flight(B-coroutines-35 완료) |  |
| coroutine-runtime | build | 있음 | 09 | 01-event-loop(B-coroutines-40 완료), 02-structured(B-coroutines-40 완료), 03-cancellation(B-coroutines-40 완료), 04-exceptions(B-coroutines-40 완료), 05-channels(B-coroutines-40 완료), 06-flow(B-coroutines-40 완료), 07-sequence(B-coroutines-40 완료), 08-manual-state-machine(B-coroutines-40 완료) |  |
| crawler | build | 없음 | 04 |  | 01-frontier(B-system-design-60 4차), 02-dedup(B-system-design-60 4차), 03-politeness(B-system-design-60 4차) |
| crdt | build | 없음 | 04 |  | 01-counters(B-distributed-30 5차), 02-sets-and-registers(B-distributed-30 5차), 03-convergence(B-distributed-30 5차) |
| dataflow | build | 있음 | 05 | 01-plan(B-spark-20 완료), 02-shuffle(B-spark-20 완료), 03-join(B-spark-20 완료), 04-adaptive(B-spark-20 완료) |  |
| db-lab | lab | 있음 | 05 | 01-gap-lock(L-database-50 완료), 02-metadata-lock(L-database-51 완료), 03-index-usage(L-database-52 완료), 04-serializable-retry(L-database-53 완료) |  |
| dedup-candidates | build | 없음 | 04 |  | 01-exact(B-pipeline-80 5차), 02-candidates(B-pipeline-80 5차), 03-components(B-pipeline-80 5차) |
| doc-editor | build | 없음 | 05 |  | 01-edit(B-design-patterns-80 5차), 02-undo(B-design-patterns-80 5차), 03-macro(B-design-patterns-80 5차), 04-snapshot(B-design-patterns-80 5차) |
| es-cluster | build | 있음 | 04 | 01-routing(B-search-20 완료), 02-terms-agg(B-search-20 완료), 03-paging(B-search-20 완료) |  |
| es-lab | lab | 있음 | 05 | 01-mapping-explosion(L-search-50 완료), 02-deep-paging(L-search-51 완료), 03-nori-analysis(L-search-52 완료), 04-terms-agg-accuracy(L-search-53 완료) |  |
| geo-index | build | 없음 | 04 |  | 01-geohash(B-system-design-100 4차), 02-radius-search(B-system-design-100 4차), 03-quadtree(B-system-design-100 4차) |
| history-rollover | build | 없음 | 04 |  | 01-materialize(B-pipeline-10 4차), 02-baseline(B-pipeline-10 4차), 03-retention(B-pipeline-10 4차) |
| id-generator | build | 있음 | 04 | 01-snowflake(B-system-design-30 완료), 02-sharded-ids(B-system-design-30 완료), 03-ordering(B-system-design-30 완료) |  |
| incident-reading | reading | 있음 | 08 | 01-cloudflare-regex(R-incidents-10 완료), 02-github-split-writes(R-incidents-20 완료), 03-s3-capacity-removal(R-incidents-30 완료), 04-crowdstrike-channel-file(R-incidents-50 완료), 05-gcp-service-control(R-incidents-60 완료), 06-meta-backbone(R-incidents-40 완료), 07-slack-cold-start(R-incidents-70 완료) |  |
| index-overlay | build | 없음 | 05 |  | 01-current-state(B-pipeline-50 4차), 02-round-merge(B-pipeline-50 4차), 03-replay(B-pipeline-50 4차), 04-reconcile(B-pipeline-70 5차) |
| jvm-lab | lab | 있음 | 05 | 01-container-heap(L-jvm-10 완료), 02-threadlocal-leak(L-jvm-20 완료), 03-thread-dump(L-jvm-30 완료), 04-jit-warmup(L-jvm-40 완료) |  |
| kafka-lab | lab | 있음 | 03 | 01-rebalance(L-messaging-40 완료), 02-durability(L-messaging-41 완료) |  |
| kv-store | build | 없음 | 04 |  | 01-basic(B-kv-store-10 4차), 02-log(B-kv-store-10 4차), 03-transactions(B-kv-store-10 4차) |
| leaderboard | build | 있음 | 04 | 01-sorted-set(B-system-design-130 완료), 02-range-queries(B-system-design-130 완료), 03-sharding(B-system-design-130 완료) |  |
| lease-lock | build | 있음 | 04 | 01-acquisition(B-distributed-60 완료), 02-expiry(B-distributed-60 완료) | 03-fenced-writes(B-distributed-80 5차) |
| ledger | build | 있음 | 04 | 01-double-entry(B-system-design-40 완료), 02-psp-idempotency(B-system-design-40 완료), 03-reconciliation(B-system-design-40 완료) |  |
| lsm | build | 있음 | 04 | 01-memtable(B-database-30 완료), 02-compaction(B-database-30 완료), 03-bloom-and-range(B-database-30 완료) |  |
| matching-engine | build | 있음 | 04 | 01-order-book(B-system-design-50 완료), 02-order-types(B-system-design-50 완료), 03-replay(B-system-design-50 완료) |  |
| mcp-server | build | 있음 | 04 | 01-tools(B-ai-agent-40 완료), 02-resources-prompts(B-ai-agent-40 완료), 03-errors-and-versions(B-ai-agent-40 완료) |  |
| metrics | build | 있음 | 04 | 01-counters(B-observability-20 완료), 02-histograms(B-observability-20 완료), 03-slo-alerts(B-observability-20 완료) |  |
| mini-dfs | build | 있음 | 04 | 01-namespace(B-hdfs-20 완료), 02-placement(B-hdfs-20 완료), 03-recovery(B-hdfs-20 완료) |  |
| mini-log | build | 있음 | 05 | 01-partitions(B-messaging-20 완료), 02-idempotence(B-messaging-20 완료), 03-groups(B-messaging-20 완료), 04-compaction(B-messaging-20 완료) |  |
| mini-olap | build | 있음 | 06 | 01-aggregate(B-olap-20 완료), 02-scale(B-olap-20 완료), 03-rollup(B-olap-20 완료), 04-sketch(B-olap-30 완료), 05-sparse-index(B-olap-30 완료) |  |
| mini-sql | build | 없음 | 04 |  | 01-select(B-mini-sql-10 5차), 02-group(B-mini-sql-10 5차), 03-join(B-mini-sql-10 5차) |
| mvcc | build | 있음 | 04 | 01-snapshot(B-database-20 완료), 02-locking(B-database-20 완료), 03-serializable(B-database-20 완료) |  |
| net-lab | lab | 있음 | 03 | 01-keepalive-race(L-network-10 완료), 02-timeout-layers(L-network-20 완료) |  |
| news-feed | build | 있음 | 04 | 01-fanout-on-write(B-system-design-70 완료), 02-fanout-on-read(B-system-design-70 완료), 03-hybrid(B-system-design-70 완료) |  |
| notifier | build | 없음 | 04 |  | 01-dispatch(B-system-design-150 5차), 02-reliability(B-system-design-150 5차), 03-throttling(B-system-design-150 5차) |
| nrt-index | build | 있음 | 04 | 01-refresh(B-search-30 완료), 02-durability(B-search-30 완료), 03-concurrency(B-search-30 완료) |  |
| object-store | build | 없음 | 04 |  | 01-versioning(B-system-design-160 5차), 02-multipart(B-system-design-160 5차), 03-durability(B-system-design-160 5차) |
| olap-lab | lab | 있음 | 04 | 01-sorting-key(L-olap-40 완료), 02-replacing-merge(L-olap-41 완료), 03-too-many-parts(L-olap-42 완료) |  |
| pipeline-lab | lab | 있음 | 02 | 01-candidate-recall(L-pipeline-90 완료) |  |
| raft | build | 있음 | 04 | 01-election(B-distributed-20 완료), 02-replication(B-distributed-20 완료), 03-commit(B-distributed-20 완료) |  |
| rag | build | 있음 | 05 | 01-chunking(B-ai-agent-30 완료), 02-retrieval(B-ai-agent-30 완료), 03-hybrid(B-ai-agent-30 완료), 04-eval(B-ai-agent-30 완료) |  |
| rate-limiter | build | 있음 | 05 | 01-buckets(B-system-design-20 완료), 02-windows(B-system-design-20 완료), 03-distributed(B-system-design-20 완료), 04-client-throttle(B-system-design-25 완료) |  |
| reading | reading | 있음 | 16 | 01-auction(R-economics-10 완료), 02-agency(R-law-10 완료), 03-grue(R-philosophy-10 완료), 04-enzyme(R-science-10 완료), 05-key-exchange(R-technology-10 완료), 06-rescission(R-society-10 완료), 07-saussure(R-humanities-10 완료), 08-perspective(R-art-10 완료), 09-falsification(R-interdisciplinary-10 완료), 10-carnot(R-science-20 완료), 11-monetary-policy(R-society-20 완료), 12-congestion-control(R-technology-20 완료), 13-source-criticism(R-humanities-20 완료), 14-plate-tectonics(R-science-30 완료), 15-prospect-theory(R-society-30 완료) |  |
| record-range | build | 없음 | 04 |  | 01-lines(B-record-range-10 4차), 02-json-ranges(B-record-range-10 4차), 03-range-read(B-record-range-10 4차) |
| regex | build | 없음 | 04 |  | 01-basic(B-regex-10 5차), 02-classes(B-regex-10 5차), 03-linear(B-regex-10 5차) |
| reservation | build | 있음 | 04 | 01-inventory(B-system-design-120 완료), 02-idempotency(B-system-design-120 완료), 03-concurrency(B-system-design-120 완료) |  |
| result-placement | build | 없음 | 04 |  | 01-ordered(B-search-60 4차), 02-constraints(B-search-60 4차), 03-exact-search(B-search-60 4차) |
| search-engine | build | 있음 | 07 | 01-boolean(B-search-engine-10 완료), 02-ranking(B-search-engine-10 완료), 03-phrase(B-search-engine-10 완료), 04-fuzzy(B-search-engine-20 완료), 05-suggest(B-search-engine-30 완료), 06-snippet(B-search-engine-40 완료) |  |
| sim-testing | build | 없음 | 04 |  | 01-scheduler(B-distributed-50 5차), 02-faults(B-distributed-50 5차), 03-shrink(B-distributed-50 5차) |
| spark-lab | lab | 있음 | 06 | 01-null-semantics(L-spark-30 완료), 02-skew-and-aqe(L-spark-31 완료), 03-overwrite-and-small-files(L-spark-32 완료), 04-retries-and-side-effects(L-spark-33 완료), 05-streaming-watermark(L-spark-34 완료) |  |
| spreadsheet | build | 없음 | 04 |  | 01-formula(B-spreadsheet-10 5차), 02-recalc(B-spreadsheet-10 5차), 03-cycles(B-spreadsheet-10 5차) |
| spring-lab | lab | 있음 | 11 | 01-pool-deadlock(L-spring-10 완료), 02-open-in-view(L-spring-20 완료), 03-proxy-pitfalls(L-spring-30 완료), 04-async-executor(L-spring-40 완료), 05-http-client-pool(L-spring-50 완료), 06-max-lifetime(L-spring-60 완료), 07-virtual-threads(L-spring-70 완료), 08-n-plus-one(L-spring-80 완료), 09-batch-insert(L-spring-90 완료), 10-scheduler(L-spring-100 완료) |  |
| table-format | build | 있음 | 04 | 01-snapshots(B-lakehouse-30 완료), 02-commits(B-lakehouse-30 완료), 03-deletes(B-lakehouse-30 완료) |  |
| tech-reading | reading | 있음 | 17 | 01-agent-loop(R-ai-agent-10 완료), 02-hdfs-write-path(R-hdfs-10 완료), 03-columnar-execution(R-olap-10 완료), 04-near-real-time-search(R-search-10 완료), 05-spark-stages(R-spark-10 완료), 06-coroutines(R-coroutines-10 완료), 07-capacity-estimation(R-system-design-10 완료), 08-mvcc-isolation(R-database-10 완료), 09-raft(R-distributed-10 완료), 10-table-format(R-lakehouse-10 완료), 11-kafka-replication(R-messaging-10 완료), 12-percentiles-and-slo(R-observability-10 완료), 13-online-resharding(R-database-40 완료), 14-creational-patterns(R-design-patterns-10 완료), 15-structural-patterns(R-design-patterns-20 완료), 16-behavioral-patterns(R-design-patterns-30 완료) |  |
| url-shortener | build | 없음 | 04 |  | 01-base62(B-system-design-140 5차), 02-collisions(B-system-design-140 5차), 03-expiry(B-system-design-140 5차) |

## 도메인별 카드

### 읽기

도메인 안내: [reading/README.md](reading/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-art-10](reading/cards/R-art-10-perspective.md) | 원근법과 시점 | reading | reading | 08-perspective | 완료(2026-11-05) | 5 | 없음 |  |
| [R-economics-10](reading/cards/R-economics-10-auction.md) | 경매의 설계 | reading | reading | 01-auction | 완료(2026-10-07) | 1 | 없음 |  |
| [R-humanities-10](reading/cards/R-humanities-10-saussure.md) | 소쉬르의 기호 이론 | reading | reading | 07-saussure | 완료(2026-11-05) | 4 | 없음 |  |
| [R-humanities-20](reading/cards/R-humanities-20-source-criticism.md) | 사료 비판 | reading | reading | 13-source-criticism | 완료(2026-11-05) | 5 | 없음 |  |
| [R-interdisciplinary-10](reading/cards/R-interdisciplinary-10-falsification.md) | 반증주의와 뒤엠-콰인 논제 | reading | reading | 09-falsification | 완료(2026-11-05) | 5 | 없음 |  |
| [R-law-10](reading/cards/R-law-10-agency.md) | 대리와 표현대리 | reading | reading | 02-agency | 완료(2026-10-07) | 1 | 없음 |  |
| [R-philosophy-10](reading/cards/R-philosophy-10-grue.md) | 귀납의 새로운 수수께끼 | reading | reading | 03-grue | 완료(2026-10-07) | 1 | 없음 |  |
| [R-science-10](reading/cards/R-science-10-enzyme.md) | 효소 반응 속도와 저해 | reading | reading | 04-enzyme | 완료(2026-11-05) | 4 | 없음 |  |
| [R-science-20](reading/cards/R-science-20-carnot.md) | 카르노 기관과 열역학 제2법칙 | reading | reading | 10-carnot | 완료(2026-11-05) | 5 | 없음 |  |
| [R-science-30](reading/cards/R-science-30-plate-tectonics.md) | 판 구조론 | reading | reading | 14-plate-tectonics | 완료(2026-11-05) | 5 | 없음 |  |
| [R-society-10](reading/cards/R-society-10-rescission.md) | 계약의 해제와 원상회복 | reading | reading | 06-rescission | 완료(2026-11-05) | 5 | 없음 |  |
| [R-society-20](reading/cards/R-society-20-monetary-policy.md) | 통화정책의 전달 경로와 테일러 준칙 | reading | reading | 11-monetary-policy | 완료(2026-11-05) | 5 | 없음 |  |
| [R-society-30](reading/cards/R-society-30-prospect-theory.md) | 전망 이론과 프레이밍 | reading | reading | 15-prospect-theory | 완료(2026-11-05) | 5 | 없음 |  |
| [R-technology-10](reading/cards/R-technology-10-key-exchange.md) | 공개키 암호와 디피-헬먼 키 교환 | reading | reading | 05-key-exchange | 완료(2026-11-05) | 4 | 없음 |  |
| [R-technology-20](reading/cards/R-technology-20-congestion-control.md) | TCP 혼잡 제어 | reading | reading | 12-congestion-control | 완료(2026-11-05) | 5 | 없음 |  |

### 구현

도메인 안내: [implementation/README.md](implementation/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [B-algorithm-drills-10](implementation/cards/B-algorithm-drills-10-algorithm-drills.md) | 주제별 알고리즘 문제 묶음 | build | algorithm-drills | 01-strings, 02-arrays, 03-sort-search, 04-structures, 05-grids-recursion | 후보 | 5 | 없음 |  |
| [C-kotlin-10](implementation/cards/C-kotlin-10-price-rules.md) | 가격 규칙 계산 | code | code-reading | 01-price-rules | 완료(2026-10-08) | 1 | 없음 |  |
| [F-kotlin-10](implementation/cards/F-kotlin-10-cursor-pagination.md) | 커서 페이지네이션 | fix | bug-fix | 01-cursor-pagination | 완료(2026-10-08) | 1 | 없음 |  |
| [C-kotlin-20](implementation/cards/C-kotlin-20-stock-reservation.md) | 재고 예약과 만료 | code | code-reading | 04-stock-reservation | 완료(2026-11-05) | 1 | 없음 |  |
| [F-kotlin-20](implementation/cards/F-kotlin-20-point-accrual.md) | 포인트 적립 계산 | fix | bug-fix | 06-point-accrual | 완료(2026-11-05) | 1 | 없음 |  |
| [C-kotlin-30](implementation/cards/C-kotlin-30-order-events.md) | 주문 이벤트 처리 | code | code-reading | 05-order-events | 완료(2026-11-05) | 1 | 없음 |  |
| [F-kotlin-30](implementation/cards/F-kotlin-30-daily-limit.md) | 일일 사용 한도 | fix | bug-fix | 07-daily-limit | 완료(2026-11-05) | 1 | 없음 |  |
| [C-kotlin-40](implementation/cards/C-kotlin-40-lru-cache.md) | 상품 상세 로컬 캐시 | code | code-reading | 06-lru-cache | 완료(2026-11-05) | 4 | 없음 |  |
| [F-kotlin-40](implementation/cards/F-kotlin-40-option-equality.md) | 장바구니 상품 옵션 비교 | fix | bug-fix | 08-option-equality | 완료(2026-11-06) | 4 | 없음 |  |
| [C-kotlin-50](implementation/cards/C-kotlin-50-retry-policy.md) | 외부 결제 호출 재시도 정책 | code | code-reading | 07-retry-policy | 완료(2026-11-05) | 4 | 없음 |  |
| [F-kotlin-50](implementation/cards/F-kotlin-50-comparator-contract.md) | 추천 점수 정렬 | fix | bug-fix | 09-comparator-contract | 완료(2026-11-06) | 4 | 없음 |  |
| [C-kotlin-60](implementation/cards/C-kotlin-60-search-query-parser.md) | 검색어 파서 | code | code-reading | 11-search-query-parser | 완료(2026-11-05) | 5 | 없음 |  |
| [F-kotlin-60](implementation/cards/F-kotlin-60-sublist-view.md) | 페이지 단위 일괄 처리 | fix | bug-fix | 18-sublist-view | 완료(2026-11-06) | 5 | 없음 |  |
| [C-kotlin-70](implementation/cards/C-kotlin-70-settlement-batch.md) | 정산 배치 | code | code-reading | 12-settlement-batch | 완료(2026-11-05) | 5 | 없음 |  |
| [F-kotlin-70](implementation/cards/F-kotlin-70-locale-format.md) | 가격 문자열 출력 | fix | bug-fix | 19-locale-format | 완료(2026-11-06) | 5 | 없음 |  |
| [C-kotlin-80](implementation/cards/C-kotlin-80-csv-import.md) | 상품 일괄 등록 | code | code-reading | 13-csv-import | 완료(2026-11-05) | 5 | 없음 |  |
| [F-kotlin-80](implementation/cards/F-kotlin-80-code-pattern.md) | 상품 코드 검증 | fix | bug-fix | 20-code-pattern | 완료(2026-11-06) | 5 | 없음 |  |
| [F-kotlin-90](implementation/cards/F-kotlin-90-retry-duplicate.md) | 주문 상태 변경 기록 | fix | bug-fix | 21-retry-duplicate | 완료(2026-11-06) | 5 | 없음 |  |
| [F-kotlin-100](implementation/cards/F-kotlin-100-cache-key.md) | 회원 등급별 가격 캐시 | fix | bug-fix | 22-cache-key | 완료(2026-11-06) | 5 | 없음 |  |
| [F-kotlin-110](implementation/cards/F-kotlin-110-float-threshold.md) | 무료 배송 기준 금액 | fix | bug-fix | 23-float-threshold | 완료(2026-11-06) | 5 | 없음 |  |
| [F-kotlin-120](implementation/cards/F-kotlin-120-sale-window.md) | 타임세일 시작과 끝 | fix | bug-fix | 24-sale-window | 완료(2026-11-06) | 5 | 없음 |  |
| [F-kotlin-130](implementation/cards/F-kotlin-130-byte-offset.md) | 한글에서 어긋나는 파일 구간 위치 | fix | bug-fix | 10-byte-offset | 완료(2026-11-06) | 4 | 없음 | B-record-range-10 |
| [F-kotlin-140](implementation/cards/F-kotlin-140-short-read.md) | 한 번만 읽어 잘리는 전송 구간 | fix | bug-fix | 11-short-read | 완료(2026-11-06) | 4 | 없음 | B-record-range-10 |
| [B-kv-store-10](implementation/cards/B-kv-store-10-kv-store.md) | 키-값 저장소 | build | kv-store | 01-basic, 02-log, 03-transactions | 후보 | 4 | 없음 |  |
| [B-mini-sql-10](implementation/cards/B-mini-sql-10-mini-sql.md) | 작은 SQL 엔진 | build | mini-sql | 01-select, 02-group, 03-join | 후보 | 5 | 없음 |  |
| [B-record-range-10](implementation/cards/B-record-range-10-record-range.md) | 레코드 경계를 지키는 파일 분할기 만들기 | build | record-range | 01-lines, 02-json-ranges, 03-range-read | 후보 | 4 | 없음 |  |
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
| [B-ai-agent-30](backend/ai-agent/cards/B-ai-agent-30-rag.md) | 검색 증강 파이프라인 만들기 | build | rag | 01-chunking, 02-retrieval, 03-hybrid, 04-eval | 완료(2026-11-05) | 2 | 없음 |  |
| [B-ai-agent-40](backend/ai-agent/cards/B-ai-agent-40-mcp-server.md) | MCP 서버 만들기 | build | mcp-server | 01-tools, 02-resources-prompts, 03-errors-and-versions | 완료(2026-11-06) | 3 | 없음 | B-ai-agent-20 |
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
| [F-coroutines-34](backend/coroutines/cards/F-coroutines-34-instant-finish.md) | 시작하자마자 끝나는 백그라운드 이벤트 | fix | bug-fix | 05-instant-finish | 완료(2026-11-05) | 4 | kotlinx-coroutines | B-coroutines-20 |
| [B-coroutines-35](backend/coroutines/cards/B-coroutines-35-coroutine-patterns-advanced.md) | 코루틴 동시 처리 심화 | build | coroutine-patterns | 05-batching, 06-single-flight | 완료(2026-10-13) | 4 | kotlinx-coroutines | B-coroutines-20 |
| [B-coroutines-40](backend/coroutines/cards/B-coroutines-40-coroutine-runtime.md) | 작은 코루틴 실행기 만들기 | build | coroutine-runtime | 01-event-loop, 02-structured, 03-cancellation, 04-exceptions, 05-channels, 06-flow, 07-sequence, 08-manual-state-machine | 완료(2026-10-14) | 4 | 없음 | B-coroutines-20 |
| [L-coroutines-50](backend/coroutines/cards/L-coroutines-50-dispatcher-starvation.md) | 블로킹 호출과 디스패처 고갈 | lab | coroutine-lab | 01-dispatcher-starvation | 완료(2026-10-13) | 4 | kotlinx-coroutines | B-coroutines-20 |

### 데이터베이스

도메인 안내: [backend/database/README.md](backend/database/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-database-10](backend/database/cards/R-database-10-mvcc-isolation.md) | MVCC 와 격리 수준 | reading | tech-reading | 08-mvcc-isolation | 완료(2026-10-10) | 4 | 없음 |  |
| [B-database-20](backend/database/cards/B-database-20-mvcc.md) | 트랜잭션 격리 엔진 만들기 | build | mvcc | 01-snapshot, 02-locking, 03-serializable | 완료(2026-10-10) | 3 | 없음 | R-database-10 |
| [B-database-30](backend/database/cards/B-database-30-lsm.md) | LSM 저장소 만들기 | build | lsm | 01-memtable, 02-compaction, 03-bloom-and-range | 완료(2026-11-06) | 4 | 없음 |  |
| [R-database-40](backend/database/cards/R-database-40-online-resharding.md) | 서비스를 멈추지 않는 데이터 이전과 샤딩 | reading | tech-reading | 13-online-resharding | 완료(2026-11-05) | 4 | 없음 | R-database-10 |
| [L-database-50](backend/database/cards/L-database-50-gap-lock.md) | 갭 잠금과 삽입 교착 | lab | db-lab | 01-gap-lock | 완료(2026-11-06) | 4 | docker | R-database-10 |
| [L-database-51](backend/database/cards/L-database-51-metadata-lock.md) | 긴 트랜잭션과 스키마 변경 대기 | lab | db-lab | 02-metadata-lock | 완료(2026-11-06) | 4 | docker | R-database-10 |
| [L-database-52](backend/database/cards/L-database-52-index-usage.md) | 인덱스를 쓰지 못하는 질의 | lab | db-lab | 03-index-usage | 완료(2026-11-06) | 5 | docker |  |
| [L-database-53](backend/database/cards/L-database-53-serializable-retry.md) | 직렬화 실패와 재시도 | lab | db-lab | 04-serializable-retry | 완료(2026-11-06) | 5 | docker | B-database-20 |

### 설계 패턴

도메인 안내: [backend/design-patterns/README.md](backend/design-patterns/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-design-patterns-10](backend/design-patterns/cards/R-design-patterns-10-creational-patterns.md) | 객체를 만드는 책임과 생성 패턴 | reading | tech-reading | 14-creational-patterns | 완료(2026-11-05) | 4 | 없음 |  |
| [R-design-patterns-20](backend/design-patterns/cards/R-design-patterns-20-structural-patterns.md) | 감싸기와 합성, 구조 패턴 | reading | tech-reading | 15-structural-patterns | 완료(2026-11-05) | 4 | 없음 | R-design-patterns-10 |
| [R-design-patterns-30](backend/design-patterns/cards/R-design-patterns-30-behavioral-patterns.md) | 바뀌는 부분 떼어 내기, 행위 패턴 | reading | tech-reading | 16-behavioral-patterns | 완료(2026-11-05) | 4 | 없음 | R-design-patterns-20 |
| [C-design-patterns-40](backend/design-patterns/cards/C-design-patterns-40-decorated-client.md) | 겹겹이 감싼 상품 조회 클라이언트 | code | code-reading | 08-decorated-client | 완료(2026-11-05) | 4 | 없음 | R-design-patterns-20 |
| [C-design-patterns-50](backend/design-patterns/cards/C-design-patterns-50-state-observers.md) | 주문 상태 객체와 구독자 알림 | code | code-reading | 09-state-observers | 완료(2026-11-05) | 4 | 없음 | R-design-patterns-30 |
| [F-design-patterns-60](backend/design-patterns/cards/F-design-patterns-60-skipped-subscriber.md) | 재입고 알림을 놓치는 구독자 | fix | bug-fix | 03-skipped-subscriber | 완료(2026-11-05) | 4 | 없음 | R-design-patterns-30 |
| [F-design-patterns-70](backend/design-patterns/cards/F-design-patterns-70-stale-redo.md) | 다시 실행하면 끼어드는 옛 편집 | fix | bug-fix | 04-stale-redo | 완료(2026-11-05) | 4 | 없음 | R-design-patterns-30 |
| [B-design-patterns-80](backend/design-patterns/cards/B-design-patterns-80-doc-editor.md) | 되돌리기를 지원하는 문서 편집기 만들기 | build | doc-editor | 01-edit, 02-undo, 03-macro, 04-snapshot | 후보 | 5 | 없음 | R-design-patterns-30 |
| [C-design-patterns-90](backend/design-patterns/cards/C-design-patterns-90-notification-queue.md) | 알림 발송 작업 큐 | code | code-reading | 14-notification-queue | 완료(2026-11-05) | 5 | 없음 |  |
| [F-design-patterns-100](backend/design-patterns/cards/F-design-patterns-100-double-start.md) | 두 번 시작되는 정산 작업 | fix | bug-fix | 25-double-start | 완료(2026-11-06) | 5 | 없음 |  |
| [F-design-patterns-110](backend/design-patterns/cards/F-design-patterns-110-unchecked-wakeup.md) | 빈 대기열에서 꺼내는 작업자 | fix | bug-fix | 26-unchecked-wakeup | 완료(2026-11-06) | 5 | 없음 |  |

### 분산 시스템

도메인 안내: [backend/distributed/README.md](backend/distributed/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-distributed-10](backend/distributed/cards/R-distributed-10-raft.md) | Raft 의 선출과 커밋 | reading | tech-reading | 09-raft | 완료(2026-10-10) | 4 | 없음 |  |
| [B-distributed-20](backend/distributed/cards/B-distributed-20-raft-build.md) | Raft 시뮬레이터 만들기 | build | raft | 01-election, 02-replication, 03-commit | 완료(2026-10-11) | 4 | 없음 | R-distributed-10 |
| [B-distributed-30](backend/distributed/cards/B-distributed-30-crdt.md) | 복제 자료형 만들기 | build | crdt | 01-counters, 02-sets-and-registers, 03-convergence | 후보 | 5 | 없음 |  |
| [B-distributed-40](backend/distributed/cards/B-distributed-40-consistent-hashing.md) | 일관 해싱 만들기 | build | consistent-hashing | 01-ring, 02-rebalance, 03-replication | 완료(2026-11-06) | 4 | 없음 |  |
| [B-distributed-50](backend/distributed/cards/B-distributed-50-simulation-testing.md) | 결정적 시뮬레이션 시험 만들기 | build | sim-testing | 01-scheduler, 02-faults, 03-shrink | 후보 | 5 | 없음 | B-distributed-20 |
| [B-distributed-60](backend/distributed/cards/B-distributed-60-lease-lock.md) | 가상 시간의 lease 잠금 만들기 | build | lease-lock | 01-acquisition, 02-expiry | 완료(2026-11-06) | 4 | 없음 |  |
| [F-distributed-70](backend/distributed/cards/F-distributed-70-late-lock-release.md) | 늦은 해제가 새 잠금을 지우는 버그 | fix | bug-fix | 12-late-lock-release | 완료(2026-11-06) | 4 | 없음 | B-distributed-60 |
| [B-distributed-80](backend/distributed/cards/B-distributed-80-fenced-writes.md) | 저장소가 검사하는 펜싱 토큰 만들기 | build | lease-lock | 03-fenced-writes | 후보 | 5 | 없음 | B-distributed-60 |

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
| [R-incidents-10](backend/incidents/cards/R-incidents-10-cloudflare-regex.md) | 정규식 하나가 멈춘 전 세계 프록시 | reading | incident-reading | 01-cloudflare-regex | 완료(2026-11-05) | 3 | 없음 |  |
| [R-incidents-20](backend/incidents/cards/R-incidents-20-github-split-writes.md) | 43초 분할이 만든 하루 동안의 장애 | reading | incident-reading | 02-github-split-writes | 완료(2026-11-05) | 3 | 없음 |  |
| [R-incidents-30](backend/incidents/cards/R-incidents-30-s3-capacity-removal.md) | 잘못 입력한 용량 제거 명령 | reading | incident-reading | 03-s3-capacity-removal | 완료(2026-11-05) | 3 | 없음 |  |
| [R-incidents-40](backend/incidents/cards/R-incidents-40-meta-backbone.md) | 스스로 사라진 DNS | reading | incident-reading | 06-meta-backbone | 완료(2026-11-05) | 4 | 없음 |  |
| [R-incidents-50](backend/incidents/cards/R-incidents-50-crowdstrike-channel-file.md) | 필드 하나가 모자랐던 콘텐츠 갱신 | reading | incident-reading | 04-crowdstrike-channel-file | 완료(2026-11-05) | 3 | 없음 |  |
| [R-incidents-60](backend/incidents/cards/R-incidents-60-gcp-service-control.md) | 기능 플래그 없는 코드 경로와 빈 필드 | reading | incident-reading | 05-gcp-service-control | 완료(2026-11-05) | 3 | 없음 |  |
| [R-incidents-70](backend/incidents/cards/R-incidents-70-slack-cold-start.md) | 연휴 뒤 첫 월요일의 급증 | reading | incident-reading | 07-slack-cold-start | 완료(2026-11-05) | 4 | 없음 |  |

### JVM

도메인 안내: [backend/jvm/README.md](backend/jvm/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [L-jvm-10](backend/jvm/cards/L-jvm-10-container-heap.md) | 메모리 한도와 기본 힙 크기 | lab | jvm-lab | 01-container-heap | 완료(2026-11-06) | 4 | 없음 |  |
| [L-jvm-20](backend/jvm/cards/L-jvm-20-threadlocal-leak.md) | 스레드 풀과 ThreadLocal | lab | jvm-lab | 02-threadlocal-leak | 완료(2026-11-06) | 4 | 없음 |  |
| [L-jvm-30](backend/jvm/cards/L-jvm-30-thread-dump.md) | 스레드 덤프로 교착 찾기 | lab | jvm-lab | 03-thread-dump | 완료(2026-11-06) | 4 | 없음 |  |
| [L-jvm-40](backend/jvm/cards/L-jvm-40-jit-warmup.md) | JIT 예열과 측정 함정 | lab | jvm-lab | 04-jit-warmup | 완료(2026-11-06) | 5 | 없음 |  |

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
| [L-load-10](backend/load/cards/L-load-10-stock-race.md) | 재고 차감과 동시 주문 | lab | backend-lab | 01-stock-race | 완료(2026-11-06) | 3 | 없음 |  |
| [L-load-20](backend/load/cards/L-load-20-idempotency.md) | 중복 요청과 멱등 키 | lab | backend-lab | 02-idempotency | 완료(2026-11-06) | 3 | 없음 |  |
| [L-load-30](backend/load/cards/L-load-30-cache-stampede.md) | 캐시 스탬피드 | lab | backend-lab | 03-cache-stampede | 완료(2026-11-06) | 3 | redis |  |
| [L-load-40](backend/load/cards/L-load-40-penetration-avalanche.md) | 캐시 관통과 동시 만료 | lab | backend-lab | 04-penetration-avalanche | 완료(2026-11-06) | 3 | redis |  |
| [L-load-50](backend/load/cards/L-load-50-stale-set.md) | 캐시와 원천의 불일치 | lab | backend-lab | 05-stale-set | 완료(2026-11-06) | 3 | redis |  |
| [L-load-60](backend/load/cards/L-load-60-cache-outage.md) | 캐시 장애와 원천 보호 | lab | backend-lab | 06-cache-outage | 완료(2026-11-06) | 3 | redis |  |
| [L-load-70](backend/load/cards/L-load-70-retry-storm.md) | 타임아웃, 재시도, 백오프 | lab | backend-lab | 07-retry-storm | 완료(2026-11-06) | 5 | 없음 |  |
| [L-load-80](backend/load/cards/L-load-80-load-shedding.md) | 과부하와 load shedding | lab | backend-lab | 08-load-shedding | 완료(2026-11-06) | 5 | 없음 |  |
| [L-load-90](backend/load/cards/L-load-90-rate-limit.md) | 속도 제한 | lab | backend-lab | 09-rate-limit | 완료(2026-11-06) | 5 | redis |  |
| [L-load-100](backend/load/cards/L-load-100-idempotent-consumer.md) | 멱등 소비자와 순서 | lab | backend-lab | idempotent-consumer | 보류(메시지 브로커 실행 방식 결정 필요) | 5 | docker |  |
| [L-load-110](backend/load/cards/L-load-110-outbox.md) | 트랜잭셔널 아웃박스 | lab | backend-lab | outbox | 보류(L-load-100 과 같은 이유) | 5 | docker |  |
| [L-load-120](backend/load/cards/L-load-120-adaptive-concurrency.md) | 적응형 동시성 제한 | lab | backend-lab | 10-adaptive-concurrency | 완료(2026-11-06) | 5 | 없음 | L-load-80 |

### 메시징: Kafka

도메인 안내: [backend/messaging/README.md](backend/messaging/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-messaging-10](backend/messaging/cards/R-messaging-10-kafka-replication.md) | 복제와 전달 보장 | reading | tech-reading | 11-kafka-replication | 완료(2026-10-10) | 4 | 없음 |  |
| [B-messaging-20](backend/messaging/cards/B-messaging-20-mini-log.md) | 파티션 로그와 컨슈머 그룹 만들기 | build | mini-log | 01-partitions, 02-idempotence, 03-groups, 04-compaction | 완료(2026-10-11) | 3 | 없음 | R-messaging-10 |
| [C-messaging-30](backend/messaging/cards/C-messaging-30-consumer-commit.md) | 오프셋 커밋 시점과 중복 처리 | code | code-reading | 10-consumer-commit | 완료(2026-11-05) | 4 | 없음 | R-messaging-10 |
| [L-messaging-40](backend/messaging/cards/L-messaging-40-rebalance.md) | 느린 처리와 재조정 폭풍 | lab | kafka-lab | 01-rebalance | 완료(2026-11-06) | 5 | docker | B-messaging-20 |
| [L-messaging-41](backend/messaging/cards/L-messaging-41-durability.md) | acks 와 최소 동기 복제본 | lab | kafka-lab | 02-durability | 완료(2026-11-06) | 5 | docker | R-messaging-10 |

### 네트워크

도메인 안내: [backend/network/README.md](backend/network/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [L-network-10](backend/network/cards/L-network-10-keepalive-race.md) | keep-alive 수명의 경합 | lab | net-lab | 01-keepalive-race | 완료(2026-11-06) | 4 | 없음 |  |
| [L-network-20](backend/network/cards/L-network-20-timeout-layers.md) | 타임아웃의 층 | lab | net-lab | 02-timeout-layers | 완료(2026-11-06) | 5 | 없음 |  |

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
| [B-olap-30](backend/olap/cards/B-olap-30-sketch-and-sparse-index.md) | 근사 집계와 희소 인덱스 | build | mini-olap | 04-sketch, 05-sparse-index | 완료(2026-11-06) | 3 | 없음 | B-olap-20 |
| [L-olap-40](backend/olap/cards/L-olap-40-sorting-key.md) | 정렬 키와 읽는 그래뉼 수 | lab | olap-lab | 01-sorting-key | 완료(2026-11-06) | 4 | docker | R-olap-10 |
| [L-olap-41](backend/olap/cards/L-olap-41-replacing-merge.md) | 병합 때만 지워지는 중복 | lab | olap-lab | 02-replacing-merge | 완료(2026-11-06) | 4 | docker | L-olap-40 |
| [L-olap-42](backend/olap/cards/L-olap-42-too-many-parts.md) | 작은 삽입과 파트 폭증 | lab | olap-lab | 03-too-many-parts | 완료(2026-11-06) | 5 | docker | L-olap-40 |

### 데이터 파이프라인

도메인 안내: [backend/pipeline/README.md](backend/pipeline/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [B-pipeline-10](backend/pipeline/cards/B-pipeline-10-history-rollover.md) | 현재 상태를 보존하는 이력 압축기 만들기 | build | history-rollover | 01-materialize, 02-baseline, 03-retention | 후보 | 4 | 없음 | B-lakehouse-30 |
| [F-pipeline-20](backend/pipeline/cards/F-pipeline-20-delete-scope.md) | 다시 적재하지 않은 키까지 지우는 정리 작업 | fix | bug-fix | 13-rollover-delete-scope | 완료(2026-11-06) | 4 | 없음 | B-pipeline-10 |
| [B-pipeline-30](backend/pipeline/cards/B-pipeline-30-batch-workflow.md) | 실행 증거로 연결하는 배치 단계 만들기 | build | batch-workflow | 01-markers, 02-resume, 03-seal | 후보 | 4 | 없음 |  |
| [F-pipeline-40](backend/pipeline/cards/F-pipeline-40-missing-chunk.md) | 빠진 조각을 못 찾는 완료 판정 | fix | bug-fix | 14-missing-chunk | 완료(2026-11-06) | 4 | 없음 | B-pipeline-30 |
| [B-pipeline-50](backend/pipeline/cards/B-pipeline-50-index-overlay.md) | 운영과 실험의 변경을 합치는 병합기 만들기 | build | index-overlay | 01-current-state, 02-round-merge, 03-replay | 후보 | 4 | 없음 |  |
| [F-pipeline-60](backend/pipeline/cards/F-pipeline-60-replay-regression.md) | 과거 회차 재실행이 최신 결과를 되돌리는 버그 | fix | bug-fix | 15-replay-regression | 완료(2026-11-06) | 4 | 없음 | B-pipeline-50 |
| [B-pipeline-70](backend/pipeline/cards/B-pipeline-70-experiment-recovery.md) | 실험 종료 뒤 운영 상태로 복구하는 도구 만들기 | build | index-overlay | 04-reconcile | 후보 | 5 | 없음 | B-pipeline-50 |
| [B-pipeline-80](backend/pipeline/cards/B-pipeline-80-dedup-candidates.md) | 중복 후보 생성과 대표 선택기 만들기 | build | dedup-candidates | 01-exact, 02-candidates, 03-components | 후보 | 5 | 없음 |  |
| [L-pipeline-90](backend/pipeline/cards/L-pipeline-90-candidate-recall.md) | 비교 비용과 놓친 중복쌍 측정 | lab | pipeline-lab | 01-candidate-recall | 완료(2026-11-05) | 5 | 없음 | B-pipeline-80 |

### 검색: Elasticsearch

도메인 안내: [backend/search/README.md](backend/search/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-search-10](backend/search/cards/R-search-10-near-real-time-search.md) | 거의 실시간 검색과 내구성 | reading | tech-reading | 04-near-real-time-search | 완료(2026-10-09) | 2 | 없음 |  |
| [B-search-20](backend/search/cards/B-search-20-es-cluster.md) | 분산 검색 조정 노드 만들기 | build | es-cluster | 01-routing, 02-terms-agg, 03-paging | 완료(2026-10-10) | 2 | 없음 | R-search-10 |
| [B-search-30](backend/search/cards/B-search-30-nrt-index.md) | 세그먼트와 translog 시뮬레이터 만들기 | build | nrt-index | 01-refresh, 02-durability, 03-concurrency | 완료(2026-11-06) | 3 | 없음 | R-search-10 |
| [F-search-40](backend/search/cards/F-search-40-hangul-normalization.md) | 한글 정규화가 어긋난 상품 검색 | fix | bug-fix | 16-hangul-normalization | 완료(2026-11-06) | 4 | 없음 |  |
| [L-search-50](backend/search/cards/L-search-50-mapping-explosion.md) | 동적 매핑과 필드 폭증 | lab | es-lab | 01-mapping-explosion | 완료(2026-11-06) | 4 | docker | R-search-10 |
| [L-search-51](backend/search/cards/L-search-51-deep-paging.md) | 깊은 페이지 넘기기 | lab | es-lab | 02-deep-paging | 완료(2026-11-06) | 4 | docker | B-search-20 |
| [L-search-52](backend/search/cards/L-search-52-nori-analysis.md) | nori 분석기와 상품명 | lab | es-lab | 03-nori-analysis | 완료(2026-11-06) | 4 | docker | R-search-10 |
| [L-search-53](backend/search/cards/L-search-53-terms-agg-accuracy.md) | terms 집계의 오차 | lab | es-lab | 04-terms-agg-accuracy | 완료(2026-11-06) | 5 | docker | B-search-20 |
| [B-search-60](backend/search/cards/B-search-60-result-placement.md) | 제약을 만족하는 검색 결과 배치기 만들기 | build | result-placement | 01-ordered, 02-constraints, 03-exact-search | 후보 | 4 | 없음 |  |
| [F-search-70](backend/search/cards/F-search-70-fallback-constraints.md) | 대체 배치 경로의 노출 상한 누락 | fix | bug-fix | 17-fallback-constraints | 완료(2026-11-06) | 4 | 없음 | B-search-60 |
| [C-search-80](backend/search/cards/C-search-80-stable-diversity.md) | 재조회해도 유지되는 추천 다양성 | code | code-reading | 15-stable-diversity | 완료(2026-11-05) | 5 | 없음 | B-search-60 |

### Spark

도메인 안내: [backend/spark/README.md](backend/spark/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-spark-10](backend/spark/cards/R-spark-10-spark-stages.md) | Spark 의 실행 계획과 셔플 | reading | tech-reading | 05-spark-stages | 완료(2026-10-09) | 2 | 없음 |  |
| [B-spark-20](backend/spark/cards/B-spark-20-dataflow.md) | 데이터 흐름 엔진 만들기 | build | dataflow | 01-plan, 02-shuffle, 03-join, 04-adaptive | 완료(2026-10-10) | 2 | 없음 | R-spark-10 |
| [L-spark-30](backend/spark/cards/L-spark-30-null-semantics.md) | NULL, 중복, 열 순서의 의미론 | lab | spark-lab | 01-null-semantics | 완료(2026-11-06) | 4 | docker | R-spark-10 |
| [L-spark-31](backend/spark/cards/L-spark-31-skew-and-aqe.md) | 데이터 쏠림과 적응 실행 | lab | spark-lab | 02-skew-and-aqe | 완료(2026-11-06) | 4 | docker | B-spark-20 |
| [L-spark-32](backend/spark/cards/L-spark-32-overwrite-and-small-files.md) | 파티션 덮어쓰기와 작은 파일 | lab | spark-lab | 03-overwrite-and-small-files | 완료(2026-11-06) | 4 | docker | R-spark-10 |
| [L-spark-33](backend/spark/cards/L-spark-33-retries-and-side-effects.md) | 재실행과 부수효과 | lab | spark-lab | 04-retries-and-side-effects | 완료(2026-11-06) | 5 | docker | R-spark-10 |
| [L-spark-34](backend/spark/cards/L-spark-34-streaming-watermark.md) | 워터마크와 늦은 이벤트 | lab | spark-lab | 05-streaming-watermark | 완료(2026-11-06) | 5 | docker | R-spark-10 |

### 프레임워크 심화와 튜닝

도메인 안내: [backend/spring/README.md](backend/spring/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [L-spring-10](backend/spring/cards/L-spring-10-pool-deadlock.md) | REQUIRES_NEW 와 풀 교착 | lab | spring-lab | 01-pool-deadlock | 완료(2026-11-06) | 4 | docker |  |
| [L-spring-20](backend/spring/cards/L-spring-20-open-in-view.md) | OSIV 와 느린 외부 호출 | lab | spring-lab | 02-open-in-view | 완료(2026-11-06) | 4 | docker |  |
| [L-spring-30](backend/spring/cards/L-spring-30-proxy-pitfalls.md) | 트랜잭션 프록시의 함정 | lab | spring-lab | 03-proxy-pitfalls | 완료(2026-11-06) | 4 | docker |  |
| [L-spring-40](backend/spring/cards/L-spring-40-async-executor.md) | @Async 실행기와 큐 | lab | spring-lab | 04-async-executor | 완료(2026-11-06) | 4 | docker |  |
| [L-spring-50](backend/spring/cards/L-spring-50-http-client-pool.md) | HTTP 클라이언트 커넥션 풀 | lab | spring-lab | 05-http-client-pool | 완료(2026-11-06) | 4 | docker |  |
| [L-spring-60](backend/spring/cards/L-spring-60-max-lifetime.md) | maxLifetime 과 끊긴 연결 | lab | spring-lab | 06-max-lifetime | 완료(2026-11-06) | 5 | docker |  |
| [L-spring-70](backend/spring/cards/L-spring-70-virtual-threads.md) | 가상 스레드와 풀 | lab | spring-lab | 07-virtual-threads | 완료(2026-11-06) | 5 | docker |  |
| [L-spring-80](backend/spring/cards/L-spring-80-n-plus-one.md) | N+1 과 페이징 | lab | spring-lab | 08-n-plus-one | 완료(2026-11-06) | 5 | docker |  |
| [L-spring-90](backend/spring/cards/L-spring-90-batch-insert.md) | IDENTITY 와 배치 삽입 | lab | spring-lab | 09-batch-insert | 완료(2026-11-06) | 5 | docker |  |
| [L-spring-100](backend/spring/cards/L-spring-100-scheduler.md) | @Scheduled 단일 스레드 | lab | spring-lab | 10-scheduler | 완료(2026-11-06) | 5 | docker |  |

### 시스템 설계

도메인 안내: [backend/system-design/README.md](backend/system-design/README.md)

| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |
|---|---|---|---|---|---|---|---|---|
| [R-system-design-10](backend/system-design/cards/R-system-design-10-capacity-estimation.md) | 규모 추정 | reading | tech-reading | 07-capacity-estimation | 완료(2026-10-10) | 3 | 없음 |  |
| [B-system-design-20](backend/system-design/cards/B-system-design-20-rate-limiter.md) | 처리율 제한기 만들기 | build | rate-limiter | 01-buckets, 02-windows, 03-distributed | 완료(2026-10-10) | 3 | 없음 |  |
| [B-system-design-25](backend/system-design/cards/B-system-design-25-client-throttling.md) | 호출자 쪽 적응형 쓰로틀링 | build | rate-limiter | 04-client-throttle | 완료(2026-11-06) | 4 | 없음 | B-system-design-20 |
| [B-system-design-30](backend/system-design/cards/B-system-design-30-id-generator.md) | 분산 고유 ID 생성기 만들기 | build | id-generator | 01-snowflake, 02-sharded-ids, 03-ordering | 완료(2026-11-06) | 3 | 없음 |  |
| [B-system-design-40](backend/system-design/cards/B-system-design-40-ledger.md) | 결제 원장 만들기 | build | ledger | 01-double-entry, 02-psp-idempotency, 03-reconciliation | 완료(2026-11-06) | 3 | 없음 |  |
| [B-system-design-50](backend/system-design/cards/B-system-design-50-matching-engine.md) | 주문 매칭 엔진 만들기 | build | matching-engine | 01-order-book, 02-order-types, 03-replay | 완료(2026-11-06) | 3 | 없음 |  |
| [B-system-design-60](backend/system-design/cards/B-system-design-60-crawler.md) | 웹 크롤러 만들기 | build | crawler | 01-frontier, 02-dedup, 03-politeness | 후보 | 4 | 없음 |  |
| [B-system-design-70](backend/system-design/cards/B-system-design-70-news-feed.md) | 뉴스 피드 팬아웃 만들기 | build | news-feed | 01-fanout-on-write, 02-fanout-on-read, 03-hybrid | 완료(2026-11-06) | 4 | 없음 |  |
| [B-system-design-80](backend/system-design/cards/B-system-design-80-chat.md) | 채팅 순서와 동기화 만들기 | build | chat | 01-sequencing, 02-sync, 03-presence | 후보 | 4 | 없음 |  |
| [B-system-design-90](backend/system-design/cards/B-system-design-90-autocomplete.md) | 검색어 자동완성 만들기 | build | autocomplete | 01-trie, 02-cached-top-k, 03-filters-and-shards | 완료(2026-11-06) | 4 | 없음 |  |
| [B-system-design-100](backend/system-design/cards/B-system-design-100-geo-index.md) | 근처 장소 검색 만들기 | build | geo-index | 01-geohash, 02-radius-search, 03-quadtree | 후보 | 4 | 없음 |  |
| [B-system-design-110](backend/system-design/cards/B-system-design-110-ad-click-aggregator.md) | 광고 클릭 집계기 만들기 | build | ad-click-aggregator | 01-windows, 02-late-events, 03-exactly-once | 후보 | 4 | 없음 |  |
| [B-system-design-120](backend/system-design/cards/B-system-design-120-reservation.md) | 호텔 예약 재고 만들기 | build | reservation | 01-inventory, 02-idempotency, 03-concurrency | 완료(2026-11-06) | 4 | 없음 |  |
| [B-system-design-130](backend/system-design/cards/B-system-design-130-leaderboard.md) | 실시간 순위표 만들기 | build | leaderboard | 01-sorted-set, 02-range-queries, 03-sharding | 완료(2026-11-06) | 4 | 없음 |  |
| [B-system-design-140](backend/system-design/cards/B-system-design-140-url-shortener.md) | URL 단축기 만들기 | build | url-shortener | 01-base62, 02-collisions, 03-expiry | 후보 | 5 | 없음 | B-system-design-30 |
| [B-system-design-150](backend/system-design/cards/B-system-design-150-notifier.md) | 알림 발송기 만들기 | build | notifier | 01-dispatch, 02-reliability, 03-throttling | 후보 | 5 | 없음 |  |
| [B-system-design-160](backend/system-design/cards/B-system-design-160-object-store.md) | 객체 저장소 만들기 | build | object-store | 01-versioning, 02-multipart, 03-durability | 후보 | 5 | 없음 |  |

