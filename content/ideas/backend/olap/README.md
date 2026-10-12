# OLAP

대량의 데이터를 빠르게 집계하는 분석 시스템의 저장 방식, 실행 방식, 모델링, 근사 집계를 익힌다. 열 저장과 정렬 키가 읽는 양을 어떻게 줄이는지, 사전 집계와 근사 집계가 무엇을 잃고 무엇을 얻는지 설명하고 질의가 느리거나 틀린 원인을 찾는 힘을 기른다.

## 1. 개념 지도

### 1.1 저장과 실행

- 트랜잭션 처리(OLTP)는 행 몇 개를 자주 읽고 쓰고 분석 처리(OLAP)는 넓은 범위를 읽어 집계한다. 열 저장은 질의가 쓰는 열만 읽고 같은 종류의 값이 모여 압축이 잘 된다.
- 벡터화 실행은 값 하나가 아니라 열 값 묶음 단위로 연산자를 실행해 함수 호출과 분기 비용을 줄인다. 늦은 실체화는 필요한 때까지 행을 다시 맞추지 않는다.
- ClickHouse MergeTree: 삽입마다 정렬된 데이터 파트가 생기고 백그라운드 병합이 파트를 합친다. 정렬 키가 데이터 순서를 정한다. 기본 키는 정렬 키의 앞부분이고 유일하지 않아도 된다.
- 기본 키는 희소 인덱스다. 그래뉼(기본 8,192행)마다 첫 행의 키 값만 표시하고 질의 조건에 맞지 않는 그래뉼을 건너뛴다. 그래뉼은 읽기의 최소 단위다.
- 파티션 키는 대부분 필요 없고 필요해도 월보다 잘게 나누지 않는다. 잘게 나눠도 질의는 빨라지지 않는다.
- 데이터 건너뛰기 인덱스: minmax, set, bloom_filter 같은 종류가 그래뉼 묶음 단위로 건너뛰기를 돕는다.
- ReplacingMergeTree 는 병합할 때만 같은 정렬 키의 중복을 지운다. 병합 시점은 알 수 없어 중복이 없다고 보장하지 않는다. 질의 때 FINAL 이나 집계로 중복을 걸러야 한다. 남는 행은 버전 열이 있으면 버전이 가장 큰 행, 없으면 마지막에 넣은 행이다.

### 1.2 모델링과 사전 집계

- 스타 스키마는 사실 테이블과 차원 테이블로 나눈다. 눈송이 스키마는 차원을 더 나누고 넓은 비정규화 테이블은 조인을 없앤다. 천천히 변하는 차원은 값을 덮어쓰거나(유형 1) 이력 행을 더한다(유형 2).
- GROUPING SETS, ROLLUP, CUBE 는 여러 묶음의 집계를 한 질의로 낸다.
- Druid 롤업은 차원 값이 같고 질의 단위로 자른 시각이 같은 행을 적재할 때 한 행으로 합친다. 개별 이벤트를 다시 조회할 수 없다. 완전 롤업과 최선 노력 롤업이 있다. 카디널리티가 높은 차원은 롤업 비율을 떨어뜨리므로 스케치로 대신한다.

### 1.3 근사 집계

- 서로 다른 값의 수는 더할 수 없다. 일별 방문자 수의 합은 월 방문자 수가 아니다. 집계를 나눠 계산하려면 합칠 수 있는 스케치를 쓴다.
- HyperLogLog 의 상대 표준 오차는 약 1.04/√m 이다(m 은 레지스터 수).
- Elasticsearch 의 cardinality 집계는 HyperLogLog++ 를 쓴다. precision_threshold 기본값은 3,000, 최댓값은 40,000 이고 그 아래 개수는 정확에 가깝다(보장은 아니다). 메모리는 약 precision_threshold × 8 바이트다.
- 평균이나 백분위수도 묶음별 결과를 단순 평균해서 합칠 수 없다.

### 1.4 분산 실행

- MPP 엔진은 큰 조인을 브로드캐스트(작은 쪽을 모든 노드에 보냄)나 분할(두 쪽을 키로 다시 나눔)로 실행한다. 통계가 없으면 조인 순서와 전략을 잘못 고른다. 런타임 필터는 빌드 쪽 키로 만든 필터를 탐색 쪽 스캔에 미리 적용한다. 카드를 만들 때 Impala, Trino 같은 엔진 문서로 확인한다.
- 날짜 경계는 시간대가 정한다. 일 집계를 UTC 로 하면 한국 시각 기준 하루와 9시간 어긋난다.

## 2. 학습 경로

1. R-olap-10 기술 지문으로 열 저장, 희소 인덱스, 사전 집계, 근사 집계를 익힌다.
2. B-olap-20 `mini-olap` 1~3단계에서 열 지향 집계 엔진을 만든다.
3. B-olap-30 `mini-olap` 4~5단계에서 근사 집계와 희소 인덱스를 더한다.
4. L-olap-40~L-olap-42 실험으로 ClickHouse 의 정렬 키, 중복 제거, 파트 수 문제를 관찰한다. Docker 결정 뒤에 한다.

## 3. 환경

- reading 과 build 카드는 추가 도구가 필요 없다.
- lab 카드는 Docker 의 ClickHouse 이미지가 필요하다.

## 4. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 일 집계와 시간대 | code | KST 와 UTC 의 날짜 경계가 다른 집계 코드의 결과 예측 |
| 퍼널과 리텐션 계산 | build 단계 | 사용자별 이벤트 순서와 창 |
| 스타 트리와 물질화 뷰 | reading | 사전 집계의 갱신 비용과 질의 이득 |
| Impala 통계와 런타임 필터 | lab | 통계 유무에 따른 조인 순서 |

## 5. 출처

- ClickHouse MergeTree: https://clickhouse.com/docs/engines/table-engines/mergetree-family/mergetree
- ClickHouse ReplacingMergeTree: https://clickhouse.com/docs/engines/table-engines/mergetree-family/replacingmergetree
- Druid Rollup: https://druid.apache.org/docs/latest/ingestion/rollup/
- Elasticsearch cardinality 집계: https://www.elastic.co/docs/reference/aggregations/search-aggregations-metrics-cardinality-aggregation
- Flajolet 외, "HyperLogLog: the analysis of a near-optimal cardinality estimation algorithm", AofA 2007. Heule 외, "HyperLogLog in Practice", EDBT 2013.
- Boncz 외, "MonetDB/X100: Hyper-Pipelining Query Execution", CIDR 2005. Stonebraker 외, "C-Store: A Column-oriented DBMS", VLDB 2005.
- Kimball, Ross, "The Data Warehouse Toolkit", 3판, 2013.
