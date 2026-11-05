# 4단계: HyperLogLog 근사 고유 수

## 1. 개요

[1.1] 4단계는 집계 항목 APPROX_COUNT_DISTINCT와 일별 스케치를 더한다. 같은 방문자가 여러 날 방문해도 기간 전체에서는 한 명이다. 일별 고유 수를 더하는 대신 일별 스케치를 병합해 기간의 고유 수를 추정한다. 기간 조회에서는 정확한 고유 수와 상대 오차도 함께 출력한다. 목표 구현 시간은 2~3시간이다.

[1.2] 1~3단계의 명령과 출력은 그대로 유지한다. 이 문서는 3단계 5.2가 출력을 정하지 않은 경우 가운데 CREATE SKETCH, ESTIMATE 명령과 APPROX_COUNT_DISTINCT 항목의 뜻을 정한다. 앞 단계의 사전 집계에 근사 측정값을 추가하지는 않는다.

## 2. 배경 개념

[2.1] 정확한 고유 수는 이미 본 값을 구별할 정보가 필요하다. 고유 수가 늘면 이 정보도 커진다. HyperLogLog는 값들을 해시한 비트열의 특징만 모아 고정된 수의 레지스터에 남긴다. 해시의 비트가 고르게 퍼진다고 가정할 때 앞에 0이 길게 이어지는 비트열은 드물다. 그런 비트열을 보았다는 사실을 값이 많이 들어왔다는 증거로 삼는다.

[2.2] 레지스터는 비트열의 관측값을 담는 작은 정수 칸이다. 정밀도 p를 정하면 레지스터 수 m은 2^p다. 해시의 앞 p비트가 레지스터를 고르고 나머지 비트에서 처음 1이 나오는 위치를 관측한다. 레지스터마다 가장 큰 관측값만 남기므로 같은 값을 반복해서 넣어도 스케치가 바뀌지 않는다.

[2.3] 같은 정밀도의 스케치 둘은 같은 위치의 레지스터 중 큰 값을 취해 합친다. 합친 결과는 두 입력을 한 스케치에 넣은 결과와 같다. 값이 겹쳐도 별도 중복 제거 없이 병합할 수 있다. 추정값끼리 더하거나 평균하는 것은 스케치 병합이 아니다. 빈 스케치의 레지스터는 모두 0이므로 다른 스케치와 합쳐도 상대 스케치는 그대로다.

[2.4] 레지스터가 거의 비어 있을 때 기본 추정식은 오차가 크다. 이때는 비어 있는 레지스터 수로 보정한다. 상대 오차는 실제 값과 추정값의 차이를 실제 값으로 나눈 비율이다. 여기서는 차이의 절댓값을 사용한다. 정확한 고유 수가 0이면 나눌 수 없으므로 출력 규칙에서 따로 정한다.

