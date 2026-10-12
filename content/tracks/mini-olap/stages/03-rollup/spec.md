# 3단계: 롤업과 사전 집계

## 1. 개요

[1.1] 3단계는 한 질의로 여러 수준의 소계와 총계를 내는 GROUPING SETS, ROLLUP, CUBE 와 GROUPING 함수를 더한다. 사전 집계를 만드는 명령도 더한다. 질의를 사전 집계로 답할 수 있으면 원본 테이블 대신 사전 집계로 답한다. 어느 쪽으로 답하는지는 EXPLAIN 으로 확인한다.

[1.2] 1단계와 2단계의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 5.2 가 출력을 정하지 않은 명령어 가운데 EXPLAIN, 1단계 5.4 의 첫째 경우 가운데 둘째 토큰이 AGGREGATE 인 CREATE, 셋째 경우 가운데 낱말이 GROUPING 인 항목, 다섯째 경우인 GROUP BY 뒤의 괄호는 이 문서가 출력을 정한다.

| 더하는 것 | 하는 일 | 정상 출력 |
|---|---|---|
| `GROUP BY GROUPING SETS (…)`, `GROUP BY ROLLUP (…)`, `GROUP BY CUBE (…)` | 그룹 집합 여럿의 결과를 한 질의로 낸다 | 머리줄 1줄과 결과 행 |
| `GROUPING(<열>)` | 그 행에서 그 열이 그룹 집합에서 빠졌는지 알려 준다 | SELECT 항목 |
| `CREATE AGGREGATE <이름> AS SELECT …` | 사전 집계를 만든다 | 없음 |
| `EXPLAIN SELECT …` | 그 질의를 무엇으로 답할지 알려 준다 | 1줄 |

## 2. 배경 개념

[2.1] 보고서는 상세 행과 함께 소계와 총계를 보여 준다. 지역별·상품별 판매량 표에는 지역마다 소계가 붙고 맨 끝에 총계가 붙는다. GROUP BY 하나는 수준 하나만 내므로 수준마다 질의를 따로 실행해 결과를 이어 붙여야 한다. GROUPING SETS 는 그룹 열의 집합 여럿을 한 질의에 적어 집합마다 GROUP BY 한 결과를 이어 붙인다. 그룹 열의 집합 하나를 그룹 집합이라 한다.

[2.2] ROLLUP 과 CUBE 는 자주 쓰는 GROUPING SETS 를 줄여 쓴 꼴이다. PostgreSQL 설명서에 따르면 `ROLLUP (e1, e2, e3)` 은 목록 자신과 목록의 모든 앞부분을 빈 목록까지 적은 것과 같다. 곧 (e1, e2, e3), (e1, e2), (e1), () 이다. 지역 아래에 상품이 있는 것처럼 차원이 계층을 이룰 때 쓴다. `CUBE (a, b, c)` 는 목록의 부분집합 2^3 = 8 개를 모두 그룹 집합으로 적은 것과 같다. 빈 그룹 집합 () 은 모든 행을 그룹 하나로 모은 총계다. PostgreSQL 은 GROUP BY 가 없는 집계처럼 입력 행이 없어도 빈 그룹 집합의 행 하나를 낸다.

[2.3] 결과 행에서 그 행의 그룹 집합에 들지 않은 그룹 열은 NULL 로 채운다. 그런데 그룹 열에 진짜 NULL 값이 있으면 "지역이 NULL 인 행들의 그룹" 과 "지역을 빼고 묶은 소계" 가 출력에서 똑같이 NULL 로 보인다. GROUPING(열) 은 그 열이 이 행의 그룹 집합에서 빠졌으면 1 을, 들어 있으면 0 을 내어 둘을 가른다.

