# 2단계: 셔플과 맵 쪽 결합

## 1. 개요

[1.1] 2단계는 행이 실제로 어느 파티션에 놓이는지를 정한다. 해시 파티셔닝 규칙과 repartition, coalesce 의 배치 규칙을 정하고 셔플마다 맵 태스크가 보낸 레코드 수와 바이트 수, 받는 파티션별 바이트 수, 흘림 파일 수를 보고한다. reduceByKey 의 맵 쪽 결합이 셔플로 옮기는 양을 얼마나 줄이는지가 이 보고에 드러난다.

[1.2] 1단계의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 5.2 가 출력을 정하지 않은 명령어 SET 과 PROFILE 은 이 문서가 출력을 정한다. 1단계 4.15 의 join 은 이 문서도 출력을 정하지 않는다.

| 더하는 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `SET <설정 이름> <값>` | 설정을 바꾼다 | 없음 |
| `PROFILE <이름>` | 파이프라인을 실행하고 스테이지와 셔플의 지표를 출력한다 | 머리줄 1줄, 스테이지마다 1줄, 셔플마다 1줄 |

[1.3] 1단계의 PLAN 과 RUN 출력은 바뀌지 않는다. RUN 은 결과 행을 정렬해 출력하고 PLAN 은 행을 계산하지 않으므로 둘 다 이 문서가 정하는 배치와 상관없다. 3단계부터 PLAN 은 데이터셋의 행으로 크기를 추정하지만 이때도 배치는 쓰지 않는다.

## 2. 배경 개념

[2.1] Spark 의 해시 파티셔너는 키의 hashCode 를 파티션 수로 나눈 나머지를 파티션 번호로 쓴다. 키가 null 이면 0 번 파티션이다. Java 의 hashCode 는 음수일 수 있고 Java 와 Kotlin 의 나머지 연산자 `%` 는 나뉘는 수의 부호를 따르므로 음수 해시의 나머지는 음수가 된다. 그래서 Spark 는 나머지가 음수이면 파티션 수를 더해 0 이상으로 만든다. 절댓값을 먼저 취하는 방법은 결과가 다르고 Integer.MIN_VALUE 의 절댓값은 여전히 음수라서 틀린다. 문자열의 hashCode 는 글자 코드를 31 진법 수처럼 읽어 32비트로 감싼 값이다. 글자가 여섯 개쯤만 되어도 32비트를 넘쳐 음수가 흔히 나온다.

[2.2] 셔플에서 앞 스테이지의 태스크는 자기 파티션의 레코드를 받을 파티션별로 나누어 쓰고 뒤 스테이지의 태스크는 모든 앞 태스크가 자기 파티션 몫으로 쓴 것을 모아 읽는다. MapReduce 의 이름을 따서 셔플에 쓰는 쪽 태스크를 맵 태스크, 읽는 쪽 파티션을 받는 파티션이라 부른다. 셔플 비용은 옮기는 레코드 수와 바이트 수에 비례한다. 실제 엔진은 레코드를 직렬화한 크기를 재지만 이 트랙은 키 길이와 값 종류로 정하는 단순한 크기 규칙을 쓴다.

[2.3] reduceByKey 는 셔플하기 전에 맵 태스크 안에서 같은 키의 값을 먼저 합쳐 키마다 레코드 하나만 보낸다. 이것을 맵 쪽 결합이라 한다. 받는 쪽은 맵 태스크마다 온 부분 결과를 다시 합친다. 합치는 차례와 묶는 방식이 달라도 결과가 같아야 하므로 함수는 결합법칙과 교환법칙을 만족해야 한다. sum, min, max 가 그렇다. 맵 쪽 결합은 한 파티션 안에 같은 키가 여러 번 나올수록 효과가 크다. 앞 셔플이 같은 키를 이미 한 파티션에 모아 두었다면 뒤 셔플의 맵 쪽 결합은 더 많이 줄인다. groupByKey 는 값을 줄이지 않고 모두 보낸다.

