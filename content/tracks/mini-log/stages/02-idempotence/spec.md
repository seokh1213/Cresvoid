# 2단계: 멱등 프로듀서

## 1. 개요

[1.1] 2단계는 프로듀서 ID 와 파티션별 시퀀스 번호로 같은 배치를 여러 번 보내도 한 번만 덧붙이는 멱등 프로듀서를 더한다. 프로그램은 프로듀서가 배치를 만드는 일과 그 배치가 브로커에 도착하는 일을 서로 다른 명령으로 받는다. 배치를 만들고 보내지 않으면 요청이 가는 길에 사라진 상황이다. 같은 배치를 두 번 보내는 것은 응답이 사라져 프로듀서가 다시 보낸 상황에 해당한다. 나중에 만든 배치를 먼저 보내면 요청의 도착 순서가 바뀐 셈이다.

[1.2] 1단계의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 5.2 는 명령어가 INIT, BATCH, DELIVER, PRODUCERS 인 줄이 나오면 그 줄부터 입력 끝까지의 출력을 정하지 않았다. 이 문서는 그 출력을 정한다.

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `INIT <프로듀서>` | 프로듀서를 시작하거나 다시 시작하고 새 프로듀서 ID 를 받는다 | 1줄 |
| `BATCH <프로듀서> <배치> <토픽> <파티션> <키> <값> [<키> <값>]...` | 배치를 만들고 시퀀스 번호를 매긴다 | 1줄 |
| `DELIVER <배치>` | 배치를 브로커에 전달한다 | 1줄 |
| `PRODUCERS <토픽> <파티션>` | 파티션에 남은 프로듀서 상태를 보여 준다 | 1줄 이상 |

## 2. 배경 개념

[2.1] 프로듀서는 요청을 보낸 뒤 브로커의 응답을 기다린다. 리더가 멈추거나 응답이 가는 길에 사라지면 프로듀서는 메시지가 적히기 전에 실패했는지 적힌 뒤 응답만 사라졌는지 알 수 없다. 다시 보내면 유실은 막지만 앞의 쓰기가 성공했었다면 같은 메시지가 로그에 두 번 적힌다. 1단계의 PRODUCE 를 같은 메시지로 두 번 실행하면 생기는 결과가 이것이다.

[2.2] 멱등 프로듀서는 이 중복을 브로커가 걸러 내게 한다. 브로커는 프로듀서마다 프로듀서 ID 를 주고 프로듀서는 파티션마다 레코드에 0 부터 시퀀스 번호를 붙인다. 레코드를 여러 개 담은 배치는 첫 레코드와 마지막 레코드의 시퀀스 번호로 범위를 나타낸다. 첫 시퀀스 번호가 10 이고 레코드가 3개인 배치는 10 부터 12 까지를 차지하고 그 파티션의 다음 배치는 13 에서 시작한다. Kafka 는 3.0 부터 멱등 프로듀서를 기본으로 켠다.

[2.3] 브로커는 프로듀서 ID 와 파티션의 조합마다 마지막으로 덧붙인 시퀀스 번호와 최근에 덧붙인 배치 몇 개의 시퀀스 범위를 기억한다. Kafka 는 최근 배치 5개를 기억하고 멱등 프로듀서가 한 연결에서 응답을 기다리지 않고 보내는 요청 수(`max.in.flight.requests.per.connection`)를 5 이하로 제한한다. 기억한 배치와 시퀀스 범위가 같은 배치가 다시 오면 브로커는 덧붙이지 않고 처음 덧붙인 오프셋으로 응답한다. 첫 시퀀스 번호가 마지막 시퀀스 번호보다 1 큰 배치만 덧붙이고 번호가 건너뛴 배치는 순서 오류(OutOfOrderSequence)로 거절한다. 앞 배치가 실패해 다시 보내는 사이에 뒤 배치가 먼저 도착해도 뒤 배치는 거절되므로 한 파티션 안의 순서가 지켜진다.

[2.4] 프로듀서 ID 는 프로듀서 프로세스의 메모리에만 있다. 프로듀서를 다시 시작하면 새 프로듀서 ID 를 받고 시퀀스 번호도 0 부터 다시 매긴다. 브로커는 새 ID 와 옛 ID 를 이을 방법이 없으므로 다시 시작한 프로듀서가 같은 내용을 다시 보내면 걸러 내지 못한다. 멱등 프로듀서가 거를 수 있는 것은 한 프로듀서 ID 가 스스로 되풀이한 재전송뿐이다. Kafka 의 트랜잭션 프로듀서는 `transactional.id` 와 에포크로 다시 시작한 뒤에도 정체성을 잇고 옛 프로세스를 막지만 이 트랙은 트랜잭션을 다루지 않는다.

