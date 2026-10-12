# 4단계: 적응 실행

## 1. 개요

[1.1] 4단계는 적응 실행을 더한다. 적응 실행을 켜면 RUN 과 PROFILE 은 셔플이 끝날 때마다 셔플 출력의 실제 크기로 남은 계획을 고친다. 셔플 뒤 한쪽 입력이 작아진 정렬 병합 조인은 브로드캐스트 조인으로 바꾸고 작은 파티션이 많은 셔플은 이웃한 파티션을 묶어 태스크 수를 줄인다. 묶은 파티션에는 번호를 다시 매긴다. 4단계의 대규모 테스트는 행 수백만 개를 다룬다.

[1.2] 1단계부터 3단계까지의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 3단계 5.2 가 출력을 정하지 않은 설정 이름 adaptive 와 minPartitionSize 는 이 문서가 출력을 정한다.

| 더하는 설정 | 하는 일 | 처음 값 |
|---|---|---|
| `adaptive` | 적응 실행을 켜고 끈다 | off |
| `minPartitionSize` | 파티션을 묶을 때 묶음 하나의 최소 크기 | 1048576 |

[1.3] 적응 실행은 처음에 꺼져 있다. 적응 실행이 꺼져 있으면 모든 명령의 출력은 3단계와 같다. PLAN 은 데이터를 계산하지 않으므로 적응 실행을 켜도 출력이 바뀌지 않는다.

## 2. 배경 개념

[2.1] 3단계까지의 계획은 실행 전에 정해진다. 엔진은 계획을 세울 때 셔플 뒤 데이터의 크기를 모르므로 추정 크기로 조인 전략을 고르고 셔플의 파티션 수도 미리 정한다. Spark SQL 의 셔플 파티션 수는 기본 200 이다. 데이터가 작으면 태스크마다 처리할 양보다 태스크를 시작하는 비용이 커지고 데이터가 크면 파티션 하나가 커진다. Spark 3.2.0 부터 기본으로 켜진 적응 실행은 셔플이 끝난 뒤 맵 출력의 실제 통계로 남은 계획을 다시 세운다.

[2.2] 파티션 병합은 번호가 이웃한 받는 파티션을 앞에서부터 차례로 묶다가 다음 파티션을 더하면 권장 크기를 넘을 때 새 묶음을 시작하고 한 묶음을 태스크 하나가 읽게 한다. 데이터를 다시 셔플하지 않고 태스크 수만 줄인다. 권장 크기만 보면 큰 파티션 사이에 낀 작은 파티션이 혼자 묶음이 될 수 있다. 그래서 Spark 는 최소 크기(`coalescePartitions.minPartitionSize`, 기본 1MB)보다 작은 묶음을 이웃한 묶음에 붙인다. 두 셔플 출력을 번호끼리 짝지어 읽는 조인에서는 같은 번호 파티션의 크기를 더해 양쪽을 똑같이 묶는다. 사용자가 `repartition(n)` 처럼 파티션 수를 직접 정한 셔플은 Spark 도 병합하지 않는다.

[2.3] 적응 실행은 셔플 뒤 한쪽 입력의 실제 크기가 브로드캐스트 임계값 이하이면 정렬 병합 조인을 브로드캐스트 조인으로 바꾼다. 3단계 2.5 처럼 filter 가 행을 거의 다 버려도 추정 크기는 줄지 않으므로 계획은 정렬 병합 조인을 고른다. 셔플을 끝내고 나서야 그 입력이 작다는 것이 드러난다. 이미 끝난 셔플은 되돌릴 수 없지만 큰 쪽을 정렬해 병합하는 비용은 아낀다. Spark 는 이때 큰 쪽의 셔플 출력을 맵 태스크 단위로 읽는 지역 읽기로 바꾸기도 하지만 이 엔진은 받는 파티션을 그대로 읽는다.

[2.4] 파티션을 묶으면 묶음마다 0 부터 새 번호가 붙고 뒤따르는 계산은 묶음 하나를 파티션 하나로 본다. 파티션 번호에 기대는 규칙은 모두 새 번호를 쓴다. repartition 이 레코드를 보내기 시작하는 파티션, coalesce 가 맡기는 구간, 다음 셔플의 맵 태스크 번호가 여기에 든다.