[2.4] 맵 태스크는 보낼 레코드를 메모리 버퍼에 모은다. 버퍼가 메모리 한도를 넘으면 버퍼의 내용을 흘림 파일에 써서 디스크로 내보내고 버퍼를 비운다. 태스크가 끝나면 흘림 파일과 버퍼에 남은 것을 합쳐 셔플 출력을 만든다. Spark 의 ExternalSorter 는 합치는 함수가 있으면 이때 여러 흘림 파일에 흩어진 같은 키를 다시 합치므로 흘림이 생겨도 셔플로 보내는 레코드는 그대로이고 흘림 파일을 쓰고 다시 읽는 디스크 비용만 는다. 맵 쪽 결합을 하는 버퍼는 같은 키가 다시 와도 커지지 않지만 버퍼를 비운 뒤에 온 같은 키는 새 항목이 된다.

[2.5] Spark 의 repartition 은 맵 태스크마다 무작위로 고른 받는 파티션부터 시작해 레코드를 하나씩 다음 파티션에 차례로 보낸다. 어느 파티션도 몰리지 않게 하려는 것이다. 이 트랙은 시작점을 맵 태스크 번호로 고정해 결과가 실행마다 같게 한다. 이렇게 보내면 레코드가 어느 파티션으로 가는지는 키가 아니라 맵 태스크 안에서 몇째 레코드인지가 정한다. 그래서 파티션 안의 행 순서가 배치를 바꾼다.

[2.6] Spark 의 coalesce 는 부모 파티션의 위치 정보가 없으면 부모 파티션을 번호 순서대로 연속한 구간으로 나누어 새 파티션 하나에 구간 하나를 맡긴다. 부모 P 개를 n 개로 줄이면 새 파티션 c 는 번호가 ⌊c × P / n⌋ 이상 ⌊(c + 1) × P / n⌋ 미만인 부모를 맡는다. 부모 3개를 2개로 줄이면 새 파티션 0 은 부모 0 을, 새 파티션 1 은 부모 1 과 2 를 맡는다.

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 쓴다.

## 4. 명령 규칙

### 파티션 안의 행

[4.1] 계보의 변환마다 출력은 1단계 4.23 의 파티션 수만큼의 파티션으로 이뤄지고 파티션마다 순서 있는 행 목록이다. 3단계의 쏠림 처리와 4단계의 파티션 병합은 실행할 때 일부 변환의 출력 파티션 수를 바꾼다. 4.2~4.11 이 파티션마다 어떤 행이 어떤 순서로 놓이는지 정한다. 비교와 정렬에 쓰는 키 순서는 1단계 4.34 의 키 순서다.

[4.2] `source d` 의 파티션 i 는 데이터셋 d 의 파티션 i 이고 행 순서는 1단계 4.8 의 순서다.

[4.3] map 과 filter 의 출력 파티션 i 는 입력 파티션 i 의 행을 차례대로 바꾸거나 거른 것이다. 행 순서는 그대로다.

[4.4] `coalesce <n>` 의 입력 파티션 수를 P 라 하자. n 이 P 보다 작으면 출력 파티션 c 는 번호가 ⌊c × P / n⌋ 이상 ⌊(c + 1) × P / n⌋ 미만인 입력 파티션들의 행을 번호 순서대로 이어 붙인 것이다. n 이 P 이상이면 출력 파티션 i 는 입력 파티션 i 와 같다.

[4.5] 좁은 의존인 reduceByKey 와 groupByKey 는 입력 파티션마다 따로 1단계 4.20 이나 4.21 처럼 묶는다. 출력 파티션 i 는 입력 파티션 i 의 행을 묶어 낸 행을 키 순서로 놓은 것이다.

### 셔플

[4.6] 넓은 의존인 변환의 입력은 앞 스테이지의 마지막 변환이 내보낸 파티션들이다. 앞 스테이지의 태스크를 이 셔플의 맵 태스크라 한다. 맵 태스크 i 는 입력 파티션 i 를 맡아 그 행을 차례대로 처리해 레코드를 보낸다. 넓은 의존인 변환의 출력 파티션을 받는 파티션이라 한다.

[4.7] 넓은 의존인 reduceByKey 와 groupByKey 는 키가 K 인 레코드를 받는 파티션 p(K) 로 보낸다. 받는 파티션 수가 n 일 때 K 가 NULL 이면 p(K) 는 0 이다. 그렇지 않으면 p(K) 는 4.8 의 해시값 h(K) 를 n 으로 나눈 나머지 가운데 0 이상 n 미만인 수다. 곧 h(K) − p(K) 가 n 의 배수가 되는 0 이상 n 미만의 정수다.

