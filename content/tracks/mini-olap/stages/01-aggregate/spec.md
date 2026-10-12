# 1단계: 집계와 NULL 규칙

## 1. 개요

[1.1] 이 트랙에서는 메모리 안의 테이블에 집계 질의를 실행하는 분석용 질의 엔진을 만든다. 프로그램은 표준 입력으로 SQL 을 닮은 명령을 한 줄씩 받아 테이블을 만들고 행을 넣고 집계 결과를 출력한다. 1단계는 집계 함수 다섯 가지와 NULL 규칙, WHERE 의 3값 논리, GROUP BY, ORDER BY, LIMIT 을 다룬다.

[1.2] 1단계의 명령은 아래 세 가지다.

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `CREATE TABLE <테이블> (<열> <자료형>, …)` | 테이블을 만든다 | 없음 |
| `INSERT INTO <테이블> VALUES (<값>, …), …` | 행을 넣는다 | 없음 |
| `SELECT <항목>, … FROM <테이블> …` | 집계 결과를 출력한다 | 머리줄 1줄과 결과 행 0줄 이상 |

[1.3] 문법은 SQL 의 작은 부분집합으로 고정한다. 식, 별칭, 조인, 하위 질의는 없고 SELECT 목록에는 열 이름과 집계만 온다. 이 트랙에서 공들일 곳은 문법 해석보다 집계의 의미와 실행이다.

[1.4] 2단계는 탭으로 나눈 데이터 줄을 한꺼번에 넣는 COPY 와 서로 다른 값의 수를 세는 COUNT(DISTINCT) 를 더하고 행 2,000,000개 안팎의 테이블에서 같은 질의를 제한 시간과 메모리 상한 안에 실행하게 한다. 3단계는 GROUPING SETS, ROLLUP, CUBE 와 사전 집계를 더한다. 이 문서의 입출력 규칙과 명령 규칙은 3단계까지 그대로 쓴다. 뒤 단계는 이 문서가 정한 출력을 바꾸지 않는다. 뒤 단계에서 뜻이 생기는 줄이 나오면 이 문서는 그 줄부터 입력 끝까지의 출력을 정하지 않는다. 새 명령은 뒤따르는 줄을 데이터로 읽거나 상태를 바꿀 수 있기 때문이다. 그런 줄은 5.2 와 5.4 에 적는다.

## 2. 배경 개념

[2.1] 트랜잭션 처리는 주문 한 건을 넣거나 잔액 하나를 고치는 것처럼 적은 수의 행을 자주 읽고 쓴다. 분석 처리는 수많은 행을 훑으면서 열 몇 개만 집계한다. "지역별 판매량 합계"나 "상품별 주문 수 상위 10개" 같은 질문이 분석 처리의 전형이다. 이런 질의를 받는 테이블은 흔히 사건 하나를 행 하나로 기록한 사실 테이블이다. 차원 모델링은 사건에서 잰 수치를 측정값이라 하고 그 수치를 묶고 거르는 기준이 되는 속성을 차원이라 한다. 판매 테이블이라면 지역과 상품이 차원이고 수량과 금액이 측정값이다.

[2.2] 집계 질의는 조건에 맞는 행을 골라 차원 값이 같은 행끼리 그룹으로 묶고 그룹마다 측정값을 값 하나로 줄인 뒤 결과를 정렬해 앞의 몇 행을 낸다. SQL 에서는 이 일을 WHERE, GROUP BY, 집계 함수, ORDER BY, LIMIT 이 차례로 맡는다.

[2.3] SQL 의 NULL 은 값이 없거나 알 수 없다는 표시다. NULL 은 값이 아니어서 어떤 값과도 같다고도 다르다고도 할 수 없다. 길이가 0 인 빈 문자열과 숫자 0 은 값이고 NULL 이 아니다.

[2.4] 그래서 SQL 의 조건은 참과 거짓에 알 수 없음을 더한 3값 논리를 따른다. 비교하는 두 쪽 가운데 하나가 NULL 이면 결과는 알 수 없음이다. x 가 무엇이든 `x = NULL` 도 알 수 없음이다. NOT 은 참과 거짓을 맞바꾸고 알 수 없음은 그대로 둔다. AND 는 거짓이 하나라도 있으면 거짓이고 OR 는 참이 하나라도 있으면 참이다. 그렇지 않을 때 알 수 없음이 끼어 있으면 결과는 알 수 없음이다. WHERE 는 조건이 참인 행만 남기고 거짓인 행과 알 수 없음인 행을 모두 버린다. 그래서 `x > 2 OR NOT x > 2` 도 x 가 NULL 인 행은 남기지 않는다. 값이 NULL 인지 묻는 IS NULL 과 IS NOT NULL 은 언제나 참이나 거짓이다.

[2.5] 집계 함수는 NULL 을 건너뛴다. COUNT(*) 는 행을 세므로 모든 열이 NULL 인 행도 센다. COUNT(열) 은 그 열이 NULL 이 아닌 행만 센다. SUM, AVG, MIN, MAX 는 NULL 이 아닌 값만 보고 그런 값이 하나도 없으면 NULL 을 낸다. 행이 하나도 없을 때 COUNT 는 0 이지만 SUM 은 0 이 아니라 NULL 이다.

