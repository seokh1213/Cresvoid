# 3단계: 구문 검색과 근접 검색

## 1. 개요

[1.1] 3단계는 QUERY 에 구문 검색과 근접 검색을 더한다. 구문 `"new york"` 은 new 바로 뒤에 york 이 오는 문서를 찾는다. 근접 연산자 `NEAR/k` 는 두 토큰의 위치 차이가 k 이하인 문서를 찾는다.

[1.2] 1단계와 2단계의 규칙은 그대로다. SEARCH 는 바뀌지 않는다. 이 문서는 QUERY 에 새로 생기는 규칙만 설명한다. 이 규칙은 식에 큰따옴표가 있거나 `NEAR/` 로 시작하는 낱말이 있는 QUERY 의 출력만 정한다. 이런 QUERY 는 1단계 4.33 이 출력을 정하지 않은 입력이므로 앞 단계가 정한 출력은 바뀌지 않는다.

## 2. 배경 개념

[2.1] 1단계의 역색인은 토큰이 어느 문서에 있는지만 기록하므로 `new york` 과 `york new` 를 구별하지 못한다. 낱말의 순서와 거리를 따지려면 문서 안의 위치를 알아야 한다.

[2.2] 위치는 문서의 토큰 목록에서 토큰이 놓인 번호다. 첫 토큰이 0 이다. 글자 번호가 아니라 토큰 번호이므로 구분자와 대소문자는 위치에 영향을 주지 않는다. `New-York` 과 `new york` 은 똑같이 new 가 위치 0, york 이 위치 1 이다.

[2.3] 위치 색인은 포스팅 목록의 문서마다 그 토큰이 나온 위치의 목록을 함께 적어 둔다. 아래는 문서 두 개로 만든 위치 색인이다.

```
문서 1: new york city
문서 4: the new new york

city → 1: [2]
new  → 1: [0], 4: [1, 2]
the  → 4: [0]
york → 1: [1], 4: [3]
```

[2.4] 위치 색인은 토큰이 나올 때마다 위치를 하나씩 저장하므로 위치가 없는 역색인보다 크다. 실제 엔진은 위치를 바로 앞 위치와의 차이로 바꿔 저장 공간을 줄인다.

[2.5] 구문 검색은 토큰 t1, …, tm 이 이어진 위치 p, p+1, …, p+m−1 에 차례로 나오는지 확인한다. 2.3 의 예에서 구문 `new york` 은 문서 1 에서는 p = 0 에서, 문서 4 에서는 p = 2 에서 맞는다. 문서 4 의 위치 1 에 있는 new 는 다음 위치 2 에 york 이 아니라 new 가 있으므로 출발점이 되지 못한다. 구문은 모든 토큰을 포함하기만 하면 되는 1단계의 여러 토큰 낱말보다 조건이 강하다.

[2.6] 근접 검색은 두 토큰의 위치 차이가 k 이하인지 확인한다. 순서는 따지지 않는다. 위치 차이가 1 이면 두 토큰이 바로 붙어 있다. 위치 차이가 k 이하이면 두 토큰 사이에 다른 토큰이 k − 1 개까지 있을 수 있다. 2.3 의 예에서 문서 4 의 the 와 york 은 위치 차이가 3 이다.

[2.7] 근접 거리를 세는 방식은 시스템마다 다르다. Lucene 의 구문 slop 은 질의의 토큰 순서에 맞추려면 토큰을 옮기는 칸 수의 합으로 센다. 이 트랙의 NEAR 는 위치 차이의 절댓값만 쓴다.

[2.8] 이 트랙은 조사를 떼어 내지 않으므로 한국어 문서에서는 조사도 토큰이 되어 위치를 차지한다. `Kotlin은 JVM 언어다` 의 토큰은 kotlin(0), 은(1), jvm(2), 언어다(3) 이다. 그래서 구문 `kotlin jvm` 은 맞지 않고 `kotlin NEAR/2 jvm` 은 맞는다.

## 3. 입출력 규칙

[3.1] 1단계와 2단계의 입출력 규칙을 그대로 따른다.

## 4. 명령 규칙

### 어휘

[4.1] QUERY 식의 어휘에 구문과 NEAR 연산자가 더해진다. 이 절의 규칙이 1단계의 어휘 나누기 규칙을 대신한다. 식에 큰따옴표도 `NEAR/` 로 시작하는 낱말도 없으면 이 절의 규칙으로 나눈 어휘는 1단계의 어휘와 같고 QUERY 의 출력도 1단계와 같다.

[4.2] 식은 왼쪽부터 어휘로 나눈다. 큰따옴표 밖의 공백은 어휘를 나눌 뿐 어휘가 아니다. 큰따옴표 밖의 괄호 `(` 와 `)` 는 앞뒤에 다른 문자가 붙어 있어도 한 글자짜리 독립 어휘다.

[4.3] 큰따옴표(`"`)는 구문을 연다. 구문은 다음 큰따옴표에서 닫힌다. 두 큰따옴표 사이의 문자열이 구문의 내용이다. 구문 하나가 어휘 하나다.

