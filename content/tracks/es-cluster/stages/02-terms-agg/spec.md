# 2단계: terms 집계와 오차 상한

## 1. 개요

[1.1] 2단계는 브랜드마다 문서 수를 세는 TERMS 명령을 더한다. TERMS 는 Elasticsearch 의 terms 집계처럼 샤드마다 문서 수가 많은 브랜드 몇 개만 조정 노드에 보내 합친 근사 결과를 출력한다. 근사 결과에는 오차 상한과 결과에 들지 못한 문서 수를 붙이고 색인 전체를 정확히 센 결과도 함께 출력한다.

[1.2] 1단계의 입출력 규칙과 CREATE, PUT, DEL, STATS, SEARCH 는 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 5.2 가 출력을 정하지 않은 줄 가운데 명령어가 TERMS 인 줄은 이 문서가 출력을 정한다. 1단계 4.20 이 출력을 정하지 않은 SEARCH 와 명령어가 OPEN_PIT, CLOSE_PIT 인 줄은 이 문서도 출력을 정하지 않는다.

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `TERMS <매개변수> …` | 브랜드마다 문서 수를 세어 근사 결과와 정확한 결과를 보여 준다 | 2줄 이상 |

## 2. 배경 개념

[2.1] 집계는 검색에 맞는 문서를 묶어 통계를 내는 기능이다. terms 집계는 필드 값마다 버킷을 하나씩 만들고 버킷마다 그 값을 가진 문서 수를 센다. 쇼핑 검색 화면에서 브랜드 필터 옆에 붙는 상품 수가 그 예다. Elasticsearch 의 terms 집계는 기본으로 문서 수가 많은 버킷 10개를 돌려준다. 문서 수가 같은 버킷은 값의 사전순으로 놓는다.

[2.2] 색인이 여러 샤드로 나뉘어 있으면 조정 노드는 샤드마다 집계를 맡기고 그 결과를 합친다. 샤드가 버킷을 모두 보내면 결과는 정확하다. 하지만 값의 종류가 많으면 주고받는 양이 크고 조정 노드의 메모리도 많이 든다. 그래서 샤드는 자기 문서에서 문서 수가 많은 버킷을 shard_size 개까지만 보낸다. 조정 노드는 같은 값의 버킷끼리 문서 수를 더해 다시 정렬하고 상위 size 개를 고른다.

[2.3] 이렇게 합친 결과는 근사다. 검색 결과와 달리 버킷의 문서 수는 여러 샤드에서 센 수를 더한 값이어서 어느 샤드에서도 상위에 들지 못한 값이 합치면 상위가 될 수 있다. 어떤 브랜드가 모든 샤드에서 shard_size 개의 바로 바깥에 놓이면 그 브랜드는 결과에서 빠진다. 한 샤드는 보냈지만 다른 샤드는 보내지 않은 브랜드는 문서 수가 실제보다 적게 나온다. 2.8 의 예에 두 경우가 모두 나온다.

[2.4] shard_size 를 키우면 결과가 정확해지지만 주고받는 버킷이 늘어난다. Elastic 문서는 shard_size 를 늘리는 쪽이 size 를 늘리는 쪽보다 훨씬 싸다고 설명한다. shard_size 의 기본값은 size × 1.5 + 10 이다. shard_size 가 size 보다 작은 것은 뜻이 없으므로 Elasticsearch 는 그런 shard_size 를 size 로 바꾼다.

[2.5] Elasticsearch 는 근사의 정도를 두 값으로 알린다. sum_other_doc_count 는 최종 상위 size 개 버킷에 들지 못한 문서 수다. 샤드가 보내지 않은 버킷의 문서와 조정 노드가 상위 size 개를 고르며 버린 버킷의 문서가 모두 여기에 들어간다. doc_count_error_upper_bound 는 버킷의 문서 수가 실제보다 얼마나 적을 수 있는지를 나타내는 상한이다.

