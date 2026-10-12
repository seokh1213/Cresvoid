# 3단계: 페이지 넘기기와 PIT

## 1. 개요

[1.1] 3단계는 검색 결과를 여러 페이지로 나눠 읽는 방법을 더한다. SEARCH 는 정렬 기준 sort, 앞에서 건너뛸 결과 수 from, 앞 페이지의 마지막 정렬 값을 넘기는 search_after, 색인 상태를 고정한 PIT 를 고르는 pit 를 매개변수로 더 받는다. PIT 를 여는 OPEN_PIT 명령과 닫는 CLOSE_PIT 명령도 더한다.

[1.2] 1단계와 2단계의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 4.20 이 출력을 정하지 않은 SEARCH 와 1단계 5.2 가 출력을 정하지 않은 줄 가운데 명령어가 OPEN_PIT, CLOSE_PIT 인 줄은 이 문서가 출력을 정한다. from, sort, search_after, pit 를 쓰지 않는 SEARCH 의 출력은 1단계와 같다.

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `OPEN_PIT` | 지금의 색인 상태를 고정한 PIT 를 연다 | 1줄 |
| `CLOSE_PIT <PIT id>` | PIT 를 닫는다 | 없음 |
| `SEARCH <매개변수> …` | 매개변수 from, sort, search_after, pit 를 더 받는다 | 1줄 이상 |

## 2. 배경 개념

[2.1] 검색 결과가 많으면 한 번에 다 보여 주지 않고 페이지로 나눈다. 가장 단순한 방법은 앞에서 건너뛸 결과 수 from 과 한 페이지의 결과 수 size 를 주는 것이다. Elasticsearch 에서 from 의 기본값은 0 이고 size 의 기본값은 10 이다.

[2.2] from 과 size 로 페이지를 넘기면 샤드마다 자기 결과를 앞에서부터 from + size 개씩 조정 노드에 보내야 한다. 앞의 from 개를 건너뛴 다음 size 개 안에 어느 샤드의 결과가 몇 개 들어갈지 미리 알 수 없기 때문이다. 조정 노드는 샤드 수 × (from + size) 개까지의 결과를 합쳐 정렬한 뒤 앞의 from 개를 버린다. 페이지가 깊어질수록 샤드가 보내고 조정 노드가 합쳐야 하는 결과가 늘어난다. Elastic 문서는 from 과 size 로 너무 깊이 넘기면 메모리와 CPU 사용량이 크게 늘어 성능이 떨어지거나 노드에 장애가 날 수 있다고 경고한다.

[2.3] 그래서 Elasticsearch 는 from + size 가 index.max_result_window 를 넘는 요청을 거절한다. 이 설정의 기본값은 10,000 이다.

[2.4] search_after 는 앞 페이지의 마지막 결과가 가진 정렬 값을 다음 요청에 넘겨 정렬 순서에서 그 값 뒤에 오는 결과부터 받는 방법이다. 샤드는 그 값 뒤의 결과 가운데 앞에서부터 size 개만 보내면 되므로 페이지가 깊어져도 샤드가 보내는 결과 수가 늘지 않는다. 다음 페이지를 받을 때는 질의와 정렬을 그대로 두고 search_after 값만 바꿔 다시 요청한다. Elasticsearch 는 search_after 와 0 이 아닌 from 을 함께 쓴 요청을 거절한다.

[2.5] search_after 는 넘긴 값과 정렬 값이 똑같은 결과를 그 값 뒤에 오는 결과로 보지 않는다. 그래서 정렬 값이 같은 결과 여럿이 페이지 경계에 걸치면 경계 뒤쪽의 결과가 다음 페이지에서 빠진다. Elastic 문서는 PIT(2.7)를 쓸 수 없으면 문서마다 값이 다른 동점 해소 필드를 정렬에 넣으라고 권한다. 그런 필드가 없으면 결과가 빠지거나 겹칠 수 있다.