[2.4] Kimball 은 원자 수준의 사실 테이블을 미리 합쳐 둔 집계 사실 테이블을 오로지 질의를 빠르게 하려고 만든다고 설명한다. 질의 도구는 질의마다 알맞은 수준의 집계 테이블을 골라야 하고 Kimball 은 이 일을 집계 안내(aggregate navigation)라 부른다. 잘 설계한 집계는 데이터베이스 색인처럼 질의를 빠르게 하되 사용자가 직접 마주치지 않는다. 이 트랙의 사전 집계도 그렇다. 질의는 언제나 원본 테이블에 하고 엔진이 그 질의에 답할 수 있는 사전 집계를 골라 쓴다.

[2.5] 기술 지문(`tech-reading` 트랙 3단계 "열 저장과 분석 질의")의 롤업은 적재할 때 차원 값이 같은 행을 합치고 원본 행을 남기지 않는다. 이 트랙의 사전 집계도 차원 값이 같은 행을 합친 결과지만 원본 행을 함께 남긴다. 그래서 사전 집계로 답할 수 없는 질의는 원본 테이블로 답한다. 이 문서에서 ROLLUP 은 SQL 의 그룹 집합 문법만 가리키고 미리 합쳐 둔 결과는 사전 집계라 부른다.

[2.6] 사전 집계로 답하려면 측정값을 다시 합칠 수 있어야 한다. 합과 개수는 나누어 구한 값을 더하면 전체의 값이 되고 최솟값과 최댓값은 나누어 구한 값의 최솟값과 최댓값이 전체의 값이다. 평균은 그렇지 않다. 크기가 다른 두 그룹의 평균을 다시 평균하면 전체 평균이 아니다. Kimball 은 비율처럼 더할 수 없는 측정값은 더할 수 있는 구성 요소를 저장해 두고 구성 요소를 먼저 합친 뒤에 최종 값을 계산하라고 권한다. 평균의 구성 요소는 합과 개수다. 고유 수는 기술 지문의 설명대로 나누어 센 값을 합칠 수 없다.

[2.7] 사전 집계에는 차원 몇 개로 묶은 결과만 있다. 그래서 질의가 묶거나 거르는 열은 모두 사전 집계의 차원이어야 한다. 차원에서 뺀 열로는 결과를 나눌 수 없고 측정값의 바탕이 된 열로 행을 거를 수도 없다. 기술 지문의 롤업에서 개별 사건의 측정값으로 행을 거를 수 없는 것과 같다. 거꾸로 사전 집계의 차원 가운데 일부로만 묶는 질의는 사전 집계의 행을 다시 묶어 답할 수 있다.

## 3. 입출력 규칙

[3.1] 1단계와 2단계의 입출력 규칙을 그대로 따른다.

## 4. 명령 규칙

### 그룹 집합

[4.1] GROUP BY 뒤에는 1단계의 열 목록 대신 `GROUPING SETS ( <그룹 집합> , … )`, `ROLLUP ( <열> , … )`, `CUBE ( <열> , … )` 가운데 하나를 쓸 수 있다. 셋 가운데 하나를 쓰면 GROUP BY 뒤에는 그것 하나만 온다. 열 목록과 섞거나 둘 이상을 잇는 것은 문법 오류다. GROUPING, SETS, ROLLUP, CUBE 는 이 단계에서 더하는 키워드다.

[4.2] GROUPING SETS 의 그룹 집합은 `( <열> , … )`, 빈 괄호 `( )`, 괄호 없는 열 이름 하나 가운데 하나이고 하나 이상 온다. 괄호 없는 열 이름 c 는 `( c )` 와 같다.

[4.3] `ROLLUP ( c1 , … , cn )` 은 (c1, …, cn), (c1, …, cn−1), …, (c1), () 의 그룹 집합 n + 1 개를 적은 GROUPING SETS 와 같다. `CUBE ( c1 , … , cn )` 은 c1, …, cn 의 부분집합 2^n 개를 모두 그룹 집합으로 적은 GROUPING SETS 와 같다. ROLLUP 과 CUBE 의 열은 하나 이상이다. 1단계의 열 목록은 그 열들로 된 그룹 집합 하나와 같다.