[4.4] 공백도 괄호도 큰따옴표도 아닌 문자가 최대로 이어진 구간은 낱말이다. 큰따옴표는 낱말을 끊는다. `a"b c"d` 는 낱말 `a`, 구문 `"b c"`, 낱말 `d` 세 어휘다.

[4.5] 구문 안의 괄호, AND·OR·NOT, `NEAR/k` 는 특별한 뜻이 없다. 구문의 내용은 공백과 괄호를 포함해 모두 토큰화한다.

[4.6] `NEAR/` 로 시작하는 낱말은 NEAR 연산자다. 대소문자를 구분하므로 `near/3` 과 `Near/3` 은 검색어 낱말이다.

[4.7] NEAR 연산자 낱말 전체는 `^NEAR/[1-9][0-9]?$` 에 맞아야 하고 `/` 뒤의 수 k 는 50 이하여야 한다.

[4.8] 정확히 AND, OR, NOT 인 낱말이 연산자라는 규칙과, 나머지 낱말을 검색어 낱말로 토큰화하는 규칙은 1단계와 같다.

### 구문

[4.9] 구문의 내용을 토큰화한 결과를 t1, …, tm 이라 하자. 문서의 위치 p, p+1, …, p+m−1 에 t1, …, tm 이 차례로 나오는 p 가 하나라도 있으면 그 문서는 구문에 맞는다.

[4.10] 위치는 문서 토큰 목록의 번호다. 첫 토큰이 0 이고 하나씩 늘어난다.

[4.11] m 이 0 인 구문은 파싱 전에 지운다. 0토큰 검색어 낱말을 지우는 것과 같다. `""` 와 `"!!!"` 는 지워진다.

[4.12] m 이 1 인 구문은 그 토큰 하나로 된 항과 같다. `"kotlin"` 은 `kotlin` 과 같다.

[4.13] 큰따옴표 밖의 여러 토큰 낱말은 1단계처럼 토큰을 모두 포함한 문서에 맞는다. 위치는 따지지 않는다. `new-york` 은 `(new AND york)` 이고 `"new york"` 과 다르다.

### NEAR

[4.14] `NEAR/k` 는 이항 연산자다. `x NEAR/k y` 는 x 가 나온 위치 i 와 y 가 나온 위치 j 가운데 i ≠ j 이고 |i − j| ≤ k 인 쌍이 하나라도 있는 문서에 맞는다.

[4.15] 순서는 따지지 않는다. `a NEAR/1 b` 는 `a b` 에도 `b a` 에도 맞는다.

[4.16] x 와 y 가 같은 토큰이면 서로 다른 두 출현이 필요하다. `a NEAR/5 a` 는 a 가 한 번만 나오는 문서에 맞지 않는다.

[4.17] NEAR 의 두 피연산자는 각각 토큰이 정확히 1개인 검색어 낱말이거나 토큰이 정확히 1개인 구문이어야 한다. 이런 낱말과 구문을 근접항이라 한다. 토큰 수는 토큰화한 목록의 길이이므로 중복을 포함해 센다. `a-a` 와 `"a a"` 는 토큰이 2개라 근접항이 아니다.

### 문법

[4.18] 0토큰 낱말과 0토큰 구문을 지운 어휘의 열은 아래 문법의 or_expr 하나와 정확히 맞아야 한다. NEAR 는 NEAR 연산자 어휘다. 항은 토큰이 1개 이상인 검색어 낱말이거나 토큰이 1개인 구문이다. 근접항은 4.17 대로 토큰이 정확히 1개인 검색어 낱말이거나 구문이다. 구문항은 토큰이 2개 이상인 구문이다. 나머지 표기는 1단계와 같다. 1단계 문법과 비교하면 not_expr 의 정의가 바뀌고 near_expr 가 새로 생기며 primary 에 구문항이 더해진다.

```
or_expr   := and_expr ( OR and_expr )*
and_expr  := not_expr ( [ AND ] not_expr )*
not_expr  := NOT not_expr | near_expr
near_expr := 근접항 NEAR 근접항 | primary
primary   := '(' or_expr ')' | 항 | 구문항
```

[4.19] NEAR 는 NOT 보다 강하게 묶는다. 우선순위는 NEAR, NOT, AND, OR 순이다. `NOT a NEAR/2 b` 는 `NOT (a NEAR/2 b)` 다.

[4.20] near_expr 하나에는 NEAR 연산자가 많아야 하나 온다. `a NEAR/2 b NEAR/3 c` 처럼 이어 쓸 수 없다.

[4.21] 나란히 놓인 NEAR 식도 1단계 규칙대로 AND 로 묶인다. `a b NEAR/1 c` 는 `a AND (b NEAR/1 c)` 다.

[4.22] 0토큰 낱말과 0토큰 구문은 문법을 맞춰 보기 전에 지운다. `a !!! NEAR/1 b` 는 `a NEAR/1 b` 와 같다.

### SEARCH

[4.23] SEARCH 는 바뀌지 않는다. SEARCH 에서 큰따옴표는 토큰화의 구분자일 뿐이므로 `SEARCH 10 "new york"` 은 선택 토큰 new 와 york 으로 검색한다. `NEAR/3` 같은 낱말도 SEARCH 에서는 토큰 near 와 3 이 된다.