[2.6] 페이지를 넘기는 사이에도 문서는 색인되고 지워진다. Elasticsearch 에서는 그 사이에 refresh 가 일어나면 다음 요청이 바뀐 색인을 읽는다. from 과 size 로 넘길 때 이미 지나온 자리에 새 문서가 들어오면 결과가 한 칸씩 밀려 앞 페이지의 마지막 결과가 다음 페이지에 다시 나온다. 지나온 자리의 문서가 지워지면 결과가 한 칸씩 당겨져 결과 하나를 건너뛴다. search_after 는 넘긴 값 뒤만 읽으므로 지나온 자리에 새로 들어온 문서를 보지 못하고 정렬 값이 바뀐 문서는 두 번 나오거나 빠진다. 점수로 정렬할 때는 문서 하나가 바뀌어도 샤드 통계가 바뀌어 다른 문서의 점수까지 움직인다.

[2.7] PIT(point in time)는 한 시점의 색인 상태를 고정해 두고 여러 검색이 같은 상태를 읽게 하는 장치다. PIT 를 연 뒤에 색인하거나 지운 문서는 그 PIT 로 하는 검색에 반영되지 않는다. Elasticsearch 는 PIT 를 열 때 id 를 돌려주고 검색 요청은 그 id 를 함께 보낸다. 다 쓴 PIT 는 닫아서 PIT 가 차지하던 자원을 돌려준다. Elastic 문서는 10,000개가 넘는 결과를 색인 상태를 고정한 채 넘기려면 search_after 와 PIT 를 함께 쓰라고 권한다.

[2.8] PIT 를 쓰는 검색에는 정렬의 마지막 기준으로 _shard_doc 이 자동으로 붙는다. _shard_doc 의 값은 PIT 안에서의 샤드 순번과 Lucene 내부 문서 번호를 묶은 것이라 문서마다 다르고 PIT 안에서는 바뀌지 않는다. Elasticsearch 는 샤드 순번에 2^32 를 곱한 값에 내부 번호를 더해 64비트 정수 하나로 만든다. 샤드 1 에서 내부 번호가 2 인 문서의 _shard_doc 은 4294967298 이다. PIT 가 없는 검색도 정렬 값이 같으면 샤드 번호와 내부 번호로 순서를 정한다. 하지만 이 두 값은 결과의 정렬 값에 들어가지 않으므로 search_after 로 넘길 수 없다.

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 따른다.

[3.2] 정렬 값 가운데 _score 는 1단계 3.11 의 형식으로 쓴다. price, id, _shard_doc 은 10진 정수로 쓴다. 0 은 `0` 으로 쓰고 다른 수는 0 으로 시작하지 않는다.

## 4. 명령 규칙

### OPEN_PIT 과 CLOSE_PIT

[4.1] `OPEN_PIT` 은 지금의 색인 상태를 고정한 PIT 를 열고 `pit <PIT id>` 한 줄을 출력한다.

[4.2] PIT id 는 1 부터 시작하고 OPEN_PIT 이 성공할 때마다 1 씩 커진다. 닫은 PIT 의 id 는 다시 쓰지 않는다.

[4.3] PIT 는 열 때 각 샤드에 있던 문서를 모두 고정한다. 문서마다 id, 샤드 번호, 내부 번호, 브랜드, 가격, 제목이 고정된다. PIT 를 연 뒤에 실행한 PUT 과 DEL 은 그 PIT 에 반영되지 않는다.

[4.4] `CLOSE_PIT <PIT id>` 는 열려 있는 PIT 를 닫는다. 성공하면 아무것도 출력하지 않는다. PIT id 인자는 `^[1-9][0-9]{0,8}$` 에 맞는 10진 정수다.

[4.5] PIT 는 CLOSE_PIT 으로 닫기 전까지 열려 있다. 한꺼번에 열 수 있는 PIT 의 수에는 한도가 없다.

### SEARCH 매개변수

[4.6] 3단계의 SEARCH 매개변수 이름은 q, size, search_type, from, sort, search_after, pit 일곱 가지다. 매개변수의 모양과 같은 이름을 두 번 쓸 수 없다는 규칙은 1단계 4.18, 4.19 와 같다.

[4.7] from 은 정렬한 결과에서 앞에서부터 건너뛸 결과 수다. 값은 `^(0|[1-9][0-9]{0,8})$` 에 맞는 10진 정수다. from 이 없으면 0 이다.