[4.4] 그룹 집합 하나나 ROLLUP, CUBE 의 열 목록 안에 같은 열을 두 번 쓰면 문법 오류다. GROUPING SETS 에 열의 집합이 같은 그룹 집합을 두 번 쓰는 것도 문법 오류다. `(a, b)` 와 `(b, a)` 는 열의 집합이 같다.

[4.5] GROUP BY 에 나온 열을 처음 나온 순서대로 늘어놓은 것이 그 질의의 그룹 열이다. `GROUPING SETS ((b), (a, b), ())` 의 그룹 열은 b, a 순서다. GROUPING SETS, ROLLUP, CUBE 안에 쓴 열 이름도 GROUP BY 에 나온 열 이름이므로 1단계 4.19 의 둘째 확인 대상이다. 1단계 4.19 의 넷째 확인과 4.33 의 기본 순서에서 그룹 열은 이 그룹 열이다.

[4.6] 그룹 집합마다 통과한 행을 그 그룹 집합의 열로 1단계 4.27 처럼 묶는다. 빈 그룹 집합은 1단계 4.26 처럼 통과한 행 전체를 그룹 하나로 삼고 통과한 행이 없어도 결과 행 하나를 낸다. 빈 그룹 집합이 아니면 통과한 행이 있는 그룹만 결과 행을 낸다. 질의의 결과는 모든 그룹 집합의 결과 행을 합친 것이다.

[4.7] 결과 행마다 그 행을 낸 그룹 집합이 있다. 그룹 열 가운데 그 그룹 집합에 들지 않은 열을 그 행에서 빠진 열이라 한다. 열 이름 항목이 그 행에서 빠진 열이면 값은 NULL 이다. 집계 항목은 그 행의 그룹에 든 행으로 1단계 4.28~4.31 과 2단계 4.8 대로 계산한다.

[4.8] `GROUPING(<열>)` 은 SELECT 목록과 ORDER BY 키에 쓰는 항목이다. 1단계 4.15 의 열 이름도 집계도 아닌 셋째 종류다. 그 열이 그 행에서 빠진 열이면 1, 아니면 0 인 정수다. 머리줄에는 `GROUPING(region)` 처럼 공백 없이 쓴다. GROUPING 의 열은 1단계 4.19 의 둘째 확인 대상이다. 넷째 확인에서는 SELECT 목록의 GROUPING 항목도 열 이름 항목과 함께 앞에서부터 보고 그 열이 그룹 열이 아니면 `ERR NOT_GROUPED <열>` 이다. 1단계의 열 목록으로 묶은 질의에서 GROUPING 은 언제나 0 이다.

[4.9] 1단계 4.33 의 기본 순서를 넓힌다. 두 행은 4.5 의 그룹 열을 순서대로 비교해 처음 다른 열에서 순서가 정해진다. 한 열에서 그 열 값이 있는 행은 그 열 값이 NULL 인 행보다 앞선다. 값이 NULL 인 행은 그 열이 빠진 행보다 앞선다. 값끼리는 1단계 4.9 의 순서이고 그 열이 빠진 두 행은 그 열에서 같다. 그래서 상세 행 뒤에 소계가 오고 총계는 맨 끝에 온다.

[4.10] ORDER BY 의 키 값은 그 항목으로 출력되는 값이다. 빠진 열의 열 이름 항목은 NULL 이므로 그 키에서는 진짜 NULL 과 같다. 모든 키에서 같은 두 행은 4.9 의 기본 순서를 따른다.

### 사전 집계