[2.5] 이 단계의 기본 추정식, 계수, 작은 범위 보정은 [Flajolet 외, AofA 2007](https://algo.inria.fr/flajolet/Publications/FlFuGaMe07.pdf)의 Figure 3을 따른다. 원문의 32비트 해시는 이 단계에서 64비트로 바꾸고 큰 범위 보정은 생략한다. 해시는 JDK의 [SHA-256](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html) 결과에서 앞 64비트를 사용한다. 난수 시드와 구현별 해시 함수는 쓰지 않는다.

[2.6] [Heule 외, EDBT 2013](https://stefanheule.com/papers/edbt13-hyperloglog.pdf)은 64비트 해시, 경험적 편향 보정, 희소 표현 등을 다룬다. 이 단계에서는 경험적 편향 보정과 희소 표현을 구현하지 않는다. 논문의 오차 분석은 통계적 성질이며 개별 입력의 오차 상한을 보장하지 않는다. 채점에서는 아래 규칙으로 계산한 출력과 정확히 일치하는지 확인한다.

## 3. 입출력 규칙

[3.1] 1~3단계의 UTF-8 표준 입출력, 명령 한 줄 처리, COPY 데이터 줄, 종료 규칙을 그대로 따른다. 새 명령의 토큰과 이름, 정수, 문자열도 1단계 4.1~4.9를 따른다. APPROX_COUNT_DISTINCT, SKETCH, ON, PRECISION, ESTIMATE, TO를 키워드에 더한다.

[3.2] 이 단계가 출력하는 추정값과 상대 오차는 소수 여섯 자리다. 계산 중에는 반올림하지 않는다. 출력할 최종 Double을 `BigDecimal.valueOf(값)`으로 바꾼 뒤 소수 여섯 자리 HALF_UP으로 반올림한 십진 표기와 같아야 한다. 지수 표기는 쓰지 않고 반올림한 0은 `0.000000`으로 쓴다. 정확한 고유 수는 앞 단계의 COUNT처럼 정수로 쓴다.

## 4. 명령 규칙

### 해시와 스케치의 값

[4.1] NULL은 해시하지 않는다. INT는 앞 단계의 출력과 같은 10진 문자열로 바꾼다. STRING은 작은따옴표를 붙이지 않은 값 자체를 사용한다. 이 문자열을 UTF-8 바이트로 바꾸고 BOM, 종결 0바이트, 자료형 접두사를 붙이지 않은 채 SHA-256을 계산한다. 따라서 서로 다른 테이블의 INT 1과 STRING '1'은 같은 해시지만, 열 하나 안에서 값의 자료형이 섞이는 일은 없다. 빈 문자열은 길이가 0인 바이트열로 해시한다.

[4.2] SHA-256 결과의 첫 바이트를 최상위 바이트로 삼아 앞 8바이트를 잇는다. 각 바이트는 0~255의 부호 없는 값이며 결과 h는 부호 없는 64비트 비트열이다. 비트를 최상위부터 읽어 앞 p비트를 0 이상 m 미만인 레지스터 번호 j로 삼는다. 나머지 64−p비트는 w다. h의 최상위 비트가 1이어도 음수 취급으로 비트를 채우거나 번호를 음수로 만들지 않는다.

[4.3] 새 스케치는 m개의 레지스터가 모두 0이다. 값을 넣을 때 w의 앞자리 0 개수에 1을 더한 수를 r로 삼는다. w가 모두 0이면 r은 65−p다. 해당 레지스터를 기존 값과 r 중 큰 값으로 바꾼다. 예를 들어 w가 1로 시작하면 r은 1이고 0001로 시작하면 4다. 앞 p비트는 0의 개수에 포함하지 않는다.

[4.4] 추정할 때 m이 16이면 α는 0.673, 32이면 0.697, 64이면 0.709다. m이 128 이상이면 α는 `0.7213 / (1.0 + 1.079 / m)`이다. 레지스터를 M이라 할 때 기본 추정값 E는 `(α × m × m) / Z`이고 Z는 모든 j에 대한 `2^(−M[j])`의 합이다. 합은 j가 0인 항부터 오름차순으로 Double에 더한다. 각 항은 정확한 2의 거듭제곱이며 α 계산의 나눗셈, E 계산의 곱셈과 나눗셈도 Double로 한다. E의 분자는 왼쪽 곱셈부터 계산한다.

[4.5] 값이 0인 레지스터 수를 V라 한다. E가 `2.5 × m` 이하이고 V가 0보다 크면 최종 추정값 A는 `m × StrictMath.log(m / V)`다. 이 나눗셈도 Double이며 log는 자연로그다. 그 밖에는 A가 E다. 추가 편향 보정, 큰 범위 보정, 정확한 값으로의 교체, 반올림 전 정수 변환은 하지 않는다. 빈 스케치의 A는 0이다.

### APPROX_COUNT_DISTINCT

[4.6] `APPROX_COUNT_DISTINCT(<열>)`을 SELECT 목록과 ORDER BY에 쓸 수 있는 집계 항목으로 더한다. INT와 STRING 열을 모두 받으며 인자는 열 이름 하나다. DISTINCT, 별표, 정밀도 인자를 붙일 수 없다. 이 항목의 정밀도는 언제나 p=10, m=1,024다. 머리줄에는 `APPROX_COUNT_DISTINCT(user)`처럼 공백 없이 쓴다.

[4.7] WHERE를 통과한 각 그룹의 NULL 아닌 열 값을 4.1~4.5로 집계한다. 그룹과 그룹 집합의 생성은 1~3단계 그대로이고 ROLLUP, CUBE, GROUPING SETS에도 쓸 수 있다. 빈 그룹과 NULL뿐인 그룹은 `0.000000`이다. SELECT의 다른 집계와 함께 쓸 수 있고 같은 항목을 반복할 수도 있다. 일별 스케치의 유무나 정밀도는 이 집계의 결과에 영향을 주지 않는다.

[4.8] ORDER BY에서 두 APPROX_COUNT_DISTINCT 값은 3.2대로 여섯 자리로 반올림한 값을 수로 비교한다. 동점이면 앞 단계의 다음 키와 기본 순서를 따른다. SELECT 목록과 ORDER BY 항목의 동일성은 앞 단계처럼 함수와 열 이름으로 정한다. 열 존재 여부는 1단계 4.19의 둘째 확인에서 검사한다.

[4.9] APPROX_COUNT_DISTINCT가 SELECT 목록에 있는 질의는 3단계 사전 집계로 답할 수 없다. 그 질의의 EXPLAIN은 `TABLE <테이블>`이다. CREATE AGGREGATE의 측정값으로 쓰면 기존 확인을 마친 뒤 `ERR NOT_MERGEABLE`이다. 기존 집계만 있는 질의의 사전 집계 선택과 EXPLAIN 출력은 바뀌지 않는다.

### 일별 스케치 정의

[4.10] `CREATE SKETCH <이름> ON <테이블> ( <값 열> ) BY <날짜 열> PRECISION <p>`는 원본 테이블의 값 열에 대한 일별 스케치를 만든다. p는 정수 토큰 하나이며 4~12여야 한다. 값 열은 INT나 STRING, 날짜 열은 INT여야 한다. 두 열이 같아도 된다. 날짜 값은 달력 날짜가 아니라 앞 단계 범위의 정수 일자다. 성공하면 아무것도 출력하지 않는다.

[4.11] 날짜 열이 NULL인 행은 일별 스케치와 기간의 정확한 고유 수에서 제외한다. 나머지 행은 날짜 값이 같은 것끼리 한 날짜로 본다. 각 날짜에는 정의한 p의 스케치가 있고 그 날짜 행의 값 열을 넣은 결과를 담는다. 값 열이 NULL뿐인 날짜는 빈 스케치다. 행이 없는 날짜도 조회할 때 빈 스케치와 같다.

[4.12] 정의할 때 이미 있는 행과 정의 뒤 성공한 INSERT·COPY의 행을 모두 반영한다. 날짜순으로 넣을 필요는 없다. 오류가 난 INSERT·COPY는 스케치와 정확한 고유 수를 바꾸지 않는다. 기간 조회는 저장된 일별 스케치를 병합해야 하며 조회 때 원본 행이나 날짜별 고유 값 집합을 다시 해시해 스케치를 만드는 방식은 이 단계의 요구를 충족하지 않는다. 저장 표현과 갱신 시점은 자유지만 각 명령에서 확인할 수 있는 상태는 이 규칙과 같아야 한다.

[4.13] 테이블, 사전 집계, 일별 스케치는 이름 공간을 함께 쓴다. CREATE TABLE·CREATE AGGREGATE도 스케치가 쓰는 이름이면 `ERR EXISTS`다. 스케치는 테이블이나 사전 집계가 아니므로 SELECT·CREATE AGGREGATE·CREATE SKETCH의 원본, INSERT·COPY의 대상으로 그 이름을 쓰면 `ERR NO_TABLE`이다.

[4.14] 문법에 맞는 CREATE SKETCH는 이름이 이미 쓰이면 `ERR EXISTS`, 원본 테이블이 없으면 `ERR NO_TABLE`, 값 열·날짜 열 순서로 처음 없는 열이 있으면 `ERR NO_COLUMN <열>`, 날짜 열이 INT가 아니면 `ERR TYPE`, p가 4~12가 아니면 `ERR PRECISION`의 순서로 확인한다. 오류가 나면 이름도 등록하지 않는다.

### 기간 조회

[4.15] `ESTIMATE <이름> FROM <시작 일자> TO <끝 일자>`는 일별 스케치의 기간을 조회한다. 두 일자는 정수 토큰이다. 시작과 끝을 모두 포함한다. 스케치가 없으면 `ERR NO_SKETCH`, 스케치가 있지만 시작이 끝보다 크면 `ERR RANGE`다. 같은 이름의 테이블이나 사전 집계만 있는 경우에도 `ERR NO_SKETCH`다.

[4.16] 기간에 든 날짜의 스케치를 레지스터 위치별 최댓값으로 합치고 합친 스케치에 4.4~4.5를 한 번 적용한다. 날짜별 추정값을 합치지 않는다. 조회가 원래 일별 레지스터를 바꾸어서는 안 된다. 빈 기간, 겹치는 기간, 같은 기간을 거듭 조회해도 조회 순서가 답에 영향을 주지 않는다.

[4.17] 정확한 고유 수 C는 날짜 열이 NULL이 아니고 기간에 드는 원본 행에서 값 열의 NULL을 제외한 서로 다른 값의 수다. 날짜가 달라도 같은 값은 한 번만 센다. 해시 충돌 여부는 정확한 고유 수에 영향을 주지 않는다. C가 0이면 상대 오차 R은 0이고 그 밖에는 `abs(A − C) / C`다. 반올림하기 전 A로 R을 계산한 뒤 A와 R을 각각 3.2대로 출력한다.

[4.18] 성공한 ESTIMATE는 `estimate|exact|relative_error` 머리줄과 `A|C|R` 결과 행, 정확히 두 줄을 출력한다. relative_error는 백분율이 아니라 비율이며 0.1은 10%다. 정확한 비교값을 얻기 위한 별도 값 저장은 허용하지만 그 값으로 스케치의 A를 대체해서는 안 된다.

## 5. 오류 규칙

[5.1] 새 명령도 명령어, 토큰과 문법, 의미 확인 순서로 처음 걸린 오류 한 줄만 출력한다. 오류가 난 명령은 상태를 바꾸지 않고 다음 명령을 처리한다. CREATE SKETCH와 ESTIMATE의 괄호·필수 토큰·정수 꼴이 틀리면 `ERR SYNTAX`다. p가 정수 꼴에는 맞지만 범위를 벗어나면 4.14 순서에 따라 `ERR PRECISION`이다.

[5.2] 3단계 5.2의 예약 범위는 이 단계에서 정의한 꼴을 제외하고 유지한다. CREATE의 둘째 토큰이 TABLE·AGGREGATE·SKETCH 중 어느 것도 아니거나, 항목 자리의 함수가 기존 함수·GROUPING·APPROX_COUNT_DISTINCT 중 어느 것도 아닌 경우는 여전히 출력을 정하지 않는다. 알려진 APPROX_COUNT_DISTINCT의 인자 꼴이 틀린 경우는 예약 범위가 아니라 `ERR SYNTAX`다. CREATE TABLE 열 정의 뒤의 확장과 모르는 영문 대문자 명령도 뒤 단계용 예약 범위로 남는다. 예약 판정은 앞 단계처럼 토큰으로 나눌 수 있는 줄에만 적용한다.

## 6. 예시

[6.1] `> `로 시작하는 줄은 입력이고 나머지는 바로 위 입력의 출력이다. 실제 입력에는 `> `를 붙이지 않는다. 6.2는 새 실행이며 6.3과 6.4는 6.2를 이어서 실행한다.

[6.2] NULL 날짜의 '가'는 일반 SELECT에는 포함되지만 일별 스케치에는 포함되지 않는다. 날짜 3은 행이 있어도 값이 NULL뿐이다.

```
> CREATE TABLE visits (day INT, user STRING)
> INSERT INTO visits VALUES (1, 'a'), (1, 'b'), (1, 'a'), (2, 'b'), (2, ''), (3, NULL), (NULL, '가')
> SELECT COUNT(DISTINCT user), APPROX_COUNT_DISTINCT(user) FROM visits
COUNT(DISTINCT user)|APPROX_COUNT_DISTINCT(user)
4|4.007833
> SELECT day, APPROX_COUNT_DISTINCT(user) FROM visits GROUP BY day
day|APPROX_COUNT_DISTINCT(user)
1|2.001956
2|2.001956
3|0.000000
NULL|1.000489
> CREATE SKETCH daily ON visits (user) BY day PRECISION 4
> ESTIMATE daily FROM 1 TO 2
estimate|exact|relative_error
3.322230|3|0.107410
> ESTIMATE daily FROM 3 TO 9
estimate|exact|relative_error
0.000000|0|0.000000
```

[6.3] 날짜 1과 2의 정확한 고유 수는 각각 2지만 기간의 고유 수는 3이다. 뒤에 행을 넣으면 기존 스케치도 갱신한다. 마지막 조회는 앞의 병합 조회가 날짜 1의 상태를 바꾸지 않았음을 보인다.

```
> ESTIMATE daily FROM 1 TO 1
estimate|exact|relative_error
2.136502|2|0.068251
> ESTIMATE daily FROM 2 TO 2
estimate|exact|relative_error
2.136502|2|0.068251
> INSERT INTO visits VALUES (2, 'a'), (3, 'c')
> ESTIMATE daily FROM 1 TO 3
estimate|exact|relative_error
4.602913|4|0.150728
> ESTIMATE daily FROM 1 TO 1
estimate|exact|relative_error
2.136502|2|0.068251
> EXPLAIN SELECT APPROX_COUNT_DISTINCT(user) FROM visits
TABLE visits
```

[6.4] 의미 오류도 정해진 순서로 처리한다. 마지막 SELECT는 문법이 틀려서 테이블 존재 여부까지 확인하지 않는다.

```
> CREATE SKETCH daily ON nope (missing) BY day PRECISION 2
ERR EXISTS
> CREATE SKETCH bad ON visits (missing) BY user PRECISION 2
ERR NO_COLUMN missing
> CREATE SKETCH bad ON visits (user) BY user PRECISION 2
ERR TYPE
> CREATE SKETCH bad ON visits (user) BY day PRECISION 2
ERR PRECISION
> ESTIMATE nope FROM 3 TO 1
ERR NO_SKETCH
> ESTIMATE daily FROM 3 TO 1
ERR RANGE
> CREATE AGGREGATE bad AS SELECT day, APPROX_COUNT_DISTINCT(user) FROM visits GROUP BY day
ERR NOT_MERGEABLE
> SELECT APPROX_COUNT_DISTINCT(*) FROM nope
ERR SYNTAX
```

## 7. 검사 방식

[7.1] 테스트마다 run.sh를 새 프로세스로 실행하고 입력 줄을 LF로 이어 표준 입력에 넣은 뒤 닫는다. 종료 코드가 0이어야 한다. 표준 출력은 줄 끝의 공백·탭·CR과 맨 끝의 빈 줄을 제거한 뒤 기대 출력과 글자 그대로 비교한다. 소수에 허용 오차를 두지 않는다. 일반 테스트 제한 시간은 실행 한 번에 10초이며 컴파일 시간은 제외한다.

[7.2] 앞 단계의 입력 한계와 검사 범위를 유지한다. 새 검사에서는 테이블당 일별 스케치가 4개 이하이고 날짜의 서로 다른 NULL 아닌 값은 366개 이하다. APPROX_COUNT_DISTINCT를 쓰는 SELECT는 모든 그룹 집합을 합친 결과 행이 512개 이하이고 서로 다른 근사 집계 항목은 4개 이하다. 기존 집계만 있는 질의에는 이 추가 한계를 적용하지 않는다.

[7.3] 새 대규모 테스트는 INT 날짜 열과 STRING 값 열이 있는 테이블에 COPY로 1,000,000행을 넣는다. 날짜는 64종이고 초기 NULL 아닌 값은 전체에서 131,071종 이하이며 여러 날짜에 같은 값이 겹친다. p=6인 스케치를 적재 앞이나 뒤에 만들고, 길이가 32~48일인 ESTIMATE 5,000개 사이에 소량의 INSERT·COPY를 섞는다. 대규모 테스트의 제한 시간은 30초다. 원본 행이나 날짜별 고유 값 집합을 조회마다 다시 해시하는 실행과 저장된 일별 스케치를 병합하는 실행의 차이를 검사한다. 정확한 고유 수도 매번 출력하므로 이를 구하는 비용도 고려해야 한다.

[7.4] 일반·대규모 검사에는 중복 값, 빈 문자열, 한글, 음수 INT, NULL, 날짜가 없는 기간, 빈 스케치 병합, 정밀도별 보정 분기와 오류 뒤 상태 보존을 포함한다. 5.2가 출력을 정하지 않은 입력은 넣지 않는다. 1~3단계 테스트도 누적 실행한다. 2단계 대규모 테스트의 최대 힙 256MB 조건은 그대로이며 이번 단계의 대규모 입력도 같은 힙에서 처리해야 한다. 이 힙 상한은 엔진이 자동 검사하지 않는다.