[2.6] Elastic 문서는 doc_count_error_upper_bound 를 샤드마다 shard_size 안에 못 든 가장 큰 버킷의 크기를 더한 값이라고 설명한다. 조정 노드는 샤드가 보내지 않은 버킷의 크기를 모른다. Elasticsearch 는 그 크기 대신 샤드가 보낸 마지막 버킷의 문서 수를 쓴다. 보내지 못한 버킷은 보낸 버킷보다 앞서지 못했으므로 문서 수가 그 값보다 클 수 없다. 이 문서는 샤드 하나가 오차 상한에 보태는 이 값을 그 샤드의 오차 몫이라 부른다. 샤드가 보낸 버킷이 shard_size 개보다 적으면 그 샤드는 버킷을 모두 보낸 것이므로 Elasticsearch 는 오차 몫을 0 으로 본다. 샤드가 보낸 버킷이 정확히 shard_size 개이면 그 샤드에 버킷이 더 없었더라도 마지막 버킷의 문서 수를 오차 몫으로 쓴다.

[2.7] 어떤 버킷의 오차는 그 버킷을 보내지 않은 샤드에서만 생긴다. 버킷을 보낸 샤드에서는 그 값의 문서를 모두 셌기 때문이다. 그래서 버킷 하나의 오차 상한은 그 버킷을 보내지 않은 샤드들의 오차 몫만 더한 값이다. 집계 전체의 doc_count_error_upper_bound 는 모든 샤드의 오차 몫을 더한 값이다. 주 샤드가 하나뿐이면 문서 수가 정확하므로 Elasticsearch 는 집계 전체의 오차 상한을 0 으로 알린다. Elastic 문서는 이 오차를 문서 수 내림차순으로 정렬할 때만 이렇게 계산할 수 있다고 적는다.

[2.8] 아래는 주 샤드가 2개인 색인에서 size 2, shard_size 2 로 집계한 예다. 정확히 세면 첫째인 bolt 는 두 샤드 어디에서도 상위 2개에 들지 못해 근사 결과에 아예 나오지 않는다. cora 는 샤드 0 이 보내지 않은 1개만큼 적게 나온다. 두 샤드 모두 버킷을 shard_size 개 보냈으므로 오차 몫은 각각 마지막으로 보낸 버킷의 문서 수인 3 과 4 다.

```
          샤드 0   샤드 1   색인 전체
acme        -        4        4
bolt        2        3        5
cora        1        4        5
dune        3        -        3
ella        3        -        3

샤드 0 이 보낸 버킷  dune 3, ella 3   오차 몫 3
샤드 1 이 보낸 버킷  acme 4, cora 4   오차 몫 4
합친 버킷            acme 4, cora 4, dune 3, ella 3
근사 결과            acme 4 (오차 상한 3), cora 4 (오차 상한 3)
정확한 결과          bolt 5, cora 5
```

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 따른다.

## 4. 명령 규칙

### 형식

[4.1] `TERMS <매개변수> …` 는 색인의 모든 문서를 브랜드마다 세어 근사 결과와 정확한 결과를 출력한다. TERMS 는 색인을 바꾸지 않는다.

[4.2] TERMS 의 인자 하나하나는 1단계 4.18 의 `<이름>=<값>` 모양의 매개변수다. 이름은 size 와 shard_size 두 가지다. 한 TERMS 에 같은 이름을 두 번 쓸 수 없다. 매개변수의 순서는 결과를 바꾸지 않는다.

[4.3] size 는 출력할 버킷의 최대 개수다. 값은 `^[1-9][0-9]*$` 에 맞고 1000 이하인 10진 정수다. size 가 없으면 10 이다.

[4.4] shard_size 의 값은 `^[1-9][0-9]*$` 에 맞고 10000 이하인 10진 정수다.

[4.5] shard_size 가 없으면 size × 1.5 + 10 에서 소수 부분을 버린 값을 shard_size 로 쓴다. size 가 5 이면 17, 10 이면 25 다.

[4.6] 4.4 나 4.5 로 정한 shard_size 가 size 보다 작으면 size 를 shard_size 로 쓴다. 이 문서의 나머지에서 shard_size 는 이렇게 정한 값이다.

### 샤드가 보내는 버킷

[4.7] 샤드에서 브랜드 v 의 버킷은 그 샤드에 브랜드가 v 인 문서가 하나 이상 있을 때만 있다. 버킷의 문서 수는 그 샤드에서 브랜드가 v 인 문서의 수다.

