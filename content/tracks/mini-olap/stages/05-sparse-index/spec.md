# 5단계: 정렬 키 희소 인덱스

## 1. 개요

[1.1] 5단계는 두 열의 정렬 키와 그래뉼 크기를 지정하는 테이블을 더한다. SCAN 명령은 집계 결과와 함께 읽은 그래뉼 수와 행 수를 출력한다. 정렬 키의 앞 열로 거를 때, 뒤 열만으로 거를 때, 범위로 거를 때 읽는 양이 어떻게 달라지는지 드러내는 것이 목표다. 목표 구현 시간은 2~3시간이다.

[1.2] 1~4단계의 명령과 출력은 그대로 유지한다. 기존 CREATE TABLE에는 선택적인 정렬 정의만 더한다. 기존 SELECT, EXPLAIN, ESTIMATE에는 통계 줄을 붙이지 않는다. 이 문서는 4단계까지 예약해 둔 CREATE TABLE 열 정의 뒤의 확장과 새 SCAN 명령을 정의한다.

## 2. 배경 개념

[2.1] 정렬 키는 행을 저장할 때의 순서를 정한다. 키가 (지역, 날짜)이면 먼저 지역으로 정렬하고 같은 지역 안에서 날짜로 정렬한다. 전체 테이블의 날짜가 오름차순인 것은 아니다. 그래서 지역을 고정한 날짜 조건은 좁은 구간을 가리킬 수 있지만 날짜만 고정하면 여러 지역을 읽어야 할 수 있다. 정렬 키는 고유 키가 아니므로 같은 키의 행도 여러 개 남는다.

[2.2] 희소 인덱스는 모든 행의 키를 보관하는 대신 정렬된 행을 덩어리로 나누고 덩어리마다 표식을 남긴다. 이 단계에서는 덩어리를 그래뉼이라 부르고 표식에 그래뉼 첫 행의 키를 담는다. 다음 표식의 키는 현재 그래뉼의 마지막 키보다 작을 수 없다. 따라서 두 표식이 만드는 범위와 질의 범위를 비교하면 원본 값을 읽기 전에 일부 그래뉼을 제외할 수 있다.

[2.3] 그래뉼 안에서 어떤 행이 조건에 맞는지는 표식만으로 알 수 없다. 후보 그래뉼을 골랐으면 그 안의 행을 모두 읽고 WHERE를 평가한다. 조건에 맞는 행 수보다 읽은 행 수가 클 수 있고 실제 일치 행이 없는 그래뉼도 읽을 수 있다. 같은 키가 그래뉼 경계에 걸치면 다음 표식과 같은 키가 앞 그래뉼에도 있을 수 있다.