[2.5] 행 수백만 개를 다루면 행 하나를 처리하는 비용이 전체 시간을 정한다. JVM 에서 행마다 객체를 만들면 객체마다 객체 헤더가 붙고 Long 값은 박싱되며 값을 읽을 때마다 참조를 따라가야 하고 가비지 컬렉션의 부담이 커진다. 변환마다 행 목록을 새로 만들어 복사하면 같은 비용을 변환 수만큼 다시 치른다. 지연 실행은 행동마다 원천부터 다시 계산하므로 행동이 여럿이면 이 비용도 행동 수만큼 곱해진다. Spark 가 행을 JVM 객체 대신 바이트 배열에 담는 실행 방식을 따로 만든 이유도 여기에 있다.

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 쓴다.

## 4. 명령 규칙

### 설정

[4.1] `SET adaptive on` 은 적응 실행을 켜고 `SET adaptive off` 는 끈다. 처음에는 꺼져 있다. 최소 크기는 처음에 1048576 이고 `SET minPartitionSize <M>` 으로 바꾼다. M 은 1 이상 1,000,000,000,000,000 이하의 정수 토큰이다.

[4.2] 적응 실행이 꺼져 있으면 RUN 과 PROFILE 은 3단계와 똑같이 실행한다. 최소 크기는 적응 실행이 켜져 있을 때만 쓴다.

### 적응 실행의 흐름

[4.3] 적응 실행이 켜져 있으면 RUN 과 PROFILE 은 3단계대로 세운 계획의 스테이지를 번호 순서로 실행한다. 스테이지 하나를 실행하기 직전에 그 스테이지의 조인 전략 바꾸기(4.5, 4.6), 3단계 4.19~4.23 의 쏠림 처리, 파티션 병합(4.7~4.11)을 이 차례로 정한다. 세 결정은 앞 스테이지들이 이미 쓴 셔플의 실제 크기를 쓴다. 스테이지 번호 순서로 실행하므로 스테이지가 읽는 셔플은 모두 이미 쓰여 있다.

[4.4] 적응 실행은 스테이지의 구성과 개수와 보낼 곳을 바꾸지 않는다. 바뀌는 것은 조인 전략, 스테이지의 태스크 수, 파티션에 놓이는 행이다. RUN 의 결과 행은 적응 실행을 켜도 같다.

### 조인 전략 바꾸기

[4.5] 스테이지에 든 정렬 병합 조인 가운데 넓은 의존인 입력이 하나 이상 있는 조인마다 바꿀지 정한다. 입력의 실제 크기는 그 입력의 셔플에서 맵 태스크들이 보낸 레코드의 바이트 수 합, 곧 PROFILE 셔플 줄의 bytes 값이다. 브로드캐스트 임계값이 −1 이 아니고 넓은 의존인 입력 가운데 실제 크기가 임계값 이하인 입력이 있으면 그 조인은 브로드캐스트 조인으로 바뀐다. 그런 입력이 하나이면 그 입력이 빌드 쪽이다. 두 입력이 모두 그렇다면 실제 크기가 작은 쪽이 빌드 쪽이고 같으면 오른쪽이 빌드 쪽이다. 좁은 의존인 입력은 빌드 쪽이 되지 않는다.

[4.6] 바뀐 조인은 빌드 쪽 셔플의 받는 파티션을 모두 모아 빌드 쪽 행으로 쓴다. 나머지 입력이 스트림 쪽이다. 스트림 쪽이 넓은 의존이면 그 셔플의 받는 파티션이 스트림 쪽 파티션이고 좁은 의존이면 그 입력의 파티션이 스트림 쪽 파티션이다. 바뀐 조인의 출력 파티션 i 는 3단계 4.16 의 브로드캐스트 조인처럼 스트림 쪽 파티션 i 와 빌드 쪽 행을 모두 짝지은 결과다. 바뀐 조인에는 쏠림 처리를 하지 않는다. PROFILE 의 스테이지 줄은 바뀐 조인을 `join(broadcast-left)` 나 `join(broadcast-right)` 로 쓴다. 빌드 쪽 스테이지는 이미 셔플을 썼으므로 보낼 곳과 셔플 줄은 그대로이고 3단계 4.25 의 broadcast 줄은 출력하지 않는다.

### 파티션 병합