[2.5] 브로커는 배치를 한 단위로 덧붙인다. 한 배치의 레코드는 한 세그먼트에 모두 들어가고 배치가 세그먼트 크기보다 크면 Kafka 는 그 배치를 받지 않는다.

[2.6] 이 트랙이 Kafka 와 다른 점은 둘이다. 브로커는 상태가 없는 프로듀서 ID 의 첫 배치가 시퀀스 번호 0 으로 시작할 때만 덧붙인다(4.10). 배치의 크기가 `segment.bytes` 를 넘는지는 배치를 만들 때 확인한다(4.4).

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 따른다.

[3.2] 프로듀서 이름과 배치 이름은 1단계 3.12 의 이름이다.

## 4. 명령 규칙

### 프로듀서

[4.1] 프로듀서 ID 는 프로그램 전체에서 1 부터 차례로 나누어 준다. INIT 이 성공할 때마다 아직 쓰지 않은 다음 번호를 준다.

[4.2] `INIT <프로듀서>` 는 이름이 처음 나온 프로듀서를 시작하거나 이미 시작한 프로듀서를 다시 시작한다. 어느 쪽이든 프로듀서는 새 프로듀서 ID 를 받고 `pid=<프로듀서 ID>` 를 출력한다. 다시 시작한 프로듀서는 옛 프로듀서 ID 를 버리고 모든 파티션의 다음 시퀀스 번호를 0 으로 되돌린다. 다시 시작하기 전에 만든 배치는 바뀌지 않는다(4.6).

### BATCH

[4.3] `BATCH <프로듀서> <배치> <토픽> <파티션> <키> <값> [<키> <값>]...` 은 주어진 파티션에 보낼 배치를 만든다. 다섯째 인자부터 두 개씩 묶어 레코드 하나의 키와 값으로 읽는다. 인자에 적힌 순서가 배치 안의 레코드 순서다. 레코드는 1개 이상 100개 이하다. 키와 값은 1단계 3.13 을 따르고 키 자리의 `-` 는 키가 없다는 뜻이다. 파티션은 0 이상 999 이하인 정수다. BATCH 는 배치를 브로커에 보내지 않는다.

[4.4] BATCH 는 아래 순서로 확인한다. INIT 한 적이 없는 프로듀서이면 `ERR UNKNOWN_PRODUCER` 다. 어느 프로듀서든 같은 이름의 배치를 이미 만들었으면 `ERR BATCH_EXISTS` 다. 토픽이 없으면 `ERR UNKNOWN_TOPIC` 이고 파티션 번호가 토픽의 파티션 수 이상이면 `ERR UNKNOWN_PARTITION` 이다. 배치의 크기, 곧 레코드 크기(1단계 4.8)의 합이 그 토픽의 `segment.bytes` 보다 크면 `ERR RECORD_TOO_LARGE` 다.

[4.5] 프로듀서는 토픽의 파티션마다 다음 시퀀스 번호를 가지고 처음 값은 0 이다. 레코드가 n 개인 배치를 만들면 배치의 첫 시퀀스 번호는 그 파티션의 다음 시퀀스 번호이고 마지막 시퀀스 번호는 첫 시퀀스 번호 + n − 1 이다. 그 파티션의 다음 시퀀스 번호는 마지막 시퀀스 번호 + 1 이 된다. 배치는 프로듀서의 지금 프로듀서 ID 를 받는다. BATCH 는 `pid=<프로듀서 ID> seq=<첫 시퀀스 번호>-<마지막 시퀀스 번호>` 를 출력한다.

[4.6] 만든 배치의 프로듀서 ID, 시퀀스 번호, 토픽, 파티션, 레코드는 프로그램이 끝날 때까지 바뀌지 않는다. 프로듀서를 다시 시작해도 마찬가지다. 배치는 몇 번이든 전달할 수 있다.

### 브로커의 프로듀서 상태

[4.7] 브로커는 파티션마다 프로듀서 ID 별 상태를 가진다. 상태는 마지막 시퀀스 번호와 기억한 배치 목록이다. 기억한 배치마다 첫 시퀀스 번호, 마지막 시퀀스 번호, 첫 오프셋, 마지막 오프셋을 둔다. 그 파티션에 배치를 덧붙인 적이 없는 프로듀서 ID 는 상태가 없다. PRODUCE 는 프로듀서 상태를 바꾸지 않는다. 보관 규칙이 세그먼트를 지워도 프로듀서 상태는 남는다.