[4.8] 샤드는 자기 버킷을 문서 수 내림차순으로 정렬하고 문서 수가 같으면 브랜드의 사전순으로 정렬한다. 샤드는 앞에서부터 shard_size 개까지의 버킷을 조정 노드에 보낸다. 버킷이 shard_size 개보다 적으면 모두 보낸다.

[4.9] 이 문서의 사전순은 두 문자열을 앞에서부터 한 글자씩 ASCII 코드로 비교하는 순서다. 한 문자열이 다른 문자열의 앞부분이면 짧은 쪽이 앞선다. 숫자는 영문자보다 앞선다. `a1` 은 `ab` 보다, `ab` 는 `abc` 보다 앞선다.

[4.10] 샤드의 오차 몫은 다음처럼 정한다. 샤드가 보낸 버킷이 shard_size 개보다 적으면 오차 몫은 0 이다. 보낸 버킷이 shard_size 개이면 오차 몫은 보낸 버킷 가운데 마지막 버킷의 문서 수다. 샤드에 버킷이 정확히 shard_size 개 있어서 모두 보냈어도 오차 몫은 마지막 버킷의 문서 수다.

### 근사 결과

[4.11] 조정 노드는 샤드들이 보낸 버킷을 브랜드별로 합친다. 합친 버킷의 근사 문서 수는 그 브랜드의 버킷을 보낸 샤드들이 보낸 문서 수를 더한 값이다. 버킷을 보내지 않은 샤드의 문서는 근사 문서 수에 들어가지 않는다.

[4.12] 합친 버킷을 근사 문서 수 내림차순으로 정렬하고 근사 문서 수가 같으면 브랜드의 사전순으로 정렬한다. 앞에서부터 size 개까지의 버킷이 근사 결과다. 합친 버킷이 size 개보다 적으면 모두 근사 결과다.

[4.13] 근사 결과에 든 버킷의 오차 상한은 그 브랜드의 버킷을 보내지 않은 샤드들의 오차 몫을 더한 값이다. 그 샤드에 그 브랜드의 문서가 아예 없어도 오차 몫을 더한다. 모든 샤드가 그 브랜드의 버킷을 보냈으면 오차 상한은 0 이다.

[4.14] 집계의 오차 상한은 P 가 1 이면 0 이다. P 가 2 이상이면 모든 샤드의 오차 몫을 더한 값이다.

[4.15] 근사 결과의 나머지 문서 수는 색인의 문서 수에서 근사 결과에 든 버킷들의 근사 문서 수를 뺀 값이다. 색인의 문서 수는 모든 샤드의 문서 수를 더한 값이다.

### 정확한 결과

[4.16] 브랜드 v 의 정확한 문서 수는 모든 샤드에서 브랜드가 v 인 문서의 수를 더한 값이다. 정확한 문서 수가 1 이상인 브랜드마다 버킷이 하나 있다.

[4.17] 이 버킷들을 정확한 문서 수 내림차순으로 정렬하고 정확한 문서 수가 같으면 브랜드의 사전순으로 정렬한다. 앞에서부터 size 개까지의 버킷이 정확한 결과다. 버킷이 size 개보다 적으면 모두 정확한 결과다.

[4.18] 정확한 결과의 나머지 문서 수는 색인의 문서 수에서 정확한 결과에 든 버킷들의 정확한 문서 수를 뺀 값이다.

[4.19] 1단계 4.13 대로 같은 id 의 문서가 여러 샤드에 있으면 각각을 따로 센다.

### 출력

[4.20] TERMS 는 먼저 `terms <근사 결과의 버킷 수> error <집계의 오차 상한> other <근사 결과의 나머지 문서 수>` 한 줄을 출력한다.

[4.21] 이어서 근사 결과의 버킷마다 4.12 의 순서대로 한 줄씩 `<브랜드> <근사 문서 수> <오차 상한> <정확한 문서 수>` 를 출력한다. 오차 상한은 4.13 의 값이고 정확한 문서 수는 4.16 의 값이다.

[4.22] 다음으로 `exact <정확한 결과의 버킷 수> other <정확한 결과의 나머지 문서 수>` 한 줄을 출력한다.