[4.7] 스테이지가 번호로 읽는 셔플은 그 스테이지의 넓은 의존인 reduceByKey, groupByKey, repartition 이 읽는 셔플이다. 계획이 정렬 병합 조인으로 고른 조인에서 넓은 의존인 입력의 셔플도 번호로 읽는 셔플이다. 4.5 로 바뀐 조인도 계획에서는 정렬 병합 조인이므로 스트림 쪽이 넓은 의존이면 그 셔플은 번호로 읽는 셔플이다. 4.6 의 빌드 쪽 셔플은 모두 모아 읽으므로 번호로 읽는 셔플이 아니다. 한 스테이지가 번호로 읽는 셔플은 받는 파티션 수가 모두 같다. 이 수를 n 이라 하자.

[4.8] 스테이지는 다음 네 조건을 모두 만족할 때 파티션을 묶는다. 첫째, 적응 실행이 켜져 있다. 둘째, 스테이지가 번호로 읽는 셔플이 하나 이상 있다. 셋째, 그 셔플 가운데 repartition 의 셔플이 없다. 넷째, 그 스테이지에서 쏠림 처리로 쪼갠 파티션이 없다. 받는 파티션 p 의 크기 sₚ 는 그 스테이지가 번호로 읽는 모든 셔플에서 받는 파티션 p 가 받은 바이트 수를 더한 값이다.

[4.9] 권장 크기를 A, 최소 크기를 M 이라 하자. 묶음은 번호가 이웃한 받는 파티션들이다. 1단계 4.20 과 2단계 4.10 의 묶음은 키가 같은 행의 모음이지만 이 문서의 묶음은 받는 파티션의 모음이다. 첫 묶음은 파티션 0 으로 시작한다. p 가 1 부터 n − 1 까지 차례로, 지금 묶음의 크기가 M 이상이고 지금 묶음의 크기에 sₚ 를 더하면 A 보다 크면 지금 묶음을 닫고 파티션 p 로 새 묶음을 시작한다. 그렇지 않으면 파티션 p 를 지금 묶음에 넣는다. 묶음의 크기는 그 묶음에 든 파티션의 크기 합이다. 마지막 파티션까지 넣은 뒤 지금 묶음을 닫는다. 묶음이 둘 이상이고 마지막 묶음의 크기가 M 보다 작으면 마지막 묶음을 바로 앞 묶음에 합친다. 이 규칙 때문에 크기가 A 를 넘는 묶음도 생길 수 있다.

[4.10] 묶음에는 앞에서부터 0, 1, 2, … 새 번호를 붙인다. 그 스테이지가 번호로 읽는 셔플은 모두 묶음 g 를 받는 파티션 g 하나로 읽는다. 그 파티션의 레코드는 묶음에 든 받는 파티션들의 레코드를 모두 합친 것이다. 셔플을 번호로 읽는 변환은 이 파티션을 2단계 4.11, 3단계 4.16, 이 문서 4.6 의 받는 파티션처럼 다룬다. 그래서 reduceByKey 의 출력 파티션은 묶음 전체에서 키 순서로 놓이고 조인의 출력 파티션은 묶음 전체에서 3단계 4.5 의 순서로 놓인다. 묶음 안 레코드의 순서는 결과에 드러나지 않는다.

[4.11] 파티션을 묶은 스테이지에서 셔플을 번호로 읽는 변환은 묶음마다 출력 파티션 하나를 내므로 출력 파티션 수가 묶음 수와 같다. 그 뒤의 변환은 앞 변환이 실제로 낸 파티션을 입력 파티션으로 받는다. coalesce 는 입력 파티션 수와 인자 가운데 작은 쪽을 파티션 수로 삼고 2단계 4.4 의 구간도 입력 파티션 번호로 정한다. 나머지 변환은 입력 파티션 i 마다 출력 파티션 i 하나를 내므로 출력 파티션 수가 입력 파티션 수와 같다. 좁은 의존인 reduceByKey 와 groupByKey, 두 입력이 모두 좁은 의존인 정렬 병합 조인도 그렇다. 1단계 4.23 과 3단계 4.10 이 reduceByKey, groupByKey, 정렬 병합 조인의 출력 파티션 수로 정한 인자 n 은 그 스테이지에서 쓰지 않는다. 그 스테이지의 태스크 수는 마지막 변환의 출력 파티션 수이고 그 스테이지가 셔플에 쓰면 이 파티션 번호가 맵 태스크 번호다.