[2.4] [ClickHouse MergeTree 문서](https://clickhouse.com/docs/engines/table-engines/mergetree-family/mergetree)는 정렬된 데이터와 그래뉼 첫 키의 표식, 그래뉼 단위 읽기를 설명한다. 이 단계는 그 개념을 메모리 엔진으로 옮긴 연습 모델이다. 실제 MergeTree의 데이터 파트, 병합, 가변 그래뉼, 보조 인덱스와 일반적인 조건 분석기를 구현하지 않는다. 이 문서가 정한 고정 행 수와 보수적인 후보 선정 규칙을 따른다.

## 3. 입출력 규칙

[3.1] UTF-8, 명령 한 줄, COPY 데이터, 오류 뒤 계속 처리, 종료 규칙은 앞 단계 그대로다. SCAN과 GRANULARITY를 키워드에 더하고 나머지 토큰은 1단계 규칙을 쓴다.

[3.2] 성공한 SCAN은 먼저 감싼 SELECT의 머리줄과 결과 행을 기존 형식으로 출력하고 마지막에 `READ granules=<G> rows=<R>` 한 줄을 출력한다. G와 R은 음이 아닌 10진 정수다. SELECT가 결과 행을 내지 않아도 머리줄과 통계 줄은 출력한다. 오류가 있으면 오류 줄 하나만 출력하고 머리줄이나 통계 줄은 출력하지 않는다.

## 4. 명령 규칙

### 정렬 정의

[4.1] 기존 `CREATE TABLE <테이블> ( <열 정의> , … )` 뒤에 `ORDER BY ( <첫 키 열> , <둘째 키 열> ) GRANULARITY <g>`를 붙일 수 있다. 두 열과 g는 모두 필수다. 키 열은 서로 다른 이름 두 개이며 이미 정의한 열이어야 한다. g는 정수 토큰 하나이고 1~65,536이어야 한다. ASC, DESC, NULLS 절과 세 번째 키 열은 받지 않는다. 정렬 정의를 붙이지 않은 CREATE TABLE은 앞 단계 그대로다.

[4.2] 모든 행을 첫 키 열, 둘째 키 열 순으로 오름차순 정렬한 것을 이 테이블의 논리적인 정렬 순서라 한다. 각 열의 값 비교는 1단계 4.9를 따르되 NULL은 NULL이 아닌 모든 값 뒤에 온다. 두 키가 모두 같으면 성공한 INSERT·COPY로 먼저 들어온 행이 앞선다. 명령 하나 안에서는 행을 쓴 순서다. NULL 키도 허용하고 중복 행을 지우지 않는다.

[4.3] INSERT·COPY는 앞 단계처럼 행을 추가한다. 추가된 행을 포함해 4.2의 순서를 적용하고 그래뉼을 다시 정한 결과가 다음 명령에 보여야 한다. 내부에서 정렬과 인덱스를 갱신하는 시점과 저장 표현은 자유다. 실패한 적재는 정렬 순서·표식·통계를 바꾸지 않는다. 사전 집계와 일별 스케치에도 앞 단계대로 성공한 행만 반영한다.

[4.4] 정렬된 N행에 0부터 N−1까지 위치를 붙인다. 그래뉼 i는 위치 i×g부터 min((i+1)×g, N) 바로 앞까지다. i는 0부터 시작하며 그래뉼 수는 N/g를 올림한 수다. 마지막 그래뉼은 g행보다 작을 수 있고 빈 테이블에는 그래뉼이 없다. 표식 F[i]는 그래뉼 i 첫 행의 두 키 값이다.

[4.5] 문법에 맞는 CREATE TABLE은 테이블·사전 집계·스케치 중 그 이름이 이미 있으면 `ERR EXISTS`, 키 열을 쓴 순서로 처음 없는 열이 있으면 `ERR NO_COLUMN <열>`, g가 범위 밖이면 `ERR GRANULARITY`의 순서로 확인한다. 키 열의 자료형은 INT와 STRING을 모두 허용한다. 같은 키 열 두 번, 키 개수 오류, 정렬 정의 일부 누락은 문법 오류이므로 이 의미 확인보다 먼저 `ERR SYNTAX`다.

### SCAN

[4.6] `SCAN <SELECT 문>`은 SELECT를 실행하고 읽기 통계를 더한다. 뒤에는 1~4단계의 완전한 SELECT 한 개가 오며 GROUPING SETS·ROLLUP·CUBE와 APPROX_COUNT_DISTINCT도 허용한다. SCAN 뒤 SELECT의 문법과 의미를 기존 SELECT와 같은 순서로 확인한 다음 정렬 정의가 없는 테이블이면 `ERR NO_INDEX`다. SCAN에 ESTIMATE나 EXPLAIN을 감쌀 수는 없다.

[4.7] SCAN은 항상 원본 테이블을 읽는다. 사전 집계나 일별 스케치로 대신 답하지 않는다. 결과 값과 순서는 원본 테이블의 모든 행에 감싼 SELECT를 실행한 결과와 같아야 한다. 기존 SELECT와 EXPLAIN의 사전 집계 선택 규칙은 그대로이며 SCAN을 실행해도 그 뒤의 선택과 출력이 바뀌지 않는다.

### 후보 범위를 만드는 조건

[4.8] 후보 선정에 사용하는 조건을 아래처럼 제한한다. WHERE가 없거나 WHERE의 어느 위치에든 논리 연산자 OR 또는 NOT이 있으면 후보 범위는 전체다. IS NOT NULL의 NOT은 이 논리 연산자에 포함하지 않는다. 그렇지 않으면 괄호를 벗기고 AND로 연결된 비교들을 모은다. 이 중 정렬 키 열을 왼쪽에 두고 NULL 아닌 리터럴과 `=`, `<`, `<=`, `>`, `>=`로 비교하는 조건만 후보 범위에 사용한다. `<>`, IS NULL, IS NOT NULL, NULL과의 비교, 정렬 키가 아닌 열의 비교는 후보 선정에서 무시한다. 무시한 비교도 읽은 행의 WHERE를 평가할 때는 원래대로 적용한다.

[4.9] 범위 계산에는 값보다 작은 가상 경계 −∞와 NULL을 포함한 모든 값보다 큰 가상 경계 +∞를 사용한다. 두 키를 묶은 값은 첫 키를 먼저 비교하고 같으면 둘째 키를 비교하는 사전식 순서다. 가상 경계는 실제 값이나 NULL이 아니며 입력과 출력에 쓰지 않는다. 표식 키의 NULL 순서는 4.2와 같다.

[4.10] 4.8에서 모은 첫 키의 각 비교를 일차원 구간으로 바꾸어 교집합 I1을 만든다. `k = a`는 [a,a], `k >= a`는 [a,+∞), `k > a`는 (a,+∞), `k <= a`는 (−∞,a], `k < a`는 (−∞,a)다. 해당 비교가 없으면 (−∞,+∞)다. 같은 쪽 경계가 여러 개면 더 좁은 것을 남기고 같은 값의 경계 중에는 열린 경계가 더 좁다. 아래 경계가 위보다 크거나 두 값이 같은데 한쪽이라도 열려 있으면 빈 구간이다. 정수에서 인접한 두 값 사이처럼 실제 값의 존재 여부를 별도로 따져 구간을 더 줄이지는 않는다.

[4.11] I1이 빈 구간이면 최종 후보 범위 Q도 빈 구간이다. I1이 닫힌 한 점 [a,a]이면 둘째 키의 비교도 4.10대로 모아 I2를 만든다. I2가 비면 Q도 비고, 그렇지 않으면 I2의 아래·위 경계를 각각 (a, 아래 경계), (a, 위 경계)로 바꾼 것이 Q다. 각 경계의 열림·닫힘은 I2와 같다. 둘째 키 조건이 없어도 I2를 전체 구간으로 만들어 같은 규칙을 쓴다.

[4.12] I1이 빈 구간도 한 점도 아니면 둘째 키 조건은 후보 선정에 사용하지 않는다. I1의 유한한 아래 경계가 닫힌 a이면 Q의 아래 경계는 닫힌 (a,−∞), 열린 a이면 열린 (a,+∞)다. 유한한 위 경계가 닫힌 b이면 Q의 위 경계는 닫힌 (b,+∞), 열린 b이면 열린 (b,−∞)다. I1의 아래·위가 무한 경계이면 Q에서도 모든 두 키 값보다 작은 경계·큰 경계로 유지한다. 둘째 키만 조건에 있으면 Q는 전체다.

### 표식과 읽기 통계

[4.13] 마지막이 아닌 그래뉼 i의 표식 구간은 두 끝을 포함한 [F[i], F[i+1]]이다. 마지막 그래뉼의 표식 구간은 [F[i], +∞)다. 실제 마지막 행의 키로 위 경계를 좁히지 않는다. Q와 표식 구간이 겹치는 그래뉼을 모두 후보로 고른다. 겹침은 두 구간 경계를 비교해 판정하며 경계가 같을 때 양쪽 구간이 그 경계를 포함해야 겹친다. I1·I2와 마찬가지로 구간 안의 실제 키 존재 여부는 따로 확인하지 않는다.

[4.14] 표식 구간의 F[i+1]을 포함하는 것은 의도적인 규칙이다. 다음 그래뉼과 같은 키가 현재 그래뉼 끝에도 있을 수 있기 때문이다. 다음 표식과 같은 키를 찾을 때 현재 그래뉼도 읽을 수 있다. 또 마지막 표식보다 큰 값을 찾아 일치 행이 없어도 마지막 그래뉼을 읽을 수 있다. 키별 최솟값·최댓값이나 실제 일치 행을 미리 조사해 이 규칙보다 후보를 더 줄이지 않는다.

[4.15] 후보 그래뉼을 한 번씩 읽고 그 안의 모든 행에 원래 WHERE를 적용한다. G는 후보 그래뉼 수이고 R은 후보 그래뉼들의 실제 행 수의 합이다. WHERE를 통과한 행 수, 읽은 열의 수, 그룹 수가 아니다. 집계 항목이 여러 개거나 그룹 집합이 여러 개여도 같은 행을 거듭 세지 않는다. LIMIT 0과 COUNT(*)도 같은 후보를 읽으며 읽기 생략이나 LIMIT에 따른 조기 중단은 하지 않는다. 표식을 비교한 횟수와 정렬·적재 때 접근한 행은 통계에 세지 않는다.

[4.16] SCAN을 거듭 실행해도 매번 현재 테이블과 조건으로 G와 R을 계산한다. 앞의 SCAN 결과를 재사용해 읽기 통계를 0으로 만들지 않는다. 후보가 없으면 G와 R은 모두 0이며 빈 입력에 대한 SELECT 결과를 먼저 출력한다. 일반 SELECT와 ESTIMATE에 읽기 통계를 누적하지 않는다.

## 5. 오류 규칙

[5.1] 새 오류는 `ERR GRANULARITY`와 `ERR NO_INDEX`다. SCAN 자체의 꼴이나 뒤의 SELECT 문법이 틀리면 `ERR SYNTAX`다. SELECT의 기존 오류 확인이 NO_INDEX보다 앞선다. 오류 명령은 상태를 바꾸지 않는다.

[5.2] 앞 단계에서 예약해 둔 입력 중 이 단계가 정의한 꼴을 제외한 범위는 유지한다. CREATE TABLE의 열 정의 뒤에 ORDER 이외의 토큰이 오는 확장은 여전히 출력을 정하지 않는다. ORDER로 시작한 확장은 4.1의 꼴에 맞지 않으면 문법 오류다. SCAN 안 SELECT의 알 수 없는 함수에 대해서도 4단계 5.2의 예약 범위를 적용한다. 세미콜론 등으로 토큰화 자체가 실패하는 줄은 예약 확장이 아니라 기존처럼 오류다.

## 6. 예시

[6.1] 예시의 `> `는 입력 표시다. 실제 입력에는 붙이지 않는다. 6.2는 새 실행이며 6.3과 6.4는 6.2를 이어서 실행한다.

[6.2] g=2이며 정렬된 키는 (1,1), (1,2), (1,2), (1,3), (2,1), (2,2), (3,1) 순서다. 그래뉼 첫 키는 (1,1), (1,2), (2,1), (3,1)이다. tenant = 1만 지정하면 둘째 표식과 셋째 표식 사이의 그래뉼까지 읽고 둘째 키만 지정하면 모든 그래뉼을 읽는다.

```
> CREATE TABLE events (tenant INT, day INT, amount INT) ORDER BY (tenant, day) GRANULARITY 2
> INSERT INTO events VALUES (2, 2, 60), (1, 2, 20), (3, 1, 70), (1, 1, 10), (2, 1, 50), (1, 3, 40), (1, 2, 30)
> SCAN SELECT COUNT(*), SUM(amount) FROM events WHERE tenant = 1
COUNT(*)|SUM(amount)
4|100
READ granules=2 rows=4
> SCAN SELECT COUNT(*), SUM(amount) FROM events WHERE day = 2
COUNT(*)|SUM(amount)
3|110
READ granules=4 rows=7
> SCAN SELECT COUNT(*), SUM(amount) FROM events WHERE tenant = 1 AND day = 2
COUNT(*)|SUM(amount)
2|50
READ granules=2 rows=4
> SCAN SELECT COUNT(*) FROM events WHERE tenant > 1 AND tenant < 3
COUNT(*)
2
READ granules=2 rows=4
> SCAN SELECT COUNT(*) FROM events WHERE tenant = 9
COUNT(*)
0
READ granules=1 rows=1
> SCAN SELECT COUNT(*) FROM events WHERE tenant = 1 AND tenant = 2
COUNT(*)
0
READ granules=0 rows=0
```

[6.3] OR를 포함하면 후보를 좁히지 않는다. LIMIT은 읽은 양을 줄이지 않는다. 기존 SELECT와 EXPLAIN은 사전 집계를 쓰지만 SCAN은 원본을 읽는다.

```
> SCAN SELECT COUNT(*) FROM events WHERE tenant = 1 OR tenant = 3
COUNT(*)
5
READ granules=4 rows=7
> SCAN SELECT COUNT(*) FROM events WHERE tenant = 1 LIMIT 0
COUNT(*)
READ granules=2 rows=4
> CREATE AGGREGATE totals AS SELECT tenant, COUNT(*), SUM(amount) FROM events GROUP BY tenant
> EXPLAIN SELECT SUM(amount) FROM events WHERE tenant = 1
AGGREGATE totals
> SELECT SUM(amount) FROM events WHERE tenant = 1
SUM(amount)
100
> SCAN SELECT SUM(amount) FROM events WHERE tenant = 1
SUM(amount)
100
READ granules=2 rows=4
```

[6.4] 새 행은 적재 순서가 아니라 정렬 키 순서의 자리에 들어간다. (1,2) 키가 세 행으로 늘어 첫째와 둘째 그래뉼에 걸친다. 같은 키를 지우지 않고 집계하며 실패한 적재는 행을 남기지 않는다.

```
> INSERT INTO events VALUES (1, 2, 5)
> SCAN SELECT COUNT(*), SUM(amount) FROM events WHERE tenant = 1 AND day = 2
COUNT(*)|SUM(amount)
3|55
READ granules=2 rows=4
> INSERT INTO events VALUES (1, 2, 99), (2, 1)
ERR VALUE_COUNT
> SCAN SELECT COUNT(*) FROM events
COUNT(*)
8
READ granules=4 rows=8
> CREATE TABLE plain (x INT)
> SCAN SELECT missing FROM plain
ERR NO_COLUMN missing
> SCAN SELECT COUNT(*) FROM plain
ERR NO_INDEX
> CREATE TABLE bad (x INT, y INT) ORDER BY (x, x) GRANULARITY 0
ERR SYNTAX
> CREATE TABLE bad (x INT, y INT) ORDER BY (x, z) GRANULARITY 0
ERR NO_COLUMN z
> CREATE TABLE bad (x INT, y INT) ORDER BY (x, y) GRANULARITY 0
ERR GRANULARITY
```

## 7. 검사 방식

[7.1] 테스트마다 run.sh를 새 프로세스로 실행하고 입력 줄을 LF로 이어 표준 입력에 넣은 뒤 닫는다. 종료 코드가 0이어야 한다. 표준 출력은 줄 끝 공백·탭·CR과 맨 끝 빈 줄을 제거한 뒤 기대 출력과 줄별로 정확히 비교한다. READ 줄의 수치도 정확히 같아야 한다. 일반 테스트의 실행 제한 시간은 10초이며 컴파일 시간은 제외한다.

[7.2] 앞 단계의 입력 한계를 유지한다. 새 검사에서는 테이블의 정렬 키가 언제나 두 열이고 g는 1~65,536이다. INT·STRING·NULL 키, 같은 키가 여러 그래뉼에 걸친 입력, g보다 작은 마지막 그래뉼, 빈 테이블, 적재 오류 뒤의 조회를 포함한다. 후보 선정에 쓰는 비교와 쓰지 않는 비교를 함께 검사한다. SCAN의 GROUPING SETS·ROLLUP·CUBE와 APPROX_COUNT_DISTINCT도 검사한다.

[7.3] 새 대규모 테스트는 INT 키 두 열과 INT 측정값 한 열인 테이블에 COPY로 2,000,000행을 넣는다. 첫 키는 200,000종이고 첫 키마다 둘째 키 10종이 있으며 입력 순서는 정렬되어 있지 않다. g=128이다. SCAN 100,000개 대부분은 첫 키의 한 값 또는 좁은 범위를 지정하며 일부는 둘째 키 조건을 함께 쓴다. 뒤 열만 지정하는 SCAN은 10개 이하다. 중간에 소량의 행을 추가하는 INSERT를 최대 두 번 섞는다. 제한 시간은 60초다. 이 규모는 선택적인 조회에서도 모든 원본 행을 훑는 실행과 표식으로 고른 그래뉼만 읽는 실행의 차이를 검사한다.

[7.4] 1~4단계 테스트도 누적 실행한다. 앞 단계의 SELECT·EXPLAIN·ESTIMATE 출력은 바뀌면 안 된다. 5.2가 출력을 정하지 않은 입력은 넣지 않는다. 기존 대규모 테스트와 새 대규모 테스트는 최대 힙 256MB에서도 처리해야 한다. 힙 상한은 엔진이 자동 검사하지 않으므로 2단계 7.5의 직접 실행 방식으로 확인한다.