[4.11] `CREATE AGGREGATE <이름> AS SELECT <항목 목록> FROM <테이블> GROUP BY <열 목록>` 은 원본 테이블의 사전 집계를 만든다. AGGREGATE 와 AS 는 이 단계에서 더하는 키워드다. AS 뒤는 1단계와 2단계의 SELECT 꼴이되 WHERE, ORDER BY, LIMIT 이 없고 GROUP BY 는 반드시 있으며 1단계의 열 목록이어야 한다. 항목에는 GROUPING 을 쓸 수 없다. 이를 어기면 문법 오류다. 성공하면 아무것도 출력하지 않는다.

[4.12] GROUP BY 의 열을 사전 집계의 차원이라 하고 항목 가운데 집계를 사전 집계의 측정값이라 한다. 측정값이 될 수 있는 집계는 COUNT(*), COUNT(열), SUM(열), MIN(열), MAX(열) 이다.

[4.13] 문법에 맞는 CREATE AGGREGATE 는 아래 순서로 확인한다. 첫째, 그 이름을 이미 테이블이나 사전 집계가 쓰고 있으면 `ERR EXISTS` 다. 둘째, AS 뒤의 SELECT 를 1단계 4.19 의 첫째부터 넷째까지 차례로 확인한다. 셋째, 항목에 AVG 나 COUNT(DISTINCT) 가 있으면 `ERR NOT_MERGEABLE` 이다.

[4.14] 테이블과 사전 집계는 한 이름 공간을 함께 쓴다. CREATE TABLE 도 그 이름을 사전 집계가 쓰고 있으면 `ERR EXISTS` 다. 사전 집계는 테이블이 아니어서 SELECT 와 CREATE AGGREGATE 의 FROM, INSERT, COPY 에 사전 집계의 이름을 쓰면 `ERR NO_TABLE` 이다.

[4.15] 사전 집계는 원본 테이블의 행을 차원 값이 모두 같은 것끼리 1단계 4.27 처럼 묶은 그룹마다 한 행을 두고 그 행에 측정값을 담는다. 사전 집계의 행 수는 이 그룹의 수다. 사전 집계는 언제나 원본 테이블의 모든 행을 반영한다. 사전 집계를 만든 뒤에 INSERT 나 COPY 로 넣은 행도 반영한다. 원본 테이블에 행이 없으면 사전 집계의 행 수는 0 이다.

### 사전 집계로 답하기

[4.16] 오류가 없는 SELECT 를 사전 집계 V 로 답할 수 있으려면 다음을 모두 갖춰야 한다. FROM 의 테이블이 V 의 원본 테이블이다. 질의의 그룹 열(4.5)이 모두 V 의 차원이다. WHERE 에 나온 열이 모두 V 의 차원이다. SELECT 목록의 집계마다 4.17 이 정한 측정값이 V 에 있다. GROUPING 항목은 집계가 아니므로 측정값이 없어도 된다.

[4.17] 질의의 집계마다 V 에 있어야 하는 측정값은 다음과 같다. COUNT(*) 에는 COUNT(*), COUNT(c) 에는 COUNT(c), SUM(c) 에는 SUM(c), MIN(c) 에는 MIN(c), MAX(c) 에는 MAX(c) 가 있어야 한다. AVG(c) 에는 SUM(c) 와 COUNT(c) 가 함께 있어야 한다. 측정값은 함수와 열이 모두 같아야 하므로 COUNT(*) 는 COUNT(c) 를 대신하지 못하고 COUNT(c) 도 COUNT(*) 를 대신하지 못한다. COUNT(DISTINCT c) 가 있는 질의는 어떤 사전 집계로도 답할 수 없다.

[4.18] 답할 수 있는 사전 집계가 여럿이면 그 질의를 실행하는 시점의 행 수(4.15)가 가장 적은 것을 고른다. 이 행 수는 사전 집계의 행을 모두 센 것이고 질의의 WHERE 와 상관없다. 행 수가 같으면 이름이 1단계 4.9 의 순서로 앞서는 것을 고른다.