[4.8] 키 K 의 글자가 앞에서부터 c₁, c₂, …, c_L 일 때 해시값 h(K) 는 다음처럼 구한다. 처음에 h 는 0 이다. 글자를 앞에서부터 하나씩 읽을 때마다 h × 31 에 그 글자의 코드를 더하고 그 값을 32비트 2의 보수로 감싸 −2,147,483,648 이상 2,147,483,647 이하로 만든 값을 새 h 로 삼는다. 마지막 글자까지 읽은 뒤의 h 가 해시값이다. Java 와 Kotlin 의 String.hashCode() 와 같다. `apple` 의 해시값은 93029210 이고 `banana` 의 해시값은 −1396355227 이다. 파티션 수가 3 이면 `banana` 는 받는 파티션 2 로 간다. Kotlin 의 `%` 로 구한 −1 이나 절댓값을 먼저 취해 구한 1 은 틀린 답이다.

[4.9] `repartition <n>` 은 맵 태스크 i 가 보내는 j 째 레코드를 받는 파티션 (i + j) mod n 으로 보낸다. j 는 그 맵 태스크가 보내는 레코드를 0 부터 센 번호다.

[4.10] 넓은 의존인 reduceByKey 는 맵 쪽 결합을 한다. 맵 태스크는 맡은 파티션의 행을 키가 같은 것끼리 묶어 묶음마다 레코드 하나를 보낸다. 그 레코드의 값은 묶음의 값 전체를 reduceByKey 의 함수로 줄인 값이다. 결합한 레코드를 보내는 차례는 이 문서의 어떤 출력도 바꾸지 않으므로 정하지 않는다. groupByKey 와 repartition 은 결합하지 않고 행 하나를 레코드 하나로 보낸다.

[4.11] 넓은 의존인 reduceByKey 의 받는 파티션에는 그 파티션에 온 레코드를 키가 같은 것끼리 묶어 함수로 줄인 행이 키 순서로 놓인다. 넓은 의존인 groupByKey 의 받는 파티션에는 온 레코드를 키가 같은 것끼리 묶어 목록 값으로 만든 행이 키 순서로 놓인다. repartition 의 받는 파티션에는 온 레코드가 맵 태스크 번호 순서로 놓이고 같은 맵 태스크가 보낸 레코드끼리는 보낸 차례대로 놓인다. 목록 값 안의 원소 순서는 이 문서의 어떤 출력도 바꾸지 않으므로 정하지 않는다.

### 레코드 크기

[4.12] 레코드 하나의 바이트 수는 키 부분과 값 부분의 합이다. 키 부분은 키의 글자 수에 2 를 더한 값이고 NULL 키는 2 다. 값 부분은 정수 값이 8 이고 원소가 m 개인 목록 값이 4 + 8 × m 이다. `apple 8` 은 15바이트, `NULL 7` 은 10바이트, `kiwi [1,2]` 는 26바이트다.

### 메모리 한도와 흘림

[4.13] 메모리 한도는 맵 태스크 하나가 버퍼에 담을 수 있는 바이트 수다. 처음에는 한도가 없다. `SET memory <L>` 로 한도를 L 바이트로 정하고 `SET memory -1` 로 한도를 없앤다.

[4.14] 셔플에 쓰는 맵 태스크마다 버퍼 크기를 0 에서 시작해 맡은 파티션의 행을 차례대로 하나씩 버퍼에 넣는다. 넓은 의존인 reduceByKey 의 맵 태스크는 버퍼를 마지막으로 비운 뒤 같은 키의 행이 이미 들어왔으면 버퍼 크기를 그대로 두고 처음 들어오는 키이면 그 행의 바이트 수만큼 버퍼 크기를 늘린다. 그 밖의 맵 태스크는 행마다 그 행의 바이트 수만큼 버퍼 크기를 늘린다. 행 하나를 넣은 뒤 한도가 있고 버퍼 크기가 한도보다 크면 흘림이 한 번 생기고 버퍼를 비운다. 버퍼를 비우면 크기는 0 이 되고 들어온 키도 모두 잊는다.

[4.15] 흘림은 맵 태스크가 보내는 레코드를 바꾸지 않는다. 넓은 의존인 reduceByKey 의 맵 태스크는 흘림이 몇 번 생겼든 4.10 대로 맡은 파티션의 서로 다른 키마다 레코드 하나를 보낸다.