### PROFILE

[4.12] 파티션을 묶은 스테이지는 묶음 수가 n 보다 작을 때 스테이지 줄 바로 다음 줄에 `merge <a₀>-<b₀>,<a₁>-<b₁>,…` 를 출력한다. aᵢ 와 bᵢ 는 묶음 i 에 든 받는 파티션의 첫 번호와 끝 번호이고 묶음 순서대로 쉼표로 잇는다. 파티션이 하나인 묶음도 `2-2` 처럼 쓴다. 이때 2단계 4.17 의 셔플 줄과 3단계 4.25 의 broadcast 줄은 merge 줄 다음에 온다. 묶음 수가 n 과 같으면 merge 줄을 출력하지 않는다. 쏠림 처리로 쪼갠 파티션이 있는 스테이지는 묶지 않으므로 merge 줄과 skew 줄이 한 스테이지에 함께 나오지 않는다.

[4.13] RUN 의 머리줄과 PROFILE 의 머리줄과 스테이지 줄의 태스크 수는 3단계 4.24 대로 실제로 실행한 태스크 수다. 적응 실행으로 바뀐 조인 전략과 묶은 파티션이 여기에 반영된다.

## 5. 오류 규칙

[5.1] adaptive 의 값이 `on` 이나 `off` 가 아니면 `ERR SYNTAX` 다. 대소문자를 구분하므로 `ON` 도 `ERR SYNTAX` 다. minPartitionSize 의 값이 4.1 의 범위 밖이면 `ERR SYNTAX` 다.

[5.2] 4단계로 설정 이름이 모두 정해진다. 설정 이름은 memory, broadcastThreshold, skewFactor, skewThreshold, advisorySize, adaptive, minPartitionSize 일곱 가지이고 그 밖의 이름은 2단계 5.1 대로 `ERR UNKNOWN_SETTING` 이다.

[5.3] 4단계에서 새로 생기는 오류 줄은 없다.

## 6. 예시

[6.1] 예시의 형식은 1단계 6.1 과 같다. 6.3 과 6.4 는 6.2 의 입력을 먼저 실행하고 앞 예시를 차례로 실행한 것으로 보고 그 줄들을 생략한다.

[6.2] 파티션 병합이다. events 를 받는 파티션 8개에 셔플하면 크기는 0, 44, 22, 11, 11, 11, 11, 0 이다. 권장 크기 30, 최소 크기 12 로 묶으면 파티션 0 은 크기가 0 이라 최소 크기에 못 미쳐 닫히지 못하고 파티션 1 과 한 묶음이 된다. 파티션 2 를 더하면 66 이 되어 30 을 넘으므로 새 묶음이 시작된다. 파티션 3 은 크기 11 이 최소 크기보다 작아 파티션 4 와 묶인다. 마지막 묶음은 파티션 5, 6, 7 이다. 태스크는 8개에서 4개로 준다. PLAN 은 바뀌지 않고 결과 행도 같다.

```
> DATA events 4
> 0 a 1
> 0 b 2
> 0 c 3
> 1 a 4
> 1 d 5
> 2 e 6
> 2 a 7
> 3 f 8
> 3 a 9
> 3 b 10
> END
> DATA tags 2
> 0 a 100
> 0 b 200
> 1 c 300
> 1 z 400
> END
> PIPE agg = source events | reduceByKey sum 8
> PROFILE agg
job 0 stages 2 tasks 12 rows 6
stage 0 tasks 4 source(events) -> shuffle 1
write records 10 bytes 110 spills 0 sizes 0,44,22,11,11,11,11,0
stage 1 tasks 8 reduceByKey -> result
> SET adaptive on
> SET advisorySize 30
> SET minPartitionSize 12
> PLAN agg
stage 0 tasks 4 source(events) -> shuffle 1
stage 1 tasks 8 reduceByKey -> result
> PROFILE agg
job 1 stages 2 tasks 8 rows 6
stage 0 tasks 4 source(events) -> shuffle 1
write records 10 bytes 110 spills 0 sizes 0,44,22,11,11,11,11,0
stage 1 tasks 4 reduceByKey -> result
merge 0-1,2-2,3-4,5-7
> RUN agg
job 2 stages 2 tasks 8 rows 6
a 21
b 12
c 3
d 5
e 6
f 8
```