[4.23] 마지막으로 정확한 결과의 버킷마다 4.17 의 순서대로 한 줄씩 `<브랜드> <정확한 문서 수>` 를 출력한다.

[4.24] 색인에 문서가 없으면 TERMS 는 `terms 0 error 0 other 0` 과 `exact 0 other 0` 두 줄만 출력한다.

## 5. 오류 규칙

[5.1] 1단계의 명령어 목록에 TERMS 가 더해진다. 명령어가 TERMS 인 줄은 `ERR UNKNOWN_COMMAND` 를 내지 않는다.

[5.2] 색인이 없으면 TERMS 는 인자와 상관없이 `ERR NO_INDEX` 를 출력한다.

[5.3] 색인이 있으면 TERMS 의 인자를 다음처럼 확인한다. 인자에 `=` 가 없거나 인자가 `=` 로 시작하면 `ERR BAD_ARGUMENT` 다. 매개변수 이름이 size 와 shard_size 가운데 하나가 아니거나 같은 이름이 두 번 이상 나와도 `ERR BAD_ARGUMENT` 다. size 의 값이 4.3 에 맞지 않거나 shard_size 의 값이 4.4 에 맞지 않아도 `ERR BAD_ARGUMENT` 다. `TERMS 5`, `TERMS field=brand`, `TERMS size=0`, `TERMS size=1001`, `TERMS shard_size=010`, `TERMS size=2 size=3` 이 여기에 해당한다.

[5.4] 색인이 있을 때 인자가 올바른 TERMS 는 오류를 내지 않는다.

## 6. 예시

[6.1] 예시의 표기는 1단계와 같다. 예시 블록 하나는 프로그램을 한 번 실행한 기록이다.

[6.2] 2.8 의 색인이다. b 는 샤드 0 으로, a 는 샤드 1 로 간다. `size=2` 만 주면 shard_size 는 13 이다. 두 샤드가 버킷을 모두 보내고 보낸 버킷이 13개보다 적으므로 오차 몫이 모두 0 이고 근사 결과가 정확한 결과와 같다. `size=3 shard_size=2` 에서는 shard_size 가 3 이 된다. 샤드 0 은 dune, ella, bolt 를 보내 오차 몫이 2 다. 샤드 1 은 버킷 셋을 모두 보냈지만 보낸 버킷이 shard_size 개이므로 오차 몫은 마지막 버킷의 문서 수인 3 이다. cora 는 여전히 샤드 0 의 1개가 빠져 acme 뒤에 놓인다.

```
> CREATE 2
> PUT 1 b bolt 100 shoe
> PUT 2 b bolt 100 shoe
> PUT 3 b cora 100 shoe
> PUT 4 b dune 100 shoe
> PUT 5 b dune 100 shoe
> PUT 6 b dune 100 shoe
> PUT 7 b ella 100 shoe
> PUT 8 b ella 100 shoe
> PUT 9 b ella 100 shoe
> PUT 10 a acme 100 shoe
> PUT 11 a acme 100 shoe
> PUT 12 a acme 100 shoe
> PUT 13 a acme 100 shoe
> PUT 14 a bolt 100 shoe
> PUT 15 a bolt 100 shoe
> PUT 16 a bolt 100 shoe
> PUT 17 a cora 100 shoe
> PUT 18 a cora 100 shoe
> PUT 19 a cora 100 shoe
> PUT 20 a cora 100 shoe
> TERMS size=2 shard_size=2
terms 2 error 7 other 12
acme 4 3 4
cora 4 3 5
exact 2 other 10
bolt 5
cora 5
> TERMS size=2
terms 2 error 0 other 10
bolt 5 0 5
cora 5 0 5
exact 2 other 10
bolt 5
cora 5
> TERMS size=3 shard_size=2
terms 3 error 5 other 7
bolt 5 0 5
acme 4 2 4
cora 4 2 5
exact 3 other 6
bolt 5
cora 5
acme 4
```