### PROFILE

[4.16] `PROFILE <이름>` 은 그 이름의 계보를 실행하고 결과 행 대신 실행 지표를 출력한다. 이 줄은 정확히 두 토큰이어야 하고 이름은 데이터셋이나 파이프라인이다. PROFILE 은 행동이다. PROFILE 하나가 잡 하나이고 RUN 과 잡 번호를 함께 센다.

[4.17] PROFILE 은 먼저 1단계 4.31 과 같은 머리줄을 출력한다. 다음으로 스테이지마다 1단계 4.29 와 같은 스테이지 줄을 스테이지 번호 순서로 출력한다. 셔플에 쓰는 스테이지는 스테이지 줄 바로 다음 줄에 셔플 줄을 출력한다. 결과 행은 출력하지 않는다.

[4.18] 셔플 줄은 `write records <R> bytes <B> spills <S> sizes <b0>,<b1>,…,<bn−1>` 이다. R 은 그 셔플의 맵 태스크들이 보낸 레코드 수의 합이다. B 는 그 레코드들의 바이트 수 합이다. S 는 그 셔플의 맵 태스크들에서 생긴 흘림 수의 합이다. sizes 뒤에는 받는 파티션 0 부터 n − 1 까지 각 파티션이 받은 레코드의 바이트 수 합을 쉼표로 이어 쓴다. n 은 받는 파티션 수다. 레코드를 하나도 받지 않은 파티션은 0 이다.

### SET

[4.19] `SET <설정 이름> <값>` 은 설정 하나를 바꾼다. 이 줄은 정확히 세 토큰이어야 한다. 바꾼 설정은 다음 줄부터 그 설정을 다시 바꿀 때까지 쓰인다. 성공하면 아무것도 출력하지 않는다.

[4.20] 2단계의 설정 이름은 memory 하나다. memory 의 값은 −1 이거나 1 이상 1,000,000,000,000,000 이하의 정수 토큰이다.

## 5. 오류 규칙

[5.1] SET 은 아래 순서로 확인하고 처음 걸린 오류 하나를 출력한다. 첫째, 줄이 세 토큰이 아니면 `ERR SYNTAX` 다. 둘째, 설정 이름이 이 단계까지 정한 이름이 아니면 `ERR UNKNOWN_SETTING` 이다. 셋째, 값이 그 설정에 허용된 값이 아니면 `ERR SYNTAX` 다. 다만 줄이 세 토큰이고 설정 이름이 broadcastThreshold, skewFactor, skewThreshold, advisorySize, adaptive, minPartitionSize 가운데 하나이면 이 문서는 그 줄부터 입력 끝까지의 출력을 정하지 않는다. 이 이름들은 3단계와 4단계에서 더한다.

[5.2] PROFILE 은 먼저 줄이 4.16 의 꼴인지 확인해 아니면 `ERR SYNTAX` 를 출력한다. 이름이 오는 자리의 토큰이 1단계 4.1 의 정규식에 맞지 않는 줄도 여기서 `ERR SYNTAX` 다. 꼴이 맞으면 다음으로 이름이 데이터셋도 파이프라인도 아닐 때 `ERR NOT_FOUND <이름>` 을 출력한다. 오류가 난 PROFILE 은 잡을 만들지 않는다.

[5.3] 2단계에서 생기는 오류 줄은 `ERR UNKNOWN_SETTING` 이다.

| 오류 줄 | 뜻 |
|---|---|
| `ERR UNKNOWN_SETTING` | 모르는 설정 이름 |

## 6. 예시

[6.1] 예시의 형식은 1단계 6.1 과 같다. 6.3 은 6.2 의 입력을 먼저 실행한 것으로 보고 그 줄들을 생략한다.

[6.2] 맵 쪽 결합이다. 받는 파티션이 2개일 때 home 은 해시값 3208415 라 파티션 1 로, cart 는 해시값 3046176 이라 파티션 0 으로, NULL 은 파티션 0 으로 간다. by_key 의 맵 태스크 0 은 home 세 행을 레코드 하나로 합쳐 레코드 2개를 보내고 맵 태스크 1 은 레코드 3개를 보낸다. grouped 는 행 7개를 모두 보낸다. home 레코드는 14바이트, cart 레코드도 14바이트, NULL 레코드는 10바이트다. again 의 reduceByKey 는 좁은 의존이라 셔플이 없으므로 스테이지 1 다음에는 셔플 줄이 없다.