[6.3] 번호 다시 매기기다. rep 의 스테이지 1 은 6.2 처럼 묶음 넷을 읽는다. 둘째 reduceByKey 는 좁은 의존이라 입력 파티션마다 출력 파티션을 하나씩 낸다. 그래서 인자가 8 이어도 출력 파티션은 넷이다. 그 출력을 repartition 으로 셔플하므로 맵 태스크 번호는 묶음 번호 0~3 이다. 묶음 0 은 a 를 받는 파티션 0 으로, 묶음 1 은 b 를 파티션 1 로, 묶음 2 는 c 와 d 를 파티션 2 와 0 으로, 묶음 3 은 e 와 f 를 파티션 0 과 1 로 보낸다. 스테이지 2 는 repartition 의 셔플을 읽으므로 묶지 않는다.

```
> PIPE rep = source agg | reduceByKey max 8 | repartition 3
> PROFILE rep
job 3 stages 3 tasks 11 rows 6
stage 0 tasks 4 source(events) -> shuffle 1
write records 10 bytes 110 spills 0 sizes 0,44,22,11,11,11,11,0
stage 1 tasks 4 reduceByKey reduceByKey -> shuffle 2
merge 0-1,2-2,3-4,5-7
write records 6 bytes 66 spills 0 sizes 33,22,11
stage 2 tasks 3 repartition -> result
```

[6.4] 조인 전략 바꾸기다. 임계값이 40 이면 late 의 두 입력은 추정 크기가 110 과 44 라 계획은 정렬 병합 조인이다. 셔플 뒤 왼쪽 입력의 실제 크기는 22 라서 조인은 왼쪽을 빌드 쪽으로 하는 브로드캐스트 조인으로 바뀐다. 오른쪽 입력의 크기 0, 11, 22, 11 을 묶으면 처음 세 파티션이 33 이 될 때까지 최소 크기에 못 미쳐 닫히지 못한다. 마지막 묶음인 파티션 3 은 크기 11 이 최소 크기보다 작아 앞 묶음에 합쳐지므로 묶음은 하나다. 적응 실행을 끄면 3단계의 정렬 병합 조인으로 돌아간다.

```
> PIPE late = source events | filter value > 8 | join tags 4
> SET broadcastThreshold 40
> PLAN late
stage 0 tasks 4 source(events) filter -> shuffle 2
stage 1 tasks 2 source(tags) -> shuffle 2
stage 2 tasks 4 join(sortmerge) -> result
> PROFILE late
job 4 stages 3 tasks 7 rows 2
stage 0 tasks 4 source(events) filter -> shuffle 2
write records 2 bytes 22 spills 0 sizes 0,11,11,0
stage 1 tasks 2 source(tags) -> shuffle 2
write records 4 bytes 44 spills 0 sizes 0,11,22,11
stage 2 tasks 1 join(broadcast-left) -> result
merge 0-3
> RUN late
job 5 stages 3 tasks 7 rows 2
a (9,100)
b (10,200)
> SET adaptive off
> PROFILE late
job 6 stages 3 tasks 10 rows 2
stage 0 tasks 4 source(events) filter -> shuffle 2
write records 2 bytes 22 spills 0 sizes 0,11,11,0
stage 1 tasks 2 source(tags) -> shuffle 2
write records 4 bytes 44 spills 0 sizes 0,11,22,11
stage 2 tasks 4 join(sortmerge) -> result
```

[6.5] 쏠림과 병합이다. 3단계 6.6 의 hot 은 왼쪽 입력의 파티션 1 이 쏠려 조각 셋으로 쪼개지므로 적응 실행이 켜져 있어도 그 스테이지는 묶지 않는다. 권장 크기를 100 으로 올리면 쪼개지 않고 두 입력의 크기를 번호끼리 더한 11, 88, 22, 22 로 묶는다. 최소 크기 30 에서 파티션 0 은 닫히지 못하고 파티션 1 과 묶여 99 가 된다. 파티션 2 를 더하면 100 을 넘으므로 새 묶음이 시작되고 파티션 3 이 거기에 들어간다. both 는 두 reduceByKey 가 읽는 셔플을 함께 묶는다. 크기 합은 11, 66, 22, 22 이고 묶음은 하나다. 그래서 두 reduceByKey 와 조인은 인자가 4 여도 파티션 하나를 낸다. 뒤의 coalesce 2 도 파티션 하나를 그대로 둔다. 이 블록은 새 실행이다.