[4.19] 고른 사전 집계가 있으면 SELECT 는 원본 테이블 대신 그 사전 집계의 행으로 결과를 계산한다. 사전 집계의 행 하나는 그 행에 합쳐진 원본 행 전체를 대신하므로 측정값을 4.17 의 짝대로 다시 합친다. 개수와 합은 더하고 최솟값과 최댓값은 다시 최솟값과 최댓값을 구한다. AVG 는 다시 합친 합을 다시 합친 개수로 나눠 1단계 4.31 대로 반올림한다. 결과는 원본 테이블로 계산한 결과와 언제나 같다. 고른 사전 집계가 없으면 원본 테이블로 계산한다.

### EXPLAIN

[4.20] `EXPLAIN <SELECT 문>` 은 SELECT 문을 실행하지 않고 그 질의를 무엇으로 답할지 한 줄로 출력한다. 4.18 로 고른 사전 집계가 있으면 `AGGREGATE <사전 집계 이름>` 이고 없으면 `TABLE <테이블 이름>` 이다. EXPLAIN 은 이 단계에서 더하는 키워드다.

[4.21] 명령어가 EXPLAIN 인 줄은 1단계 4.1~4.7 대로 줄 전체를 토큰으로 나눈다. 줄을 토큰으로 나눌 수 없거나 EXPLAIN 뒤의 토큰 열이 SELECT 문의 꼴에 맞지 않으면 `ERR SYNTAX` 다. 꼴에 맞지만 SELECT 문에 오류가 있으면 그 SELECT 문을 실행했을 때와 같은 오류 줄을 출력한다.

## 5. 오류 규칙

[5.1] 3단계에서 생기는 오류 줄은 `ERR NOT_MERGEABLE` 이다. `ERR EXISTS` 와 `ERR NO_TABLE` 은 4.13 과 4.14 대로 사전 집계의 이름에도 쓴다.

| 오류 줄 | 뜻 |
|---|---|
| `ERR NOT_MERGEABLE` | 사전 집계의 측정값이 될 수 없는 집계를 썼음 |

[5.2] 다음 네 경우의 줄이 나오면 이 문서도 그 줄부터 입력 끝까지의 출력을 정하지 않는다. 첫째, 명령어가 영문 대문자로만 이뤄졌는데 CREATE, INSERT, SELECT, COPY, EXPLAIN 이 아닌 줄이다. 둘째, 명령어가 CREATE 이고 둘째 토큰이 있는데 TABLE 도 AGGREGATE 도 아닌 줄이다. 셋째, 1단계 5.4 의 둘째 경우인 줄이다. 넷째, SELECT 목록이나 ORDER BY 의 키에서 항목이 올 자리에 낱말과 `(` 가 차례로 오는데 그 낱말이 COUNT, SUM, AVG, MIN, MAX, GROUPING 이 아닌 줄이다. 넷째 경우는 EXPLAIN 뒤의 SELECT 문과 CREATE AGGREGATE 의 항목에도 쓴다. 1단계 5.4 의 셋째 경우처럼 그 낱말 바로 앞까지의 토큰에 문법 오류가 있으면 넷째 경우에 들지 않는다. 둘째부터 넷째까지는 1단계 5.4 처럼 토큰으로 나눌 수 있는 줄에만 해당한다. `SELECT foo(qty) FROM sales;` 는 끝의 `;` 때문에 토큰으로 나눌 수 없으므로 1단계와 2단계에서처럼 `ERR SYNTAX` 다. 이 규칙은 1단계 5.3 과 다른 모든 오류보다 앞선다. 2단계 5.2 처럼 `\.` 줄이 오기 전에 입력이 끝나는 COPY 의 출력도 정하지 않는다.

[5.3] CREATE AGGREGATE 와 EXPLAIN 도 1단계 5.5 처럼 명령어, 토큰과 꼴, 4절의 확인 순서대로 확인하고 처음 걸린 오류 하나만 출력한다.

## 6. 예시