```
> DATA clicks 2
> 0 home 1
> 0 cart 1
> 0 home 1
> 0 home 1
> 1 cart 1
> 1 home 1
> 1 NULL 1
> END
> PIPE by_key = source clicks | reduceByKey sum 2
> PIPE grouped = source clicks | groupByKey 2 | map value sum
> PROFILE by_key
job 0 stages 2 tasks 4 rows 3
stage 0 tasks 2 source(clicks) -> shuffle 1
write records 5 bytes 66 spills 0 sizes 38,28
stage 1 tasks 2 reduceByKey -> result
> PROFILE grouped
job 1 stages 2 tasks 4 rows 3
stage 0 tasks 2 source(clicks) -> shuffle 1
write records 7 bytes 94 spills 0 sizes 38,56
stage 1 tasks 2 groupByKey map -> result
> RUN grouped
job 2 stages 2 tasks 4 rows 3
NULL 1
cart 2
home 4
> PIPE again = source by_key | filter value > 1 | reduceByKey max 2
> PROFILE again
job 3 stages 2 tasks 4 rows 2
stage 0 tasks 2 source(clicks) -> shuffle 1
write records 5 bytes 66 spills 0 sizes 38,28
stage 1 tasks 2 reduceByKey filter reduceByKey -> result
```

[6.3] 흘림이다. 한도가 20바이트이면 by_key 의 맵 태스크 0 은 home 을 넣어 14바이트가 되고 cart 를 넣어 28바이트가 되어 흘린다. 버퍼를 비웠으므로 다음 home 은 새 키로 들어와 14바이트가 되고 마지막 home 은 버퍼 크기를 바꾸지 않는다. 맵 태스크 1 은 cart 와 home 을 넣어 한 번 흘린다. 흘림이 생겨도 보낸 레코드 수와 바이트 수는 6.2 와 같다. grouped 의 맵 태스크 0 은 행 두 개마다 28바이트가 되어 두 번 흘리고 맵 태스크 1 은 한 번 흘린다.

```
> SET memory 20
> PROFILE by_key
job 4 stages 2 tasks 4 rows 3
stage 0 tasks 2 source(clicks) -> shuffle 1
write records 5 bytes 66 spills 2 sizes 38,28
stage 1 tasks 2 reduceByKey -> result
> PROFILE grouped
job 5 stages 2 tasks 4 rows 3
stage 0 tasks 2 source(clicks) -> shuffle 1
write records 7 bytes 94 spills 3 sizes 38,56
stage 1 tasks 2 groupByKey map -> result
```

[6.4] 음수 해시다. banana 의 해시값 −1396355227 을 3 으로 나눈 0 이상의 나머지는 2 다. polygenelubricants 의 해시값은 −2147483648 이고 3 으로 나눈 0 이상의 나머지는 1 이다. 이 값의 절댓값은 32비트 정수로 나타낼 수 없다. apple 의 해시값 93029210 의 나머지는 2 다. 받는 파티션 0 에는 레코드가 하나도 오지 않는다. 이 블록은 새 실행이다.

```
> DATA words 1
> 0 banana 1
> 0 polygenelubricants 1
> 0 apple 1
> END
> PIPE w = source words | reduceByKey sum 3
> PROFILE w
job 0 stages 2 tasks 4 rows 3
stage 0 tasks 1 source(words) -> shuffle 1
write records 3 bytes 59 spills 0 sizes 0,28,31
stage 1 tasks 3 reduceByKey -> result
```

[6.5] coalesce 와 repartition 의 배치다. coalesce 2 는 파티션 0 을 새 파티션 0 에, 파티션 1 과 2 를 새 파티션 1 에 맡긴다. 첫 repartition 에서 맵 태스크 0 은 a 와 be 를 받는 파티션 0 과 1 로, 맵 태스크 1 은 cat, deer, eagle, falcon 을 파티션 1, 2, 0, 1 로 보낸다. 그 결과 받는 파티션 0 은 a 와 eagle, 1 은 be, cat, falcon, 2 는 deer 를 이 순서로 담는다. 둘째 repartition 의 맵 태스크 1 은 be, cat, falcon 을 파티션 1, 0, 1 로 보내고 맵 태스크 2 는 deer 를 파티션 0 으로 보낸다. 이 블록도 새 실행이다.