### DELIVER

[4.8] `DELIVER <배치>` 는 배치를 브로커에 전달한다. 만든 적이 없는 배치이면 `ERR UNKNOWN_BATCH` 다. 브로커는 배치의 프로듀서 ID 로 그 파티션의 상태를 찾아 4.9~4.11 의 순서로 처리한다.

[4.9] 기억한 배치 가운데 첫 시퀀스 번호와 마지막 시퀀스 번호가 모두 전달된 배치와 같은 것이 있으면 중복이다. 브로커는 배치를 덧붙이지 않고 상태도 바꾸지 않으며 `duplicate <첫 오프셋>-<마지막 오프셋>` 을 출력한다. 오프셋은 기억한 배치의 것이다.

[4.10] 중복이 아니면 기대 번호를 정한다. 상태가 있으면 기대 번호는 마지막 시퀀스 번호 + 1 이고 상태가 없으면 0 이다. 배치의 첫 시퀀스 번호가 기대 번호와 다르면 `ERR OUT_OF_ORDER_SEQUENCE expected=<기대 번호>` 를 출력한다. 목록에서 밀려난 옛 배치도 여기에 해당한다.

[4.11] 첫 시퀀스 번호가 기대 번호와 같으면 배치를 덧붙인다. 배치의 크기를 s 로 보고 1단계 4.15 의 롤 규칙을 한 번 적용한 뒤 레코드를 배치 안의 순서대로 활성 세그먼트에 덧붙인다. 레코드마다 로그 끝 오프셋을 오프셋으로 받고 시각은 지금 시각이다. 키 없는 레코드를 덧붙여도 그 토픽의 스티키 파티션과 누적 바이트(1단계 4.11~4.13)는 바뀌지 않는다. 그다음 상태의 마지막 시퀀스 번호를 배치의 마지막 시퀀스 번호로 바꾸고 배치를 기억한 배치 목록 끝에 더한다. 기억한 배치가 5개를 넘으면 가장 먼저 더한 배치를 목록에서 지운다. 상태가 없었으면 이때 상태가 생긴다. DELIVER 는 `appended <첫 오프셋>-<마지막 오프셋>` 을 출력한다.

### PRODUCERS

[4.12] `PRODUCERS <토픽> <파티션>` 은 그 파티션에 상태가 있는 프로듀서 ID 마다 한 줄씩 프로듀서 ID 가 작은 것부터 `pid=<프로듀서 ID> last_seq=<마지막 시퀀스 번호> cached=<범위>[,<범위>]...` 를 출력한다. 범위는 기억한 배치의 `<첫 시퀀스 번호>-<마지막 시퀀스 번호>` 이고 먼저 더한 배치부터 쉼표로 잇는다. 상태가 있는 프로듀서 ID 가 없으면 `(none)` 한 줄을 출력한다. 파티션 인자와 오류는 1단계 4.25 와 같다.

## 5. 오류 규칙

[5.1] 1단계의 5.1 과 5.5 를 그대로 따른다.

[5.2] 명령어가 1단계의 일곱 명령과 INIT, BATCH, DELIVER, PRODUCERS 가운데 어느 것과도 정확히 같지 않으면 `ERR UNKNOWN_COMMAND` 를 출력한다. 다만 GROUP, JOIN, LEAVE, CRASH, POLL, COMMIT, ASSIGNMENT, COMMITTED 는 3단계에서, TOMBSTONE, DIRTY 는 4단계에서 명령이 되므로 명령어가 이 열 가지 가운데 하나인 줄이 나오면 이 문서는 그 줄부터 입력 끝까지의 출력을 정하지 않는다.

[5.3] 인자 수가 맞지 않거나 인자가 형식에 맞지 않으면 `ERR BAD_ARGUMENT` 를 출력한다. 인자 수는 INIT 과 DELIVER 가 1 이고 PRODUCERS 가 2 다. BATCH 는 6 이상 204 이하의 짝수다. 프로듀서, 배치, 토픽 인자는 1단계 3.12 의 이름이어야 한다.

[5.4] 1단계 5.4 의 CREATE 가 나오면 이 문서도 그 줄부터 입력 끝까지의 출력을 정하지 않는다.

[5.5] 이 단계에서 새로 생기는 오류 줄은 아래와 같다.