[4.8] sort 의 값은 정렬 키 하나 이상을 쉼표(,)로 이은 것이다. 정렬 키는 `<필드>` 나 `<필드>:<방향>` 이다. 필드는 `_score`, `price`, `id`, `_shard_doc` 가운데 하나이고 방향은 `asc` 나 `desc` 다. 방향을 생략하면 _score 는 desc 이고 나머지 필드는 asc 다. 한 sort 에 같은 필드를 두 번 쓸 수 없다.

[4.9] sort 가 없으면 정렬 키는 `_score:desc` 하나다.

[4.10] pit 의 값은 PIT id 이고 `^[1-9][0-9]{0,8}$` 에 맞는 10진 정수다. pit 가 있으면 검색은 그 PIT 가 고정한 문서만으로 한다. 결과, 점수에 쓰는 N, df, avgdl, 정렬 값, 결과 수, 합친 결과 수를 모두 PIT 의 문서로 구한다.

[4.11] _shard_doc 은 pit 가 있을 때만 정렬 키로 쓸 수 있다. pit 가 있고 정렬 키에 _shard_doc 이 없으면 정렬 키 맨 끝에 `_shard_doc:asc` 를 붙인다. 4.8 이나 4.9 로 정한 정렬 키에 이 문단대로 붙인 키까지 더한 목록을 이 문서는 실제 정렬 키라 부른다.

[4.12] search_after 의 값은 실제 정렬 키마다 값 하나씩을 실제 정렬 키의 순서대로 쉼표로 이은 것이다. search_after 의 값을 쉼표마다 나눈 조각 하나하나가 값이다. 쉼표가 n 개이면 값은 n + 1 개이고 빈 조각도 값 하나로 센다. 값의 개수는 실제 정렬 키의 개수와 같아야 한다. _score 키의 값은 `^(0|[1-9][0-9]{0,8})\.[0-9]{4}$` 에 맞아야 하고 price, id, _shard_doc 키의 값은 `^(0|[1-9][0-9]{0,17})$` 에 맞아야 한다.

[4.13] search_after 가 있으면 from 은 없거나 0 이어야 한다.

### 정렬 값과 정렬

[4.14] 결과의 정렬 값은 필드마다 다음과 같다. _score 는 1단계 3.12 의 반올림한 점수, price 는 가격, id 는 id 다. _shard_doc 은 샤드 번호 × 4294967296 + 내부 번호다. pit 가 있으면 내부 번호는 PIT 가 고정한 값이다.

[4.15] 결과는 실제 정렬 키를 앞에서부터 차례로 비교해 정렬한다. 두 결과의 정렬 값이 처음으로 다른 키에서 그 키의 방향이 asc 이면 값이 작은 결과가 앞서고 desc 이면 값이 큰 결과가 앞선다. 실제 정렬 키의 값이 모두 같으면 방향과 상관없이 샤드 번호가 작은 결과가 앞서고 샤드 번호도 같으면 내부 번호가 작은 결과가 앞선다.

[4.16] sort 와 pit 가 모두 없으면 4.15 의 순서는 1단계 4.32 의 순서와 같다.

### search_after

[4.17] search_after 가 있으면 결과 가운데 정렬 순서에서 search_after 값보다 뒤에 오는 결과만 남긴다. 실제 정렬 키를 앞에서부터 차례로 결과의 정렬 값과 search_after 값을 비교한다. 처음으로 값이 다른 키에서 방향이 asc 이고 결과의 값이 크면 그 결과는 뒤에 온다. 방향이 desc 이고 결과의 값이 작아도 뒤에 온다. 모든 키에서 값이 같은 결과는 뒤에 오지 않는다.

[4.18] search_after 값이 실제로 있는 결과의 정렬 값일 필요는 없다. 정렬 순서에서 그 값이 놓일 자리보다 뒤의 결과가 남는다.

[4.19] _score 의 값은 소수점 아래 넷째 자리까지의 수로 비교한다. search_after 의 `0.4700` 은 반올림한 점수 0.4700 과 같다.

### 출력