[6.1] 예시의 형식은 1단계 6.1 과 같다. 6.3 부터 6.5 까지는 6.2 의 CREATE TABLE 과 INSERT 를 먼저 실행한 것으로 보고 그 두 줄을 생략한다.

[6.2] 그룹 집합이다. ROLLUP(region, item) 은 (region, item), (region), () 세 그룹 집합을 낸다. 끝의 두 행은 region 과 item 이 모두 NULL 로 출력되지만 앞의 행은 region 이 진짜 NULL 인 그룹의 소계이고 뒤의 행은 총계다. GROUPING(region) 이 둘을 가른다. CUBE 는 (item) 그룹 집합을 더하므로 region 이 빠진 상품별 행이 총계 앞에 온다. GROUPING SETS ((item), (day)) 의 그룹 열은 item, day 순서라 item 이 빠지지 않은 행이 먼저 온다.

```
> CREATE TABLE sales (region STRING, item STRING, day INT, qty INT)
> INSERT INTO sales VALUES ('east', 'pen', 1, 3), ('east', 'ink', 1, 2), ('east', 'pen', 2, 4), ('west', 'pen', 1, 5), (NULL, 'ink', 2, 6), ('west', 'ink', 2, NULL)
> SELECT region, item, GROUPING(region), GROUPING(item), COUNT(*), SUM(qty) FROM sales GROUP BY ROLLUP(region, item)
region|item|GROUPING(region)|GROUPING(item)|COUNT(*)|SUM(qty)
'east'|'ink'|0|0|1|2
'east'|'pen'|0|0|2|7
'east'|NULL|0|1|3|9
'west'|'ink'|0|0|1|NULL
'west'|'pen'|0|0|1|5
'west'|NULL|0|1|2|5
NULL|'ink'|0|0|1|6
NULL|NULL|0|1|1|6
NULL|NULL|1|1|6|20
> SELECT region, item, COUNT(*) FROM sales GROUP BY CUBE(region, item)
region|item|COUNT(*)
'east'|'ink'|1
'east'|'pen'|2
'east'|NULL|3
'west'|'ink'|1
'west'|'pen'|1
'west'|NULL|2
NULL|'ink'|1
NULL|NULL|1
NULL|'ink'|3
NULL|'pen'|3
NULL|NULL|6
> SELECT item, day, SUM(qty) FROM sales GROUP BY GROUPING SETS ((item), (day))
item|day|SUM(qty)
'ink'|NULL|8
'pen'|NULL|12
NULL|1|10
NULL|2|10
```

[6.3] 정렬과 빈 결과다. 첫 SELECT 에서 region 이 진짜 NULL 인 행과 총계 행은 `region DESC` 의 키 값이 모두 NULL 이라 둘 다 앞으로 간다. 둘 사이는 기본 순서에 따라 진짜 NULL 인 행이 먼저다. 셋째 SELECT 처럼 통과한 행이 없어도 빈 그룹 집합은 총계 행 하나를 낸다. 같은 조건의 1단계 GROUP BY 는 머리줄만 낸다.

```
> SELECT region, SUM(qty) FROM sales GROUP BY ROLLUP(region) ORDER BY region DESC
region|SUM(qty)
NULL|6
NULL|20
'west'|5
'east'|9
> SELECT region, GROUPING(region), SUM(qty) FROM sales GROUP BY ROLLUP(region) ORDER BY SUM(qty) DESC LIMIT 2
region|GROUPING(region)|SUM(qty)
NULL|1|20
'east'|0|9
> SELECT region, COUNT(*), SUM(qty) FROM sales WHERE qty > 100 GROUP BY ROLLUP(region)
region|COUNT(*)|SUM(qty)
NULL|0|NULL
> SELECT region, COUNT(*) FROM sales WHERE qty > 100 GROUP BY region
region|COUNT(*)
> SELECT day, COUNT(*) FROM sales GROUP BY GROUPING SETS ((day), ())
day|COUNT(*)
1|3
2|3
NULL|6
```