```
> DATA views 3
> 0 a 1
> 0 a 2
> 0 b 3
> 1 a 4
> 1 a 5
> 1 c 6
> 2 a 7
> 2 d 8
> 2 e 9
> END
> DATA items 2
> 0 a 10
> 0 b 20
> 1 c 30
> 1 a 40
> END
> SET broadcastThreshold -1
> SET skewThreshold 20
> SET skewFactor 2
> SET advisorySize 25
> SET adaptive on
> SET minPartitionSize 30
> PIPE hot = source views | join items 4
> PROFILE hot
job 0 stages 3 tasks 11 rows 12
stage 0 tasks 3 source(views) -> shuffle 2
write records 9 bytes 99 spills 0 sizes 11,66,11,11
stage 1 tasks 2 source(items) -> shuffle 2
write records 4 bytes 44 spills 0 sizes 0,22,11,11
stage 2 tasks 6 join(sortmerge) -> result
skew left 1 3
> SET advisorySize 100
> PROFILE hot
job 1 stages 3 tasks 7 rows 12
stage 0 tasks 3 source(views) -> shuffle 2
write records 9 bytes 99 spills 0 sizes 11,66,11,11
stage 1 tasks 2 source(items) -> shuffle 2
write records 4 bytes 44 spills 0 sizes 0,22,11,11
stage 2 tasks 2 join(sortmerge) -> result
merge 0-1,2-3
> PIPE ra = source views | reduceByKey sum 4
> PIPE rb = source items | reduceByKey max 4
> PIPE both = source ra | join rb 4 | map value sum | coalesce 2
> PROFILE both
job 2 stages 3 tasks 6 rows 3
stage 0 tasks 3 source(views) -> shuffle 2
write records 7 bytes 77 spills 0 sizes 11,44,11,11
stage 1 tasks 2 source(items) -> shuffle 2
write records 4 bytes 44 spills 0 sizes 0,22,11,11
stage 2 tasks 1 reduceByKey reduceByKey join(sortmerge) map coalesce -> result
merge 0-3
> RUN both
job 3 stages 3 tasks 6 rows 3
a 59
b 23
c 36
```

[6.6] 오류다. 이 블록은 6.5 에 이어 실행한 것이다.

```
> SET adaptive yes
ERR SYNTAX
> SET adaptive ON
ERR SYNTAX
> SET minPartitionSize 0
ERR SYNTAX
> SET parallelism 4
ERR UNKNOWN_SETTING
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 3단계와 같다.

[7.2] 4단계 검사 입력에서 크기와 바이트 수는 64비트 정수를 넘지 않는다.

[7.3] 대규모 테스트는 파티션 16개짜리 사실 데이터셋에 행 3,000,000개, 파티션 8개짜리 차원 데이터셋에 행 300,000개를 DATA 블록으로 넣는다. 입력은 모두 50MB 안팎이다. 서로 다른 키는 200,000개 안팎이다. 적응 실행을 켜고 권장 크기와 최소 크기와 브로드캐스트 임계값을 바꿔 가며 reduceByKey, groupByKey, map, filter, 조인, repartition, coalesce 를 섞은 파이프라인에 RUN 과 PROFILE 을 모두 12개 안팎 실행한다. 조인 가운데 일부는 실행 중에 브로드캐스트 조인으로 바뀐다. RUN 은 모두 개수를 20 이하로 준다. 제한 시간은 25초다. 규모와 제한 시간은 풀이가 태스크를 스레드 하나에서 차례로 실행한다고 보고 정했다. 스레드 하나로 행마다 객체를 만들고 변환과 셔플마다 행 목록을 새로 만들어 복사하는 풀이는 이 제한 시간을 넘긴다. 같은 풀이도 태스크를 여러 스레드에 나눠 실행하면 제한 시간 안에 들 수 있다. 이 단계의 목표는 행 하나를 처리하는 비용을 줄이는 것이므로 풀이는 태스크를 스레드 하나에서 실행한다. 엔진은 스레드 수를 검사하지 않는다.

[7.4] 4단계를 검사할 때 1단계부터 3단계까지의 테스트도 다시 실행한다.