[6.3] 주 샤드가 3개이고 샤드마다 오차 몫이 다른 예다. store1, store2, store3 은 샤드 0, 1, 2 로 간다. `size=2 shard_size=2` 에서 샤드 0 은 버킷이 acme 하나뿐이라 오차 몫이 0 이고 샤드 1 과 2 는 오차 몫이 2 다. cora 를 보내지 않은 샤드는 샤드 0 뿐이므로 cora 의 오차 상한은 0 이다. `size=3 shard_size=2` 에서는 shard_size 가 3 이 된다. 샤드 1 은 버킷 셋을 모두 보내 오차 몫이 마지막 버킷의 문서 수인 1 이 된다. bolt 를 보내지 않은 샤드 0 과 2 의 오차 몫을 더하면 2 다.

```
> CREATE 3
> PUT 1 store1 acme 100 shoe
> PUT 2 store1 acme 100 shoe
> PUT 3 store1 acme 100 shoe
> PUT 4 store1 acme 100 shoe
> PUT 5 store2 bolt 100 shoe
> PUT 6 store2 bolt 100 shoe
> PUT 7 store2 bolt 100 shoe
> PUT 8 store2 cora 100 shoe
> PUT 9 store2 cora 100 shoe
> PUT 10 store2 dune 100 shoe
> PUT 11 store3 cora 100 shoe
> PUT 12 store3 cora 100 shoe
> PUT 13 store3 cora 100 shoe
> PUT 14 store3 acme 100 shoe
> PUT 15 store3 acme 100 shoe
> PUT 16 store3 dune 100 shoe
> PUT 17 store3 dune 100 shoe
> TERMS size=2 shard_size=2
terms 2 error 4 other 6
acme 6 2 6
cora 5 0 5
exact 2 other 6
acme 6
cora 5
> TERMS size=3 shard_size=2
terms 3 error 3 other 3
acme 6 1 6
cora 5 0 5
bolt 3 2 3
exact 3 other 3
acme 6
cora 5
bolt 3
```

[6.4] 주 샤드가 하나인 색인과 오류다. 문서가 없으면 두 줄만 나온다. 샤드가 버킷을 shard_size 개 보내서 오차 몫이 3 이어도 P 가 1 이므로 집계의 오차 상한은 0 이다.

```
> TERMS
ERR NO_INDEX
> CREATE 1
> TERMS
terms 0 error 0 other 0
exact 0 other 0
> PUT 1 a acme 100 x
> PUT 2 a acme 100 x
> PUT 3 a acme 100 x
> PUT 4 a bolt 100 x
> PUT 5 a bolt 100 x
> PUT 6 a cora 100 x
> TERMS size=1 shard_size=1
terms 1 error 0 other 3
acme 3 0 3
exact 1 other 3
acme 3
> TERMS size=0
ERR BAD_ARGUMENT
> TERMS 5
ERR BAD_ARGUMENT
> TERMS field=brand
ERR BAD_ARGUMENT
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계와 같다.

[7.2] 2단계 테스트도 출력을 글자 그대로 비교한다. 버킷의 순서나 수가 하나라도 다르면 실패다.

[7.3] 대규모 테스트는 주 샤드 5개 색인에 PUT 150,000개, DEL 10,000개, TERMS 200,000개를 섞어 넣는다. TERMS 는 PUT 120,000개와 DEL 2,500개를 처리한 뒤부터 나온다. PUT 의 일부는 기존 문서의 브랜드를 바꾸는 대체다. 서로 다른 브랜드는 80,000개 안팎이고 브랜드마다 문서 수는 크게 다르다. TERMS 의 size 는 10 이하이고 shard_size 는 주지 않거나 30 이하다. 제한 시간은 15초다. TERMS 마다 문서를 처음부터 다시 세거나 버킷을 모두 훑어 샤드가 보낼 버킷과 정확한 결과를 고르는 풀이는 브랜드의 문서 수를 정수 배열에 모아 두고 훑더라도 이 제한 시간을 넘기도록 규모를 정했다.

[7.4] 2단계 검사 입력에도 1단계 4.20 이 출력을 정하지 않은 SEARCH 와 명령어가 OPEN_PIT, CLOSE_PIT 인 줄은 나오지 않는다.

[7.5] 3단계를 검사할 때도 2단계 테스트를 다시 실행한다.