[6.4] 사전 집계와 EXPLAIN 이다. by_region 의 행은 east, west, NULL 세 개이고 by_region_day 의 행은 (east, 1), (east, 2), (west, 1), (west, 2), (NULL, 2) 다섯 개다. SUM(qty) 는 둘 다 답할 수 있어 행이 적은 by_region 을 고른다. AVG(qty) 에는 SUM(qty) 와 COUNT(qty) 가 함께 필요해 by_region_day 만 답할 수 있다. east 의 평균은 합 5 와 4 를 더한 9 를 개수 2 와 1 을 더한 3 으로 나눈 3.00 이다. 날짜별 평균 2.5 와 4 를 다시 평균한 3.25 가 아니다. GROUPING 은 측정값이 없어도 되므로 GROUPING(region) 을 더한 ROLLUP(region) 질의도 by_region 으로 답한다. qty 로 거르는 질의, 차원이 아닌 item 으로 묶는 질의, COUNT(DISTINCT) 가 있는 질의는 원본 테이블로 답한다.

```
> CREATE AGGREGATE by_region_day AS SELECT region, day, COUNT(*), SUM(qty), COUNT(qty) FROM sales GROUP BY region, day
> CREATE AGGREGATE by_region AS SELECT region, SUM(qty), MAX(qty) FROM sales GROUP BY region
> EXPLAIN SELECT region, SUM(qty) FROM sales GROUP BY region
AGGREGATE by_region
> EXPLAIN SELECT region, AVG(qty) FROM sales GROUP BY region
AGGREGATE by_region_day
> SELECT region, AVG(qty) FROM sales GROUP BY region
region|AVG(qty)
'east'|3.00
'west'|5.00
NULL|6.00
> EXPLAIN SELECT day, SUM(qty) FROM sales WHERE region = 'east' GROUP BY ROLLUP(day)
AGGREGATE by_region_day
> EXPLAIN SELECT region, GROUPING(region), SUM(qty) FROM sales GROUP BY ROLLUP(region)
AGGREGATE by_region
> EXPLAIN SELECT region, SUM(qty) FROM sales WHERE qty > 2 GROUP BY region
TABLE sales
> EXPLAIN SELECT item, SUM(qty) FROM sales GROUP BY item
TABLE sales
> EXPLAIN SELECT COUNT(DISTINCT region) FROM sales
TABLE sales
> EXPLAIN SELECT MAX(qty) FROM sales
AGGREGATE by_region
> EXPLAIN SELECT region, COUNT(qty) FROM sales GROUP BY region
AGGREGATE by_region_day
```

[6.5] 오류다. AVG 와 COUNT(DISTINCT) 는 측정값이 될 수 없다. 사전 집계와 테이블은 이름을 함께 쓰고 사전 집계는 FROM 에 쓸 수 없다. WHERE 가 있거나 GROUP BY 가 없는 정의는 문법 오류다. 이 블록은 6.4 의 두 CREATE AGGREGATE 도 먼저 실행한 것으로 본다.