## 5. 오류 규칙

[5.1] 닫히지 않은 큰따옴표가 있으면 `ERR SYNTAX` 를 출력한다.

[5.2] `NEAR/` 로 시작하는 낱말이 4.7 의 형식에 맞지 않으면 `ERR SYNTAX` 를 출력한다. `NEAR/`, `NEAR/0`, `NEAR/05`, `NEAR/51`, `NEAR/3a` 가 여기에 해당한다.

[5.3] NEAR 의 피연산자 자리에 4.17 의 근접항이 아닌 것이 오면 `ERR SYNTAX` 를 출력한다. 토큰이 2개 이상인 낱말이나 구문, 괄호식, NOT 식이 여기에 해당한다. 괄호 안이 토큰 1개짜리 항이어도 `(a)` 는 괄호식이므로 피연산자가 될 수 없다.

[5.4] NEAR 의 피연산자가 빠졌거나 NEAR 식 바로 뒤에 NEAR 연산자가 다시 오면 `ERR SYNTAX` 를 출력한다.

[5.5] 그 밖에 4.18 의 문법에 맞지 않는 식은 1단계와 같이 `ERR SYNTAX` 다.

## 6. 예시

[6.1] 예시의 표기는 1단계와 같다. 예시 블록 하나는 프로그램을 한 번 실행한 기록이다.

[6.2] 구문이다. 문서 2 와 3 은 new 와 york 을 모두 포함하지만 new 바로 뒤에 york 이 오지 않는다. 문서 2 는 york 이 new 바로 앞에 있고 문서 3 은 둘 사이에 다른 토큰이 있다. 구문 안의 `AND` 는 토큰 and 다. `new"york city"` 는 낱말 new 와 구문 `"york city"` 를 AND 로 묶은 식이다.

```
> ADD 1 new york city
> ADD 2 york new
> ADD 3 new jersey and york
> ADD 4 the new new york
> QUERY "new york"
1 4
> QUERY new-york
1 2 3 4
> QUERY "york new"
2
> QUERY NOT "new york"
2 3
> QUERY "jersey AND york"
3
> QUERY new"york city"
1
> QUERY "" city
1
```

[6.3] NEAR 다. 문서 3 의 new 와 york 은 위치 차이가 3 이다. `new NEAR/1 new` 는 new 가 두 번 붙어 나오는 문서 4 에만 맞는다. 소문자 `near/1` 은 토큰 near 와 1 을 모두 요구하는 검색어 낱말이다. SEARCH 는 큰따옴표를 구분자로 다루므로 구문 `new york` 이 나오지 않는 문서 2 와 3 도 찾는다.

```
> ADD 1 new york city
> ADD 2 york new
> ADD 3 new jersey and york
> ADD 4 the new new york
> QUERY new NEAR/1 york
1 2 4
> QUERY new NEAR/3 york
1 2 3 4
> QUERY new NEAR/1 new
4
> QUERY "york" NEAR/1 city
1
> QUERY NOT new NEAR/1 york
3
> QUERY city new NEAR/1 york
1
> QUERY new !!! NEAR/1 york
1 2 4
> QUERY new near/1 york
(none)
> SEARCH 10 "new york"
2 0.2501
4 0.2323
1 0.2176
3 0.1925
```

[6.4] 한국어 조사와 위치다. `kotlin은` 은 토큰이 2개라 NEAR 의 피연산자가 될 수 없다.

```
> ADD 1 Kotlin은 JVM 언어다
> QUERY "kotlin jvm"
(none)
> QUERY "kotlin은 jvm"
1
> QUERY kotlin NEAR/2 jvm
1
> QUERY kotlin NEAR/1 jvm
(none)
> QUERY kotlin은 NEAR/1 jvm
ERR SYNTAX
```

[6.5] 문법 오류다.

```
> ADD 1 new york city
> QUERY "new york" NEAR/2 city
ERR SYNTAX
> QUERY (new) NEAR/1 york
ERR SYNTAX
> QUERY new NEAR/2 york NEAR/3 city
ERR SYNTAX
> QUERY new NEAR/1 NOT york
ERR SYNTAX
> QUERY new NEAR/51 york
ERR SYNTAX
> QUERY "new york
ERR SYNTAX
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계와 같다.

[7.2] 3단계 테스트도 2단계처럼 출력을 글자 그대로 비교한다.

[7.3] 대규모 테스트는 문서 20,000개와 구문·NEAR 를 포함한 QUERY 6,000개로 이뤄진다. 제한 시간은 15초다. 색인 없이 QUERY 마다 모든 문서의 토큰 목록을 훑는 풀이는 이 제한 시간을 넘기도록 규모를 정했다.

[7.4] 3단계를 검사할 때 1단계와 2단계 테스트도 다시 실행한다. 앞 단계 테스트의 QUERY 에는 큰따옴표도 `NEAR/` 로 시작하는 낱말도 없으므로 3단계 규칙과 부딪히지 않는다.