[4.20] SEARCH 는 먼저 `hits <결과 수> merged <합친 결과 수>` 한 줄을 출력한다. 결과 수는 1단계 4.24, 4.25 로 정한 결과 전체의 개수이고 search_after 와 from 의 영향을 받지 않는다. 합친 결과 수는 샤드마다 search_after 가 남긴 그 샤드의 결과 수와 from + size 가운데 작은 값을 구해 모두 더한 값이다. search_after 가 없으면 결과가 모두 남은 것으로 본다.

[4.21] 이어서 남은 결과를 4.15 대로 정렬하고 앞에서 from 개를 건너뛴 다음 결과부터 size 개까지 한 줄에 하나씩 출력한다. 남은 결과가 from 개 이하이면 첫 줄만 출력한다.

[4.22] 결과 줄은 `<id> <샤드 번호> <정렬 값> …` 형식이다. 정렬 값은 실제 정렬 키의 순서대로 공백 하나씩 띄워 쓴다. sort 와 pit 가 모두 없으면 결과 줄은 1단계 4.34 와 같다.

## 5. 오류 규칙

[5.1] 1단계의 명령어 목록에 OPEN_PIT 과 CLOSE_PIT 이 더해진다. 명령어가 둘 가운데 하나인 줄은 `ERR UNKNOWN_COMMAND` 를 내지 않는다.

[5.2] 색인이 없으면 OPEN_PIT 과 CLOSE_PIT 은 인자와 상관없이 `ERR NO_INDEX` 를 출력한다.

[5.3] OPEN_PIT 에 인자가 하나라도 있으면 `ERR BAD_ARGUMENT` 를 출력한다.

[5.4] CLOSE_PIT 의 인자가 정확히 1개가 아니거나 4.4 의 형식에 맞지 않으면 `ERR BAD_ARGUMENT` 를 출력한다. 인자가 올바르지만 그 id 의 PIT 가 열려 있지 않으면 `ERR NOT_FOUND` 를 출력한다. 연 적이 없는 id 와 이미 닫은 id 가 여기에 해당한다.

[5.5] 색인이 있으면 SEARCH 의 인자를 먼저 1단계 5.8 대로 확인한다. 이때 매개변수 이름의 목록은 4.6 의 일곱 가지다.

[5.6] from 의 값이 4.7 에 맞지 않거나 pit 의 값이 4.10 에 맞지 않으면 `ERR BAD_ARGUMENT` 다. sort 의 값이 4.8 에 맞지 않아도 `ERR BAD_ARGUMENT` 다. 빈 정렬 키, 모르는 필드, 모르는 방향, 같은 필드의 반복이 여기에 해당한다.

[5.7] pit 가 없는데 sort 에 _shard_doc 이 있으면 `ERR BAD_ARGUMENT` 다. search_after 값의 개수나 형식이 4.12 에 맞지 않거나 search_after 가 있는데 from 이 0 이 아니어도 `ERR BAD_ARGUMENT` 다. search_after 값의 개수는 pit 로 준 PIT 가 열려 있는지와 상관없이 4.11 의 실제 정렬 키로 센다. 실제 정렬 키가 price 하나일 때 `search_after=100,` 과 `search_after=,100` 은 빈 조각까지 세어 값이 둘이므로 `ERR BAD_ARGUMENT` 다.

[5.8] 인자가 모두 올바르고 pit 가 있으면 그 id 의 PIT 가 열려 있는지 확인한다. 열려 있지 않으면 `ERR NOT_FOUND` 를 출력한다.

[5.9] 마지막으로 from + size 가 10000 보다 크면 `ERR RESULT_WINDOW` 를 출력한다. size 가 없으면 10 으로 계산한다.

[5.10] 색인이 있을 때 5.5~5.9 에 걸리지 않은 SEARCH 는 오류를 내지 않는다.

## 6. 예시

[6.1] 예시의 표기는 1단계와 같다. 예시 블록 하나는 프로그램을 한 번 실행한 기록이다.

[6.2] from, size, sort 다. a 는 샤드 1 로, b 는 샤드 0 으로 간다. 첫 SEARCH 에서 샤드 0 은 결과 둘을, 샤드 1 은 셋 가운데 둘을 보내므로 합친 결과 수는 4 다. 가격이 같은 문서 2 와 3 은 desc 로 정렬해도 샤드 번호가 작은 문서 2 가 앞선다. trail 의 idf 는 샤드 0 에서 ln 2, 샤드 1 에서 ln 1.6 이다. from 이 9998 이고 size 가 2 이면 from + size 가 10000 이라 오류가 아니다.