[2.6] GROUP BY 는 NULL 을 비교와 다르게 다룬다. 그룹을 나눌 때는 NULL 끼리 같은 것으로 보아 그룹 열이 NULL 인 행이 모두 한 그룹이 된다. MySQL 설명서도 GROUP BY 에서는 두 NULL 을 같다고 본다고 적는다. `WHERE x = NULL` 은 어떤 행도 남기지 않지만 `GROUP BY x` 는 NULL 그룹을 하나 만든다.

[2.7] NULL 을 정렬하는 자리는 제품마다 다르다. PostgreSQL 은 NULL 을 어떤 값보다도 큰 것처럼 정렬해 오름차순에서는 맨 뒤에, 내림차순에서는 맨 앞에 두고 NULLS FIRST 와 NULLS LAST 로 자리를 직접 고르게도 한다. MySQL 은 오름차순에서 NULL 을 맨 앞에 둔다. 이 트랙은 PostgreSQL 과 같은 기본값을 쓰고 NULLS FIRST 와 NULLS LAST 를 받는다.

[2.8] 정수의 평균은 대개 정수가 아니다. 평균을 어떤 자료형과 자릿수로 낼지도 제품마다 다르다. 이 트랙은 평균을 소수 둘째 자리까지 반올림해 내고 반올림 규칙은 4.31 에 정한다.

## 3. 입출력 규칙

[3.1] 프로그램은 표준 입력에서 명령을 한 줄씩 읽어 입력이 끝날 때까지 처리하고 결과를 표준 출력에 쓴다.

[3.2] 입력이 끝나면 종료 코드 0 으로 끝난다.

[3.3] 입력은 UTF-8 이다. 줄은 LF(U+000A)로 나눈다. 마지막 줄 뒤에 LF 가 없어도 그 줄을 처리한다.

[3.4] 이 문서에서 공백은 U+0020 한 글자만 가리킨다. 탭(U+0009)과 CR(U+000D)은 공백이 아니다.

[3.5] 줄 앞뒤의 공백은 무시한다. 빈 줄과 공백만 있는 줄은 건너뛰고 아무것도 출력하지 않는다.

[3.6] 명령어는 줄 맨 앞부터 첫 공백 바로 앞까지다. 공백이 없으면 줄 전체가 명령어다.

[3.7] 출력은 UTF-8 이고 출력하는 줄은 모두 LF 로 끝낸다.

[3.8] 이 문서의 정규식은 문자열 전체에 적용한다.

[3.9] 프로그램은 테이블이 하나도 없는 상태에서 시작한다. 이전 실행의 상태를 이어받지 않는다.

## 4. 명령 규칙

### 토큰

[4.1] 명령어가 CREATE, INSERT, SELECT 인 줄은 줄 전체를 앞에서부터 토큰으로 나눈다. 토큰은 낱말, 정수, 문자열, 기호 네 종류다. 토큰 사이의 공백은 몇 개든 구분자일 뿐이고 어느 두 토큰 사이에도 공백이 없어도 된다. `COUNT(*)` 와 `COUNT ( * )` 는 같은 토큰 열이다. 공백이 있든 없든 토큰은 지금 자리에서 만들 수 있는 가장 긴 것으로 끊는다. 그래서 `1OR` 은 정수 `1` 과 낱말 `OR` 두 토큰이고 `ORv` 는 낱말 하나다.

[4.2] 낱말은 `[A-Za-z_][A-Za-z0-9_]*` 에 맞는 글자 열이다. 낱말 가운데 4.4 의 키워드는 키워드로 쓰이고 `^[a-z][a-z0-9_]{0,31}$` 에 맞는 낱말은 이름으로 쓰인다. 테이블 이름과 열 이름은 이름이어야 한다. 키워드도 이름도 아닌 낱말은 어느 자리에도 올 수 없다. `Sales`, `FOO`, `_x` 가 그런 낱말이다.

[4.3] 정수는 숫자로 시작하거나 `-` 바로 뒤에 숫자가 오는 자리에서 시작해 숫자가 이어지는 데까지다. 정수 토큰은 `^(0|-?[1-9][0-9]*)$` 에 맞고 −1,000,000,000 이상 1,000,000,000 이하여야 한다. `007`, `-0`, `1000000001` 은 쓸 수 없는 정수다. 숫자가 뒤따르지 않는 `-` 는 토큰이 아니다.

[4.4] 키워드는 CREATE, TABLE, INT, STRING, INSERT, INTO, VALUES, NULL, SELECT, FROM, WHERE, GROUP, BY, ORDER, ASC, DESC, NULLS, FIRST, LAST, LIMIT, AND, OR, NOT, IS, COUNT, SUM, AVG, MIN, MAX 다. 키워드는 대소문자를 구분하므로 `select` 나 `Null` 은 키워드가 아니다.