| 오류 줄 | 뜻 |
|---|---|
| `ERR UNKNOWN_PRODUCER` | INIT 한 적이 없는 프로듀서 |
| `ERR BATCH_EXISTS` | 같은 이름의 배치를 이미 만들었음 |
| `ERR UNKNOWN_BATCH` | 만든 적이 없는 배치 |
| `ERR OUT_OF_ORDER_SEQUENCE expected=<기대 번호>` | 첫 시퀀스 번호가 기대 번호와 다름 |

## 6. 예시

[6.1] 예시의 표기는 1단계 6.1 과 같다.

[6.2] 기본 동작이다. 시퀀스 번호는 파티션마다 따로 매기고 레코드 수만큼 늘어난다. 같은 배치를 다시 보내면 처음 덧붙인 오프셋으로 중복을 알린다. 파티션 1 의 배치는 파티션 0 의 순서와 상관없이 덧붙는다.

```
> CREATE pay 2
> INIT p
pid=1
> BATCH p b1 pay 0 k1 v1 k2 v2
pid=1 seq=0-1
> BATCH p b2 pay 0 k3 v3
pid=1 seq=2-2
> BATCH p b3 pay 1 - x
pid=1 seq=0-0
> DELIVER b1
appended 0-1
> DELIVER b1
duplicate 0-1
> DELIVER b3
appended 0-0
> DELIVER b2
appended 2-2
> FETCH pay 0 0 10
0 k1 v1
1 k2 v2
2 k3 v3
> PRODUCERS pay 0
pid=1 last_seq=2 cached=0-1,2-2
> PRODUCERS pay 1
pid=1 last_seq=0 cached=0-0
```

[6.3] 도착 순서가 바뀐 배치다. 배치 b 는 시퀀스 1 에서 시작하는데 브로커는 아직 0 을 기대하므로 거절한다. a 가 도착한 뒤에도 c 는 1 을 기대하는 브로커에게 거절되고 b 를 다시 보낸 뒤에야 덧붙는다. 로그의 순서는 배치를 만든 순서와 같다.

```
> CREATE q 1
> INIT w
pid=1
> BATCH w a q 0 - m1
pid=1 seq=0-0
> BATCH w b q 0 - m2 - m3
pid=1 seq=1-2
> BATCH w c q 0 - m4
pid=1 seq=3-3
> DELIVER b
ERR OUT_OF_ORDER_SEQUENCE expected=0
> DELIVER a
appended 0-0
> DELIVER c
ERR OUT_OF_ORDER_SEQUENCE expected=1
> DELIVER b
appended 1-2
> DELIVER c
appended 3-3
> DELIVER a
duplicate 0-0
> FETCH q 0 0 10
0 - m1
1 - m2
2 - m3
3 - m4
```

[6.4] 기억한 배치 목록에는 5개까지 들어간다. b5 를 덧붙이면 b0 이 목록에서 밀려난다. b1 은 아직 목록에 있으므로 중복이지만 b0 은 목록에 없고 시퀀스 0 이 기대 번호 6 과 달라 순서 오류다.

```
> CREATE r 1
> INIT p
pid=1
> BATCH p b0 r 0 - v0
pid=1 seq=0-0
> BATCH p b1 r 0 - v1
pid=1 seq=1-1
> BATCH p b2 r 0 - v2
pid=1 seq=2-2
> BATCH p b3 r 0 - v3
pid=1 seq=3-3
> BATCH p b4 r 0 - v4
pid=1 seq=4-4
> BATCH p b5 r 0 - v5
pid=1 seq=5-5
> DELIVER b0
appended 0-0
> DELIVER b1
appended 1-1
> DELIVER b2
appended 2-2
> DELIVER b3
appended 3-3
> DELIVER b4
appended 4-4
> DELIVER b5
appended 5-5
> DELIVER b1
duplicate 1-1
> DELIVER b0
ERR OUT_OF_ORDER_SEQUENCE expected=6
> PRODUCERS r 0
pid=1 last_seq=5 cached=1-1,2-2,3-3,4-4,5-5
```

[6.5] 프로듀서를 다시 시작한다. 애플리케이션이 다시 시작한 뒤 `order-7` 을 다시 보내면 새 프로듀서 ID 2 와 시퀀스 0 이 붙어 걸러지지 않는다. 다시 시작하기 전에 만든 m2 는 옛 프로듀서 ID 1 의 상태에서 기대 번호 1 과 맞아 덧붙는다.