```
> CREATE 2
> PUT 1 a acme 500 trail shoe
> PUT 2 b acme 300 trail shoe
> PUT 3 a zeta 300 rain shoe
> PUT 4 b zeta 100 rain shoe
> PUT 5 a acme 200 trail shoe
> SEARCH sort=price size=2
hits 5 merged 4
4 0 100
5 1 200
> SEARCH sort=price from=2 size=2
hits 5 merged 5
2 0 300
3 1 300
> SEARCH sort=price from=4 size=2
hits 5 merged 5
1 1 500
> SEARCH sort=price:desc
hits 5 merged 5
1 1 500
2 0 300
3 1 300
5 1 200
4 0 100
> SEARCH q=trail sort=_score,price:desc size=3
hits 3 merged 3
2 0 0.6931 300
1 1 0.4700 500
5 1 0.4700 200
> SEARCH sort=price from=9998 size=2
hits 5 merged 5
> SEARCH sort=price from=9999 size=2
ERR RESULT_WINDOW
```

[6.3] search_after 와 동점 해소 필드다. price 하나로 정렬하면 첫 페이지의 마지막 가격 300 을 넘겼을 때 같은 가격의 문서 3 이 빠진다. id 를 정렬의 마지막 기준으로 넣으면 300 과 2 를 넘겨 문서 3 부터 이어 읽는다. 이 예에서는 문서마다 id 가 달라서 동점이 풀린다. 1단계 4.13 대로 같은 id 의 문서가 여러 샤드에 있으면 price 와 id 가 모두 같은 결과가 생긴다. 그런 결과가 페이지 경계에 걸치면 id 를 정렬에 넣어도 경계 뒤쪽의 결과가 다음 페이지에서 빠진다.

```
> CREATE 2
> PUT 1 a acme 500 trail shoe
> PUT 2 b acme 300 trail shoe
> PUT 3 a zeta 300 rain shoe
> PUT 4 b zeta 100 rain shoe
> PUT 5 a acme 200 trail shoe
> SEARCH sort=price size=3
hits 5 merged 5
4 0 100
5 1 200
2 0 300
> SEARCH sort=price size=3 search_after=300
hits 5 merged 1
1 1 500
> SEARCH sort=price,id size=3
hits 5 merged 5
4 0 100 4
5 1 200 5
2 0 300 2
> SEARCH sort=price,id size=3 search_after=300,2
hits 5 merged 2
3 1 300 3
1 1 500 1
```

[6.4] 페이지 사이의 변경과 PIT 다. 첫 페이지를 읽은 뒤 가격이 150 인 문서 6 이 들어와 from 으로 읽은 둘째 페이지에 문서 5 가 다시 나온다. PIT 1 은 문서 6 까지 고정하므로 그 뒤의 PUT 7 과 DEL 5 는 PIT 의 결과를 바꾸지 않는다. 샤드 0 의 문서 2, 4, 6 은 내부 번호가 0, 1, 2 이고 샤드 1 의 문서 1, 3, 5 는 0, 1, 2 다. PIT 없이 search_after=150 으로 읽으면 지워진 문서 5 는 나오지 않고 가격이 120 인 문서 7 은 150 앞이라 나오지 않는다. q=trail 로 검색한 두 SEARCH 에서 문서 1 의 점수는 PIT 에서는 0.4700 이고 지금의 색인에서는 0.9808 이다. 문서 5 가 지워져 샤드 1 의 trail 문서 빈도가 1 로 줄었기 때문이다.