[4.5] 문자열은 작은따옴표(')로 시작해 다음 작은따옴표에서 끝난다. 두 작은따옴표 사이의 글자들이 문자열 값이고 사이에 글자가 없으면 빈 문자열이다. 문자열 값에 쓸 수 있는 글자는 U+0020 부터 U+007E 까지의 ASCII 글자와 U+AC00 부터 U+D7A3 까지의 한글 음절이다. 다만 작은따옴표와 역슬래시(\)는 쓸 수 없다. 작은따옴표를 문자열 안에 넣는 방법은 없다. 닫는 작은따옴표가 없거나 두 작은따옴표 사이에 쓸 수 없는 글자가 있으면 줄을 토큰으로 나눌 수 없다.

[4.6] 기호는 `(`, `)`, `,`, `*`, `=`, `<>`, `<`, `<=`, `>`, `>=` 열 가지다. `<=` 는 `<` 와 `=` 가 아니라 기호 하나다. `!=` 는 기호가 아니다.

[4.7] 문자열 밖에서 공백도 아니고 낱말, 정수, 문자열, 기호를 시작할 수도 없는 글자를 만나면 줄을 토큰으로 나눌 수 없다. 탭, 세미콜론(;), 큰따옴표(")가 그런 글자다.

### 자료형과 값

[4.8] 열의 자료형은 INT 와 STRING 두 가지다. INT 값은 −1,000,000,000 이상 1,000,000,000 이하의 정수다. STRING 값은 4.5 에서 쓸 수 있는 글자로 된 0글자 이상의 문자열이다. 어느 열이든 값 대신 NULL 을 담을 수 있다.

[4.9] 값의 순서는 다음과 같다. INT 값은 수의 크기 순서다. STRING 값은 앞 글자부터 차례로 유니코드 코드 포인트를 비교해 처음 다른 글자에서 순서를 정한다. 한쪽이 다른 쪽의 앞부분이면 짧은 쪽이 앞선다. 그래서 빈 문자열이 가장 앞서고 `'B'` 는 `'a'` 보다, `'z'` 는 `'가'` 보다 앞선다. NULL 은 이 순서에 들지 않는다. NULL 의 자리는 비교(4.23), 그룹(4.27), 정렬(4.33, 4.35)에서 따로 정한다.

### CREATE TABLE

[4.10] `CREATE TABLE <테이블> ( <열 정의> , … )` 는 행이 없는 테이블을 만든다. 괄호 안에는 열 정의가 하나 이상 쉼표로 나뉘어 온다. 열 정의는 열 이름 뒤에 INT 나 STRING 을 쓴 것이다. 쓴 순서가 테이블의 열 순서다. 성공하면 아무것도 출력하지 않는다.

[4.11] 한 CREATE TABLE 안에서 같은 열 이름을 두 번 쓰면 문법 오류다. 문법에 맞는 CREATE TABLE 은 같은 이름의 테이블이 이미 있으면 `ERR EXISTS` 를 출력한다.

### INSERT

[4.12] `INSERT INTO <테이블> VALUES ( <값> , … ) , ( <값> , … ) …` 는 괄호 하나를 행 하나로 보고 쓴 순서대로 테이블 끝에 넣는다. 행은 하나 이상이고 한 행의 값도 하나 이상이다. 값 자리에는 정수, 문자열, NULL 가운데 하나가 온다. 성공하면 아무것도 출력하지 않는다.

[4.13] 문법에 맞는 INSERT 는 아래 순서로 확인한다. 먼저 테이블이 없으면 `ERR NO_TABLE` 이다. 다음으로 행을 앞에서부터 하나씩 확인한다. 값의 개수가 테이블의 열 수와 다르면 `ERR VALUE_COUNT` 다. 개수가 맞으면 값을 앞에서부터 열과 짝지어 INT 열에는 정수나 NULL 이, STRING 열에는 문자열이나 NULL 이 왔는지 보고 아니면 `ERR TYPE` 이다. 앞 행에서 걸린 오류가 뒤 행의 오류보다 먼저다. 오류가 나면 한 행도 넣지 않는다.

### SELECT 의 꼴

[4.14] SELECT 는 `SELECT <항목 목록> FROM <테이블>` 뒤에 `WHERE <조건>`, `GROUP BY <열 목록>`, `ORDER BY <키 목록>`, `LIMIT <정수>` 를 이 순서로 붙인 꼴이다. 붙이는 절은 어느 것이든 뺄 수 있고 저마다 많아야 한 번 온다. 목록은 원소 하나 이상을 쉼표로 나눈 것이다.

[4.15] 항목은 열 이름이거나 집계다. 집계는 `COUNT(*)`, `COUNT(<열>)`, `SUM(<열>)`, `AVG(<열>)`, `MIN(<열>)`, `MAX(<열>)` 여섯 꼴이다. 같은 항목을 여러 번 써도 된다.

[4.16] GROUP BY 의 열 목록에는 열 이름만 온다. 같은 열을 두 번 쓰면 문법 오류다. GROUP BY 에 쓴 열을 그룹 열이라 한다.

[4.17] ORDER BY 의 키는 항목 하나 뒤에 ASC 나 DESC 를 붙이고 그 뒤에 NULLS FIRST 나 NULLS LAST 를 붙인 것이다. 붙이는 것은 둘 다 뺄 수 있다. ASC 도 DESC 도 붙이지 않으면 ASC 다.

[4.18] LIMIT 뒤에는 0 이상의 정수 하나가 온다.

### SELECT 의 확인

[4.19] 문법에 맞는 SELECT 는 아래 순서로 확인하고 처음 걸린 오류 하나를 출력한다. 첫째, FROM 의 테이블이 없으면 `ERR NO_TABLE` 이다. 둘째, 줄에 나온 열 이름을 앞에서부터 보아 테이블에 없는 것이 있으면 `ERR NO_COLUMN c` 다. c 는 그 가운데 처음 나온 열 이름이다. SELECT 목록, WHERE, GROUP BY, ORDER BY 에 나온 열 이름이 모두 대상이다. 셋째, 자료형이 맞지 않으면 `ERR TYPE` 이다. SELECT 목록과 ORDER BY 에 나온 SUM 과 AVG 의 열은 모두 INT 열이어야 하고 WHERE 의 비교는 4.22 를 지켜야 한다. COUNT, MIN, MAX 는 두 자료형의 열을 모두 받는다. 넷째, SELECT 목록의 열 이름 항목을 앞에서부터 보아 그룹 열이 아닌 것이 있으면 `ERR NOT_GROUPED c` 다. c 는 그 가운데 처음 나온 열 이름이다. GROUP BY 가 없으면 그룹 열도 없다. 다섯째, ORDER BY 의 키 가운데 SELECT 목록의 어느 항목과도 같지 않은 항목이 있으면 `ERR NOT_SELECTED` 다.

[4.20] 두 항목은 꼴과 함수 이름과 열 이름이 모두 같으면 같은 항목이다. `SUM(qty)` 와 `SUM( qty )` 는 같은 항목이고 `COUNT(*)` 와 `COUNT(qty)` 는 다른 항목이다.

### WHERE

[4.21] 조건은 비교들을 NOT, AND, OR 와 괄호로 엮은 것이다. 괄호 안의 조건이 가장 먼저 묶이고 다음으로 NOT, AND, OR 순서로 묶인다. `NOT a = 1 AND b = 2 OR c = 3` 은 `((NOT a = 1) AND b = 2) OR c = 3` 과 같다. NOT 은 비교, 괄호로 감싼 조건, 다른 NOT 앞에 올 수 있다.

[4.22] 비교는 `<열> IS NULL`, `<열> IS NOT NULL`, `<열> <연산자> <값>` 세 꼴이다. 연산자는 `=`, `<>`, `<`, `<=`, `>`, `>=` 이고 값은 정수, 문자열, NULL 가운데 하나다. 비교의 왼쪽에는 언제나 열 이름이 온다. `5 < qty` 와 `qty = price` 는 문법 오류다. `<열> <연산자> <값>` 에서 INT 열에는 정수나 NULL 이, STRING 열에는 문자열이나 NULL 이 와야 한다. 그렇지 않으면 4.19 의 셋째 확인에서 `ERR TYPE` 이다.

[4.23] 행마다 비교의 결과는 참, 거짓, 알 수 없음 가운데 하나다. `<열> <연산자> <값>` 은 그 행의 열 값이나 비교할 값이 NULL 이면 알 수 없음이다. 둘 다 NULL 이 아니면 4.9 의 순서로 비교해 연산자의 관계가 성립하면 참, 아니면 거짓이다. `<>` 는 같지 않다는 관계다. `<열> IS NULL` 은 그 행의 열 값이 NULL 이면 참이고 아니면 거짓이다. `<열> IS NOT NULL` 은 그 반대다.

[4.24] NOT 은 참을 거짓으로, 거짓을 참으로 바꾸고 알 수 없음은 그대로 둔다. AND 는 양쪽 가운데 하나라도 거짓이면 거짓이다. 거짓이 없는데 알 수 없음이 있으면 알 수 없음이고 둘 다 참이면 참이다. OR 는 양쪽 가운데 하나라도 참이면 참이다. 참이 없는데 알 수 없음이 있으면 알 수 없음이고 둘 다 거짓이면 거짓이다.

[4.25] WHERE 가 있으면 조건이 참인 행만 그룹과 집계에 쓰고 거짓이나 알 수 없음인 행은 버린다. WHERE 가 없으면 모든 행을 쓴다. 조건을 통과한 행을 이 문서는 통과한 행이라 부른다.

### 그룹과 집계

[4.26] GROUP BY 가 없으면 통과한 행 전체가 그룹 하나다. 통과한 행이 없어도 이 그룹은 있고 결과는 언제나 한 행이다.

[4.27] GROUP BY 가 있으면 통과한 행을 그룹 열의 값이 모두 같은 것끼리 묶어 그룹을 만든다. 그룹을 나눌 때는 NULL 과 NULL 을 같은 값으로 보고 NULL 과 빈 문자열은 다른 값으로 본다. 그룹은 통과한 행이 하나 이상 있는 것만 생기고 그룹 하나가 결과 행 하나가 된다. 통과한 행이 없으면 결과 행도 없다.

[4.28] 집계는 그룹에 든 행으로 계산한다. COUNT(*) 는 그룹의 행 수다. COUNT(열) 은 그룹에서 그 열 값이 NULL 이 아닌 행의 수다. 둘 다 그룹에 행이 없으면 0 이다.

[4.29] SUM(열) 은 그룹에서 NULL 이 아닌 그 열 값의 합이다. NULL 이 아닌 값이 없으면 NULL 이다.

[4.30] MIN(열) 과 MAX(열) 은 그룹에서 NULL 이 아닌 그 열 값 가운데 4.9 의 순서로 가장 앞선 값과 가장 뒤에 오는 값이다. NULL 이 아닌 값이 없으면 NULL 이다.

[4.31] AVG(열) 은 그룹에서 NULL 이 아닌 그 열 값의 합 S 를 그 값의 개수 C 로 나눈 정확한 값을 소수 둘째 자리까지 반올림한 값이다. 소수 둘째 자리까지로 나타낼 수 있는 수 가운데 S/C 에 가장 가까운 수를 고른다. 가장 가까운 수가 둘이면 0 에서 먼 쪽을 고른다. 1/8 은 0.13 이 되고 −1/8 은 −0.13 이 된다. C 가 0 이면 NULL 이다.

[4.32] 결과 행에서 열 이름 항목의 값은 그 그룹의 그룹 열 값이고 집계 항목의 값은 4.28~4.31 로 계산한 값이다. AVG 의 값은 반올림한 값 자체이고 정렬할 때도 그 값을 쓴다.

### 순서와 LIMIT

[4.33] ORDER BY 가 없으면 결과 행을 기본 순서로 놓는다. 기본 순서는 두 행의 그룹 열 값을 GROUP BY 에 쓴 열 순서대로 비교해 처음 다른 열에서 정한다. 한 열에서 값끼리는 4.9 의 순서를 따르고 NULL 은 어떤 값보다도 뒤에 온다. 그룹 열이 SELECT 목록에 없어도 기본 순서는 그 열로 정한다.

[4.34] ORDER BY 가 있으면 두 행의 키 값을 키를 쓴 순서대로 비교해 처음 순서가 갈리는 키에서 두 행의 순서를 정한다. 키의 값은 그 키의 항목과 같은 SELECT 항목의 값이다. ASC 는 4.9 의 순서대로, DESC 는 그 반대로 놓는다. 모든 키에서 같은 두 행은 기본 순서를 따른다.

[4.35] 한 키에서 값이 NULL 인 행은 NULLS FIRST 이면 NULL 이 아닌 모든 행보다 앞에 오고 NULLS LAST 이면 뒤에 온다. 둘 다 없으면 ASC 에서는 뒤에, DESC 에서는 앞에 온다. NULLS FIRST 와 NULLS LAST 는 ASC 인지 DESC 인지와 상관없이 자리를 정한다. 그 키에서 두 행이 모두 NULL 이면 그 키로는 순서가 갈리지 않는다.

[4.36] LIMIT n 이 있으면 정렬한 결과의 앞 n 행만 출력한다. 결과 행이 n 개보다 적으면 모두 출력한다. LIMIT 0 은 결과 행을 하나도 출력하지 않는다.

### 출력

[4.37] SELECT 는 먼저 머리줄 한 줄을 출력하고 그 뒤에 결과 행을 한 줄에 하나씩 출력한다. 결과 행이 없으면 머리줄만 출력한다.

[4.38] 머리줄은 SELECT 항목을 쓴 순서대로 `|` 로 이은 것이다. 열 이름 항목은 열 이름을 쓴다. 집계 항목은 함수 이름, 여는 괄호, `*` 나 열 이름, 닫는 괄호를 공백 없이 이어 `COUNT(*)`, `SUM(qty)` 처럼 쓴다.

[4.39] 결과 행은 항목의 값을 쓴 순서대로 `|` 로 이은 것이다. 정수는 10진수로 쓰고 음수에는 `-` 를 붙인다. 문자열은 작은따옴표로 감싸 `'east'`, `''` 처럼 쓴다. NULL 은 `NULL` 로 쓴다. 그래서 문자열 `'NULL'` 과 NULL 은 출력에서도 구별된다.

[4.40] AVG 값은 정수 부분, 마침표, 소수 두 자리로 쓰고 음수에는 앞에 `-` 를 붙인다. 정수 부분은 0 으로 시작하지 않되 절댓값이 1 보다 작으면 `0` 이다. 반올림한 값이 0 이면 `-` 없이 `0.00` 으로 쓴다. `2.75`, `-0.13`, `5.50`, `0.00` 처럼 쓴다.

[4.41] CREATE TABLE 과 INSERT 는 성공하면 아무것도 출력하지 않는다.

## 5. 오류 규칙

[5.1] 오류가 나면 `ERR` 로 시작하는 오류 줄 하나만 출력한다. 오류를 낸 명령은 상태를 바꾸지 않는다. 프로그램은 다음 줄을 이어서 처리한다.

[5.2] 명령어가 CREATE, INSERT, SELECT 가운데 하나이면 그 명령으로 처리한다. 명령어가 영문 대문자 A~Z 로만 이뤄졌는데 이 셋이 아니면 이 문서는 그 줄부터 입력 끝까지의 출력을 정하지 않는다. 뒤 단계는 이런 명령어로 명령을 더한다. 2단계의 COPY 와 3단계의 EXPLAIN 이 그 예다. 영문 대문자가 아닌 글자가 하나라도 든 명령어는 `ERR UNKNOWN_COMMAND` 다. `SELECT;`, `Select`, `select` 가 그런 명령어다.

[5.3] 명령어가 CREATE, INSERT, SELECT 인 줄을 토큰으로 나눌 수 없거나 토큰 열이 4절의 꼴에 맞지 않으면 `ERR SYNTAX` 다. 4.3 에 맞지 않는 정수, 이름이어야 할 자리에 온 이름 아닌 낱말, 4.11 과 4.16 의 같은 열 두 번도 문법 오류다.

[5.4] 토큰으로 나눌 수 있는 줄 가운데 아래 다섯 경우의 줄은 뒤 단계에서 뜻이 생기므로 이 문서는 그 줄부터 입력 끝까지의 출력을 정하지 않는다. 이 규칙은 5.3 과 4절의 다른 모든 오류보다 앞선다. 첫째, 명령어가 CREATE 이고 둘째 토큰이 있는데 TABLE 이 아닌 줄이다. 둘째, CREATE TABLE 의 열 정의 목록이 꼴에 맞게 닫힌 뒤에 토큰이 더 있는 줄이다. 셋째, SELECT 목록이나 ORDER BY 의 키에서 항목이 올 자리에 낱말과 `(` 가 차례로 오는데 그 낱말이 COUNT, SUM, AVG, MIN, MAX 가 아닌 줄이다. 넷째, COUNT, `(`, DISTINCT 세 토큰이 차례로 나오는 줄이다. 다섯째, GROUP BY 뒤부터 ORDER BY 나 LIMIT 또는 줄 끝 앞까지 사이에 `(` 가 있는 줄이다. 둘째 경우는 열 정의 목록을 닫는 괄호까지, 셋째 경우는 그 낱말 바로 앞까지의 토큰에 5.3 의 문법 오류가 없을 때만 해당한다. 같은 열 이름을 두 번 쓴 `CREATE TABLE t (a INT, a INT) x` 는 둘째 경우에 들지 않으므로 `ERR SYNTAX` 다. `SELECT a b(c) FROM t` 에서 b 가 놓인 곳은 쉼표나 FROM 이 올 자리이므로 셋째 경우에 들지 않는다. 이 줄도 `ERR SYNTAX` 다.

[5.5] 명령은 명령어를 먼저 확인하고(5.2), 다음으로 토큰과 꼴을 확인한 뒤(5.3, 5.4), 4절에 명령마다 적은 순서대로 확인한다(4.11, 4.13, 4.19). 처음 걸린 오류 하나만 출력한다.

[5.6] 이 단계의 오류 줄은 아래와 같다.

| 오류 줄 | 뜻 |
|---|---|
| `ERR UNKNOWN_COMMAND` | 모르는 명령어 |
| `ERR SYNTAX` | 토큰으로 나눌 수 없거나 꼴이 틀림 |
| `ERR EXISTS` | 같은 이름의 테이블이 이미 있음 |
| `ERR NO_TABLE` | 테이블이 없음 |
| `ERR VALUE_COUNT` | INSERT 한 행의 값 개수가 열 수와 다름 |
| `ERR TYPE` | 값이나 열의 자료형이 맞지 않음 |
| `ERR NO_COLUMN <열>` | 테이블에 없는 열 |
| `ERR NOT_GROUPED <열>` | 그룹 열이 아닌 열 이름 항목 |
| `ERR NOT_SELECTED` | SELECT 목록에 없는 ORDER BY 항목 |

## 6. 예시

[6.1] 예시 블록 하나는 프로그램을 한 번 실행한 기록이다. `> ` 로 시작하는 줄은 입력이고 나머지 줄은 바로 위 입력의 출력이다. 실제 입력에는 `> ` 가 없다. 6.3 과 6.4 는 6.2 의 CREATE TABLE 과 두 INSERT 를 먼저 실행한 것으로 보고 그 세 줄을 생략한다.

[6.2] 기본 동작이다. 첫 SELECT 에서 COUNT(*) 는 여섯 행을 모두 세지만 COUNT(qty) 는 qty 가 NULL 인 두 행을 뺀다. AVG(qty) 는 11/4 이다. MIN(item) 은 빈 문자열이다. 둘째 SELECT 에서 region 이 NULL 인 행은 따로 한 그룹이 되고 그 그룹의 price 는 모두 NULL 이라 AVG(price) 가 NULL 이다. east 의 AVG(price) 는 250/3 이다. 셋째 SELECT 처럼 그룹 열을 SELECT 목록에 쓰지 않아도 결과 행은 그 열의 기본 순서로 놓인다. 넷째 SELECT 에서 빈 문자열 item 은 가장 앞선 그룹이고 MIN(region) 과 MAX(region) 은 region 의 NULL 을 건너뛴다.

```
> CREATE TABLE sales (region STRING, item STRING, qty INT, price INT)
> INSERT INTO sales VALUES ('east', 'pen', 3, 120), ('east', 'ink', NULL, 40), ('west', 'pen', 5, 100)
> INSERT INTO sales VALUES (NULL, 'pen', 2, NULL), ('west', '', 1, 300), ('east', 'pen', NULL, 90)
> SELECT COUNT(*), COUNT(qty), SUM(qty), AVG(qty), MIN(item), MAX(price) FROM sales
COUNT(*)|COUNT(qty)|SUM(qty)|AVG(qty)|MIN(item)|MAX(price)
6|4|11|2.75|''|300
> SELECT region, COUNT(*), SUM(qty), AVG(price) FROM sales GROUP BY region
region|COUNT(*)|SUM(qty)|AVG(price)
'east'|3|3|83.33
'west'|2|6|200.00
NULL|1|2|NULL
> SELECT COUNT(*) FROM sales GROUP BY region
COUNT(*)
3
2
1
> SELECT item, MIN(region), MAX(region) FROM sales GROUP BY item
item|MIN(region)|MAX(region)
''|'west'|'west'
'ink'|'east'|'east'
'pen'|'east'|'west'
```

[6.3] WHERE 의 3값 논리다. qty 가 NULL 인 두 행은 `qty > 2` 에서도 `NOT qty > 2` 에서도 알 수 없음이라 둘을 OR 로 이어도 남지 않는다. `qty = NULL` 은 모든 행에서 알 수 없음이다. 여섯째 SELECT 에서 region 이 NULL 인 행은 `region = 'east'` 가 알 수 없음이지만 `qty IS NULL` 이 거짓이라 AND 가 거짓이 되고 NOT 으로 참이 된다. 마지막 SELECT 에서 east 의 qty 가 NULL 인 두 행은 OR 의 양쪽이 거짓과 알 수 없음이라 버려진다.

```
> SELECT COUNT(*) FROM sales WHERE qty > 2
COUNT(*)
2
> SELECT COUNT(*) FROM sales WHERE NOT qty > 2
COUNT(*)
2
> SELECT COUNT(*) FROM sales WHERE qty > 2 OR NOT qty > 2
COUNT(*)
4
> SELECT COUNT(*) FROM sales WHERE qty = NULL
COUNT(*)
0
> SELECT COUNT(*) FROM sales WHERE qty IS NULL OR region <> 'east'
COUNT(*)
4
> SELECT COUNT(*), COUNT(region) FROM sales WHERE NOT (region = 'east' AND qty IS NULL)
COUNT(*)|COUNT(region)
4|3
> SELECT item, COUNT(*) FROM sales WHERE region <> 'east' OR qty > 1 GROUP BY item
item|COUNT(*)
''|1
'pen'|3
```

[6.4] 정렬이다. DESC 의 기본값은 NULL 을 앞에 두고 NULLS LAST 는 DESC 에서도 NULL 을 뒤에 둔다. 셋째 SELECT 에서 COUNT(*) 가 같은 두 행은 기본 순서, 곧 item 의 순서를 따른다. 넷째 SELECT 에서 COUNT(*) 가 1 인 두 그룹은 SUM(qty) 로 갈린다. ink 의 SUM(qty) 는 NULL 이라 ASC 에서 뒤에 오고 LIMIT 2 에 들지 못한다. 마지막 SELECT 의 기본 순서는 SELECT 목록이 아니라 GROUP BY 에 쓴 열 순서를 따른다.

```
> SELECT region, SUM(qty) FROM sales GROUP BY region ORDER BY region DESC
region|SUM(qty)
NULL|2
'west'|6
'east'|3
> SELECT region, SUM(qty) FROM sales GROUP BY region ORDER BY region DESC NULLS LAST
region|SUM(qty)
'west'|6
'east'|3
NULL|2
> SELECT item, COUNT(*) FROM sales GROUP BY item ORDER BY COUNT(*)
item|COUNT(*)
''|1
'ink'|1
'pen'|4
> SELECT item, COUNT(*), SUM(qty) FROM sales GROUP BY item ORDER BY COUNT(*) DESC, SUM(qty) LIMIT 2
item|COUNT(*)|SUM(qty)
'pen'|4|10
''|1|1
> SELECT region, item, COUNT(*) FROM sales GROUP BY item, region
region|item|COUNT(*)
'west'|''|1
'east'|'ink'|1
'east'|'pen'|2
'west'|'pen'|1
NULL|'pen'|1
```

[6.5] 오류다. 같은 INSERT 의 둘째 행만 값이 모자라도 첫 행까지 넣지 않으므로 그 뒤의 COUNT(*) 는 0 이다. `'it''s'` 는 문자열 `'it'` 과 `'s'` 가 이어진 것이라 문법 오류다. `city, SUM(region)` 에서는 자료형보다 열 이름을 먼저 확인하므로 TYPE 이 아니라 NO_COLUMN 이 나온다. 이 블록은 6.2 의 세 줄을 실행하지 않은 새 실행이다.

```
> CREATE TABLE sales (region STRING, qty INT)
> CREATE TABLE sales (x INT)
ERR EXISTS
> CREATE TABLE t2 (a INT, a STRING)
ERR SYNTAX
> INSERT INTO nope VALUES (1)
ERR NO_TABLE
> INSERT INTO sales VALUES ('east', 1), ('west')
ERR VALUE_COUNT
> INSERT INTO sales VALUES ('east', 'one')
ERR TYPE
> INSERT INTO sales VALUES ('east', 007)
ERR SYNTAX
> INSERT INTO sales VALUES ('it''s', 1)
ERR SYNTAX
> SELECT COUNT(*) FROM sales
COUNT(*)
0
> SELECT region, SUM(qty) FROM sales
ERR NOT_GROUPED region
> SELECT SUM(region) FROM sales
ERR TYPE
> SELECT COUNT(*) FROM sales WHERE qty = '1'
ERR TYPE
> SELECT city, SUM(region) FROM sales WHERE zip > 1
ERR NO_COLUMN city
> SELECT region FROM sales GROUP BY region ORDER BY COUNT(*)
ERR NOT_SELECTED
> SELECT COUNT(*) FROM sales GROUP BY region, region
ERR SYNTAX
> SELECT COUNT(*) FROM sales WHERE 1 < qty
ERR SYNTAX
> SELECT COUNT(*) FROM Sales
ERR SYNTAX
> select COUNT(*) FROM sales
ERR UNKNOWN_COMMAND
```

[6.6] AVG 의 반올림과 빈 결과다. a 는 1/8 = 0.125 라 0 에서 먼 0.13 이 되고 b 는 −0.125 라 −0.13 이 된다. c 는 −2/3 이다. e 는 NULL 이 아닌 값이 없어 SUM 과 AVG 가 NULL 이다. GROUP BY 가 없는 SELECT 는 통과한 행이 없어도 한 행을 내지만 GROUP BY 가 있는 SELECT 는 머리줄만 낸다. 이 블록도 새 실행이다.

```
> CREATE TABLE r (g STRING, v INT)
> INSERT INTO r VALUES ('a', 1), ('a', 0), ('a', 0), ('a', 0), ('a', 0), ('a', 0), ('a', 0), ('a', 0)
> INSERT INTO r VALUES ('b', -1), ('b', 0), ('b', 0), ('b', 0), ('b', 0), ('b', 0), ('b', 0), ('b', 0)
> INSERT INTO r VALUES ('c', -2), ('c', 0), ('c', 0), ('d', 5), ('d', 6), ('e', NULL)
> SELECT g, COUNT(v), SUM(v), AVG(v) FROM r GROUP BY g
g|COUNT(v)|SUM(v)|AVG(v)
'a'|8|1|0.13
'b'|8|-1|-0.13
'c'|3|-2|-0.67
'd'|2|11|5.50
'e'|0|NULL|NULL
> SELECT g, AVG(v) FROM r GROUP BY g ORDER BY AVG(v) DESC
g|AVG(v)
'e'|NULL
'd'|5.50
'a'|0.13
'b'|-0.13
'c'|-0.67
> SELECT COUNT(*), COUNT(v), SUM(v), AVG(v), MIN(g) FROM r WHERE v > 100
COUNT(*)|COUNT(v)|SUM(v)|AVG(v)|MIN(g)
0|0|NULL|NULL|NULL
> SELECT g, COUNT(*) FROM r WHERE v > 100 GROUP BY g
g|COUNT(*)
> SELECT g, COUNT(*) FROM r GROUP BY g LIMIT 0
g|COUNT(*)
```

## 7. 검사 방식

[7.1] 엔진은 테스트마다 풀이의 `run.sh` 를 새 프로세스로 실행한다. 작업 디렉터리는 풀이 디렉터리다.

[7.2] 엔진은 테스트의 입력 줄을 LF 로 잇고 마지막 줄 뒤에도 LF 를 붙여 표준 입력에 쓴 뒤 표준 입력을 닫는다.

[7.3] 프로세스가 0 이 아닌 종료 코드로 끝나면 실패다.

[7.4] 표준 출력만 기대 출력과 비교하고 표준 오류는 비교하지 않는다. 각 줄 끝의 공백, 탭, CR 을 지우고 끝에 붙은 빈 줄을 지운 뒤 줄 수와 각 줄의 내용이 정확히 같아야 한다.

[7.5] 일반 테스트의 제한 시간은 10초다. 제한 시간을 넘기면 엔진이 프로세스를 끝내고 실패로 판정한다.

[7.6] 대규모 테스트는 테이블 두 개에 INSERT 로 행 50,000개 안팎을 넣고 SELECT 200개 안팎을 실행한다. INSERT 한 줄에는 행이 1,000개까지 들어 있다. STRING 열 하나는 서로 다른 값이 20,000개 안팎이고 그 열로 묶는 GROUP BY 가 질의에 섞인다. 제한 시간은 10초다. 행마다 지금까지 만든 그룹을 처음부터 훑어 제 그룹을 찾는 풀이는 이 제한 시간을 넘기도록 규모를 정했다.

[7.7] 검사 입력의 한 줄은 UTF-8 로 65,536바이트 이하다. 테이블 하나의 열은 16개 이하이고 행은 10,000,000개 이하다. 그래서 SUM 의 결과는 64비트 부호 있는 정수에 담긴다.

[7.8] 1단계 검사 입력에는 5.2 와 5.4 가 출력을 정하지 않은 줄이 나오지 않는다.

[7.9] 2단계와 3단계를 검사할 때도 1단계 테스트를 다시 실행한다.

[7.10] 3.3 의 마지막 문장과 3.7 의 LF 규칙은 엔진이 검사하지 않는다. 엔진은 입력의 마지막 줄 뒤에도 LF 를 붙이고(7.2) 출력 줄 끝의 CR 과 끝에 붙은 빈 줄을 지운 뒤 비교하기(7.4) 때문이다. 두 규칙은 표준 입출력 프로그램의 관례라서 적어 둔다.