```
> CREATE AGGREGATE bad AS SELECT region, AVG(qty) FROM sales GROUP BY region
ERR NOT_MERGEABLE
> CREATE AGGREGATE bad AS SELECT region, COUNT(DISTINCT item) FROM sales GROUP BY region
ERR NOT_MERGEABLE
> CREATE AGGREGATE by_region AS SELECT day, COUNT(*) FROM sales GROUP BY day
ERR EXISTS
> CREATE AGGREGATE sales AS SELECT day, COUNT(*) FROM sales GROUP BY day
ERR EXISTS
> CREATE TABLE by_region (a INT)
ERR EXISTS
> SELECT COUNT(*) FROM by_region
ERR NO_TABLE
> CREATE AGGREGATE w AS SELECT region, SUM(qty) FROM sales WHERE day = 1 GROUP BY region
ERR SYNTAX
> CREATE AGGREGATE w AS SELECT SUM(qty) FROM sales
ERR SYNTAX
> CREATE AGGREGATE w AS SELECT item, SUM(qty) FROM sales GROUP BY region
ERR NOT_GROUPED item
> CREATE AGGREGATE w AS SELECT region, SUM(item) FROM sales GROUP BY region
ERR TYPE
> EXPLAIN SELECT region, SUM(qty) FROM nope GROUP BY region
ERR NO_TABLE
> EXPLAIN SELECT region, SUM(qty) FROM sales
ERR NOT_GROUPED region
> SELECT region, COUNT(*) FROM sales GROUP BY GROUPING SETS ((region), (region))
ERR SYNTAX
> SELECT region, COUNT(*) FROM sales GROUP BY ROLLUP(region, region)
ERR SYNTAX
> SELECT region, COUNT(*) FROM sales GROUP BY region, ROLLUP(item)
ERR SYNTAX
> SELECT GROUPING(item), COUNT(*) FROM sales GROUP BY ROLLUP(region)
ERR NOT_GROUPED item
```

[6.6] 사전 집계의 선택은 질의를 실행하는 시점의 행 수로 정한다. 처음에는 by_a 와 by_b 의 행이 둘씩이라 이름이 앞선 by_a 를 고른다. a 가 z 인 행을 넣으면 by_a 의 행이 셋으로 늘어 by_b 를 고른다. 마지막 EXPLAIN 에서 `b = 2` 에 맞는 행은 by_b 와 by_ab 에 하나씩 있지만 행 수가 2 와 4 라 by_b 를 고른다. 이 블록은 새 실행이다.

```
> CREATE TABLE t (a STRING, b INT, v INT)
> INSERT INTO t VALUES ('x', 1, 10), ('y', 1, 20), ('x', 2, 30)
> CREATE AGGREGATE by_b AS SELECT b, SUM(v) FROM t GROUP BY b
> CREATE AGGREGATE by_a AS SELECT a, SUM(v) FROM t GROUP BY a
> EXPLAIN SELECT SUM(v) FROM t
AGGREGATE by_a
> INSERT INTO t VALUES ('z', 1, 40)
> EXPLAIN SELECT SUM(v) FROM t
AGGREGATE by_b
> SELECT SUM(v) FROM t
SUM(v)
100
> EXPLAIN SELECT b, SUM(v) FROM t WHERE a = 'x' GROUP BY b
TABLE t
> CREATE AGGREGATE by_ab AS SELECT a, b, SUM(v) FROM t GROUP BY a, b
> EXPLAIN SELECT SUM(v) FROM t WHERE b = 2
AGGREGATE by_b
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계와 2단계와 같다.

[7.2] 검사 입력의 CUBE 는 열이 4개 이하이고 GROUPING SETS 의 그룹 집합은 16개 이하다. 사전 집계는 원본 테이블마다 8개 이하다.

[7.3] 3단계 검사 입력에는 5.2 가 출력을 정하지 않은 줄이 나오지 않는다.

[7.4] 대규모 테스트는 원본 테이블 하나에 COPY 로 행 1,000,000개를 넣고 사전 집계 4개를 만든다. 사전 집계 가운데 일부는 COPY 앞에, 나머지는 뒤에 만든다. 사전 집계의 행 수는 수십에서 수천이다. 그 뒤로 SELECT 와 EXPLAIN 5,000개 안팎이 이어지고 사이사이 INSERT 가 섞인다. 질의는 대부분 사전 집계로 답할 수 있고 ROLLUP 과 CUBE 를 쓰는 질의가 많다. 제한 시간은 15초다. 사전 집계로 답할 수 있는 질의도 원본 테이블을 훑어 답하는 풀이는 이 제한 시간을 넘기도록 규모를 정했다.

[7.5] 메모리 상한은 2단계 7.5 와 같다.

[7.6] 3단계를 검사할 때 1단계와 2단계 테스트도 다시 실행한다.