```
> CREATE 2
> PUT 1 a acme 500 trail shoe
> PUT 2 b acme 300 trail shoe
> PUT 3 a zeta 300 rain shoe
> PUT 4 b zeta 100 rain shoe
> PUT 5 a acme 200 trail shoe
> SEARCH sort=price size=2
hits 5 merged 4
4 0 100
5 1 200
> PUT 6 b acme 150 rain shoe
> SEARCH sort=price from=2 size=2
hits 6 merged 6
5 1 200
2 0 300
> OPEN_PIT
pit 1
> SEARCH pit=1 sort=price size=2
hits 6 merged 4
4 0 100 1
6 0 150 2
> PUT 7 a zeta 120 rain shoe
> DEL 5 a
> SEARCH pit=1 sort=price size=2 search_after=150,2
hits 6 merged 3
5 1 200 4294967298
2 0 300 0
> SEARCH sort=price size=2 search_after=150
hits 6 merged 3
2 0 300
3 1 300
> SEARCH pit=1 q=trail
hits 3 merged 3
2 0 0.9808 0
1 1 0.4700 4294967296
5 1 0.4700 4294967298
> SEARCH q=trail
hits 2 merged 2
2 0 0.9808
1 1 0.9808
> CLOSE_PIT 1
> SEARCH pit=1
ERR NOT_FOUND
```

[6.5] 오류와 경계다. pit 가 있으면 실제 정렬 키는 price 와 _shard_doc 이므로 search_after 값이 하나뿐이면 오류다. `search_after=1.0000` 은 점수 1.0000 보다 뒤, 곧 점수가 1 보다 작은 결과만 남긴다. PIT 2 는 연 적이 없으므로 from + size 가 한도를 넘어도 `ERR NOT_FOUND` 가 먼저 나온다.

```
> CREATE 2
> PUT 1 a acme 500 trail shoe
> SEARCH sort=_shard_doc
ERR BAD_ARGUMENT
> SEARCH sort=price:up
ERR BAD_ARGUMENT
> SEARCH sort=price,price
ERR BAD_ARGUMENT
> SEARCH sort=brand
ERR BAD_ARGUMENT
> SEARCH sort=price,
ERR BAD_ARGUMENT
> SEARCH sort=price search_after=1,2
ERR BAD_ARGUMENT
> SEARCH sort=price search_after=100,
ERR BAD_ARGUMENT
> SEARCH sort=price from=1 search_after=100
ERR BAD_ARGUMENT
> SEARCH search_after=1.5
ERR BAD_ARGUMENT
> SEARCH search_after=1.0000
hits 1 merged 0
> SEARCH pit=1
ERR NOT_FOUND
> OPEN_PIT
pit 1
> SEARCH pit=1 sort=price search_after=100
ERR BAD_ARGUMENT
> SEARCH pit=1 sort=price search_after=100,0
hits 1 merged 1
1 1 500 4294967296
> SEARCH pit=1 from=9991
ERR RESULT_WINDOW
> SEARCH pit=2 from=9991
ERR NOT_FOUND
> SEARCH from=9990
hits 1 merged 1
> CLOSE_PIT 1
> CLOSE_PIT 1
ERR NOT_FOUND
> OPEN_PIT now
ERR BAD_ARGUMENT
> CLOSE_PIT 01
ERR BAD_ARGUMENT
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계와 같다.

[7.2] 3단계 테스트도 출력을 글자 그대로 비교한다. 결과의 순서, 정렬 값, 합친 결과 수가 하나라도 다르면 실패다.

[7.3] 대규모 테스트는 주 샤드 5개 색인에 PUT 100,000개를 넣고 시작한다. 그 뒤 PIT 10개를 하나씩 열고 PIT 마다 search_after 를 쓴 SEARCH 2,000개를 실행한 다음 닫는다. 그 사이사이에 PUT 과 DEL 10,000개와 pit 가 없는 SEARCH 2,000개가 섞인다. pit 가 있는 SEARCH 는 결과가 수만 개에 이르고 search_after 값은 정렬 순서의 아무 자리나 가리킨다. PIT 하나에 쓰는 q, search_type, sort 의 조합은 세 가지 이하이고 size 는 5 이하다. pit 가 없는 SEARCH 의 결과는 500개 이하다. 제한 시간은 15초다. 페이지를 요청할 때마다 결과를 처음부터 다시 구해 정렬하는 풀이는 이 제한 시간을 넘기도록 규모를 정했다.

[7.4] 3단계를 검사할 때 1단계와 2단계 테스트도 다시 실행한다.