```
> CREATE s 1
> INIT app
pid=1
> BATCH app m1 s 0 order-7 paid
pid=1 seq=0-0
> DELIVER m1
appended 0-0
> BATCH app m2 s 0 order-8 paid
pid=1 seq=1-1
> INIT app
pid=2
> BATCH app m3 s 0 order-7 paid
pid=2 seq=0-0
> DELIVER m3
appended 1-1
> DELIVER m2
appended 2-2
> DELIVER m1
duplicate 0-0
> PRODUCERS s 0
pid=1 last_seq=1 cached=0-0,1-1
pid=2 last_seq=0 cached=0-0
> FETCH s 0 0 10
0 order-7 paid
1 order-7 paid
2 order-8 paid
```

[6.6] 배치는 한 단위로 덧붙는다. 활성 세그먼트에 8바이트가 있으므로 5바이트 배치를 더하면 13 이 되어 롤한 뒤 두 레코드를 모두 새 세그먼트에 덧붙인다. 첫 레코드 하나만 보면 9바이트라 들어갈 수 있었다. 11바이트 배치는 만들 수 없고 실패한 BATCH 는 배치 이름도 시퀀스 번호도 쓰지 않는다.

```
> CREATE z 1 segment.bytes=10
> INIT p
pid=1
> PRODUCE z - abcdefgh
partition=0 offset=0
> BATCH p b z 0 - x - yyyy
pid=1 seq=0-1
> DELIVER b
appended 1-2
> SEGMENTS z 0
base=0 records=1 bytes=8 max_time=0
base=1 records=2 bytes=5 max_time=0 active
> BATCH p c z 0 - 123456 - 12345
ERR RECORD_TOO_LARGE
> BATCH p c z 0 - 12345
pid=1 seq=2-2
> DELIVER c
appended 3-3
> SEGMENTS z 0
base=0 records=1 bytes=8 max_time=0
base=1 records=3 bytes=10 max_time=0 active
```

[6.7] 오류다. 인자가 5개인 BATCH 는 마지막 레코드의 값이 없고 4개인 BATCH 는 레코드가 없다.

```
> CREATE pay 2
> BATCH nobody b1 pay 0 - x
ERR UNKNOWN_PRODUCER
> INIT p
pid=1
> BATCH p b1 pay 0 - x
pid=1 seq=0-0
> BATCH p b1 pay 0 - y
ERR BATCH_EXISTS
> BATCH p b2 nope 0 - x
ERR UNKNOWN_TOPIC
> BATCH p b2 pay 2 - x
ERR UNKNOWN_PARTITION
> BATCH p b2 pay 0 -
ERR BAD_ARGUMENT
> BATCH p b2 pay 0
ERR BAD_ARGUMENT
> DELIVER zz
ERR UNKNOWN_BATCH
> PRODUCERS pay 1
(none)
> DELIVER b1
appended 0-0
> PRODUCERS pay 0
pid=1 last_seq=0 cached=0-0
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계 7.1~7.5 와 7.7 과 같다.

[7.2] 대규모 테스트는 프로듀서 4개가 토픽 2개(파티션 합계 4개)에 배치 800,000개(메시지 2,000,000개 안팎)를 만들면서 DELIVER 1,200,000개를 섞은 입력이다. DELIVER 의 일부는 같은 배치를 다시 보내거나 뒤 배치를 앞 배치보다 먼저 보낸다. 끝내 전달하지 않는 배치도 있다. INIT 으로 프로듀서를 다시 시작하는 일과 옛 프로듀서 ID 의 배치가 늦게 도착하는 일도 몇 번씩 나온다. 프로듀서 ID 하나가 파티션 하나에 덧붙이는 배치는 수만 개다. PRODUCE, TICK, FETCH, OFFSETS, PRODUCERS 도 섞인다. 제한 시간은 30초다. DELIVER 마다 그 프로듀서 ID 가 그 파티션에 덧붙인 배치를 모두 훑거나 로그의 레코드를 훑어 중복을 찾는 풀이는 이 제한 시간을 넘기도록 규모를 정했다. 다만 4.9~4.11 에 따라 DELIVER 하나는 기억한 배치 5개만 확인해 처리할 수 있으므로 대규모 테스트는 주로 명령 수백만 개를 읽고 처리해 출력하는 속도를 잰다.

[7.3] 검사 입력에서 프로듀서 ID 하나가 파티션 하나에 매기는 시퀀스 번호는 1,000,000 보다 작다.

[7.4] 2단계 검사 입력에는 명령어가 5.2 의 열 가지 가운데 하나인 줄과 1단계 5.4 의 CREATE 가 나오지 않는다.

[7.5] 2단계를 검사할 때 1단계 테스트도 다시 실행한다. 3단계와 4단계를 검사할 때도 2단계 테스트를 다시 실행한다.