```
> DATA logs 3
> 0 a 1
> 0 be 2
> 1 cat 3
> 2 deer 4
> 2 eagle 5
> 2 falcon 6
> END
> PIPE r = source logs | coalesce 2 | repartition 3 | repartition 2
> PROFILE r
job 0 stages 3 tasks 7 rows 6
stage 0 tasks 2 source(logs) coalesce -> shuffle 1
write records 6 bytes 81 spills 0 sizes 26,41,14
stage 1 tasks 3 repartition -> shuffle 2
write records 6 bytes 81 spills 0 sizes 38,43
stage 2 tasks 2 repartition -> result
```

[6.6] 셔플 두 번이다. 첫 셔플에서 a1, a5, b2 는 받는 파티션 0 에 모이고 파티션 1 은 비며 c1 은 2 로, b1 은 3 으로 간다. 받는 파티션 0 의 행은 키 순서로 a1, a5, b2 다. 키를 앞 한 글자로 줄이면 이 파티션에 a 가 둘 생기고 둘째 셔플의 맵 쪽 결합이 둘을 레코드 하나로 합치므로 둘째 셔플은 레코드 4개를 보낸다. 이 블록도 새 실행이다.

```
> DATA sales 2
> 0 a1 5
> 0 b1 1
> 1 a5 2
> 1 b2 3
> 1 c1 4
> END
> PIPE twice = source sales | reduceByKey sum 4 | map key prefix 1 | reduceByKey sum 2
> PROFILE twice
job 0 stages 3 tasks 8 rows 3
stage 0 tasks 2 source(sales) -> shuffle 1
write records 5 bytes 60 spills 0 sizes 36,0,12,12
stage 1 tasks 4 reduceByKey map -> shuffle 2
write records 4 bytes 44 spills 0 sizes 22,22
stage 2 tasks 2 reduceByKey -> result
> RUN twice
job 1 stages 3 tasks 8 rows 3
a 7
b 4
c 4
```

[6.7] 오류다. 한도 0 은 허용된 값이 아니다. cache 는 정한 설정 이름이 아니므로 값을 보기 전에 UNKNOWN_SETTING 이다. 오류가 난 PROFILE 은 잡을 만들지 않는다. 이 블록은 6.6 에 이어 실행한 것이다.

```
> SET memory 0
ERR SYNTAX
> SET memory
ERR SYNTAX
> SET cache abc
ERR UNKNOWN_SETTING
> SET memory abc
ERR SYNTAX
> PROFILE nope
ERR NOT_FOUND nope
> PROFILE twice 3
ERR SYNTAX
> RUN twice 1
job 2 stages 3 tasks 8 rows 3
a 7
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계와 같다. PROFILE 이 다루는 계보의 변환도 1단계 7.6 대로 1,000개 이하다.

[7.2] 2단계 검사 입력에는 4.12 의 레코드 크기로 버퍼 크기와 바이트 수를 셀 때 64비트 정수를 넘는 값이 나오지 않는다.

[7.3] 대규모 테스트는 파티션 8개짜리 데이터셋에 행 800,000개를 넣고 서로 다른 키를 200,000개 안팎 둔다. 파티션마다 같은 키가 여러 번 나오고 몇몇 키는 아주 자주 나온다. reduceByKey, groupByKey, repartition, coalesce 를 섞은 파이프라인에 PROFILE 과 RUN 을 모두 15개 안팎 실행하고 그 사이에 SET memory 로 한도를 여러 번 바꾼다. 한도가 없거나 맵 태스크 하나의 출력보다 큰 PROFILE 도 섞인다. RUN 은 모두 개수를 20 이하로 준다. 제한 시간은 15초다. 레코드마다 버퍼를 처음부터 훑어 같은 키를 찾거나 버퍼 크기를 처음부터 다시 더하는 풀이는 이 제한 시간을 넘기도록 규모를 정했다.

[7.4] 2단계 검사 입력에는 1단계 4.15 가 출력을 정하지 않은 줄도, 이 문서 5.1 이 출력을 정하지 않은 줄도 나오지 않는다.

[7.5] 2단계를 검사할 때 1단계 테스트도 다시 실행한다. 3단계와 4단계를 검사할 때도 2단계 테스트를 다시 실행한다.
