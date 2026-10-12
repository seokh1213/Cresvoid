# 4단계: 로그 압축

## 1. 개요

[1.1] 4단계는 키마다 최신 값만 남기는 로그 압축을 더한다. 압축 정책 토픽에서는 압축이 닫힌 세그먼트에 있는 같은 키의 옛 레코드를 지운다. 값이 없는 레코드인 툼스톤을 쓰면 그 키를 지울 수 있다. 압축은 TICK 이 끝날 때 더러운 비율에 따라 일어난다. 압축으로 레코드가 지워져도 남은 레코드의 오프셋은 바뀌지 않으므로 로그 중간에 오프셋이 빈다.

[1.2] 1단계부터 3단계까지의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 앞 단계 문서는 명령어가 TOMBSTONE, DIRTY 인 줄이나 1단계 5.4 의 CREATE 가 나오면 그 줄부터 입력 끝까지의 출력을 정하지 않았다. 이 문서는 그 출력을 정한다. 그런 입력에서는 압축 정책 토픽으로 가는 PRODUCE 와 BATCH 가 `ERR KEY_REQUIRED` 를 낼 수 있고(4.7) TICK 이 압축 조건을 적용한다(4.9). FETCH 와 POLL 은 툼스톤의 값 자리에 `(null)` 을 쓴다(4.6).

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `TOMBSTONE <토픽> <키>` | 키의 툼스톤을 쓴다 | 1줄 |
| `DIRTY <토픽> <파티션>` | 파티션의 깨끗한 부분과 더러운 부분을 보여 준다 | 1줄 |
| `CREATE <토픽> <파티션 수> [<설정>=<값>]...` | 압축 설정 세 가지를 더 받는다 | 없음 |

## 2. 배경 개념

[2.1] 압축 정책(`cleanup.policy=compact`)을 쓰는 토픽은 오래된 레코드를 시간이나 크기로 지우지 않고 키마다 적어도 마지막 값을 남긴다. 계좌 잔액이나 설정값처럼 키마다 지금 상태가 중요한 데이터를 토픽에 담을 때 이 정책을 쓴다. 그런 토픽을 처음부터 읽은 컨슈머는 키마다 최종 상태를 얻는다. Kafka 문서는 압축이 지키는 성질을 이렇게 적는다. 로그의 머리까지 따라잡은 컨슈머는 쓰인 레코드를 빠짐없이 보고 그 오프셋은 빈 자리 없이 이어진다. 레코드의 순서는 바뀌지 않는다. 레코드의 오프셋은 바뀌지 않는다. 로그를 처음부터 읽는 컨슈머는 키마다 적어도 최종 상태를 본다.

[2.2] 압축 토픽의 로그는 아직 압축하지 않은 머리와 이미 압축한 꼬리로 나뉜다. 꼬리를 깨끗한 부분이라 하고 머리에서 활성 세그먼트를 뺀 나머지를 더러운 부분이라 한다. 로그 클리너는 더러운 비율이 높은 로그를 골라 압축한다. 더러운 비율은 깨끗한 부분과 더러운 부분을 합한 크기에서 더러운 부분이 차지하는 비율이다. 활성 세그먼트는 압축하지 않으므로 이 비율에 들어가지 않는다. 그래서 분모는 활성 세그먼트까지 더하는 1단계 4.19 의 로그 크기와 다르다. 더러운 비율이 `min.cleanable.dirty.ratio`(기본 0.5)를 넘어야 압축 대상이 된다. 값을 높이면 압축이 드물어지고 한 번의 압축이 효율적이지만 중복 레코드가 차지하는 공간이 는다. 값을 낮추면 그 반대다.

[2.3] 로그 클리너는 더러운 부분의 키마다 마지막 오프셋을 요약한 맵을 만든 뒤 세그먼트를 처음부터 다시 쓰면서 맵에 더 뒤의 오프셋이 있는 레코드를 버린다. 다시 쓰면 작아질 세그먼트 여러 개는 하나로 합쳐 쓴다. 압축이 끝난 곳까지가 새 깨끗한 부분이 된다. 쓰고 있는 활성 세그먼트는 아무리 오래되어도 압축하지 않는다.

[2.4] 값이 없는 레코드를 툼스톤이라 한다. 툼스톤은 그 키를 지운다는 표시다. 로그 클리너는 툼스톤보다 앞선 같은 키의 레코드를 지우고 툼스톤 자체는 `delete.retention.ms`(기본 1일) 동안 남겨 컨슈머가 지워진 사실을 볼 수 있게 한다. KIP-534 부터는 로그 클리너가 툼스톤이 든 세그먼트를 처음 압축할 때 그 시각에 `delete.retention.ms` 를 더한 삭제 시각을 정해 두고 지금 시각이 삭제 시각 전이면 툼스톤을 남긴다. 그래서 툼스톤이 남는 기간은 툼스톤을 쓴 시각이 아니라 처음 압축된 시각부터 잰다. 삭제 시각이 지나도 압축이 다시 일어나야 툼스톤이 지워진다.

[2.5] 압축은 레코드를 지워도 오프셋을 다시 매기지 않는다. 그래서 압축한 꼬리에는 오프셋이 빈 자리가 생긴다. 지워진 오프셋도 로그 안의 올바른 위치다. 그 오프셋부터 읽으면 그 뒤에 남아 있는 첫 레코드부터 받는다. 압축은 로그 시작 오프셋도 바꾸지 않는다.

[2.6] 키가 없는 레코드는 압축할 수 없으므로 Kafka 는 압축 토픽에 키 없는 레코드를 쓰지 못하게 한다.

[2.7] 이 트랙은 Kafka 와 다르게 압축을 TICK 이 끝날 때 조건에 맞는 파티션마다 한 번씩 한다(4.9). 레코드가 압축되기 전에 머무는 최소 시간(`min.compaction.lag.ms`)은 다루지 않는다. 세그먼트를 합치는 규칙은 이 문서가 정한다(4.12).

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 따른다.

[3.2] TOMBSTONE 의 키 인자는 1단계 3.13 의 키이고 `-` 일 수 없다.

## 4. 명령 규칙

### 토픽 설정

[4.1] CREATE 는 아래 세 설정도 받는다. 설정 인자를 나누고 확인하는 규칙은 1단계 4.5 와 같다.

| 설정 | 값 | 기본값 |
|---|---|---|
| `cleanup.policy` | `delete` 나 `compact` | `delete` |
| `min.cleanable.dirty.ratio` | `0`, `1`, 또는 `0.` 뒤에 ASCII 숫자 한두 개(4.2) | 0.5 |
| `delete.retention.ms` | 0 이상 1,000,000,000,000,000 이하인 정수 | 86400000 |

[4.2] `min.cleanable.dirty.ratio` 의 값은 `^(0|1|0\.[0-9]{1,2})$` 에 맞는다. `[0-9]` 는 ASCII 숫자 `0`~`9` 만 가리키므로 다른 문자 체계의 숫자나 전각 숫자를 쓴 값은 형식에 맞지 않는다. 형식에 맞는 값은 백분율 정수 R 로 바꾸어 쓴다. `0` 은 0, `1` 은 100 이고 `0.` 뒤에 숫자가 하나면 그 숫자에 10 을 곱한 값, 둘이면 그 두 자리 수다. `0.5` 와 `0.50` 은 50 이고 `0.05` 는 5 다. `.5`, `0.555`, `1.0` 은 형식에 맞지 않는다.

[4.3] `cleanup.policy` 가 `compact` 인 토픽을 압축 정책 토픽, `delete` 인 토픽을 삭제 정책 토픽이라 한다. `cleanup.policy` 를 주지 않은 토픽은 삭제 정책 토픽이다. 압축 정책 토픽에는 1단계의 보관 규칙을 적용하지 않으므로 `retention.ms` 와 `retention.bytes` 를 주어도 쓰이지 않는다. 삭제 정책 토픽에서는 `min.cleanable.dirty.ratio` 와 `delete.retention.ms` 가 쓰이지 않는다. 쓰이지 않는 설정도 1단계 4.5 대로 확인하므로 값이 범위에 맞지 않으면 형식에 맞지 않는 인자다.

### 툼스톤

[4.4] 툼스톤은 키가 있고 값이 없는 레코드다. 툼스톤의 크기는 키의 바이트 수다.

[4.5] `TOMBSTONE <토픽> <키>` 는 그 키의 툼스톤을 덧붙이고 `partition=<파티션> offset=<오프셋>` 을 출력한다. 토픽이 없으면 `ERR UNKNOWN_TOPIC` 이다. 그다음 툼스톤의 크기가 `segment.bytes` 보다 크면 `ERR RECORD_TOO_LARGE` 다. 파티션은 키로 고른다(1단계 4.10). 덧붙이는 방법은 1단계 4.15 와 같다. TOMBSTONE 은 스티키 파티션과 누적 바이트를 바꾸지 않는다. 삭제 정책 토픽에도 툼스톤을 쓸 수 있고 그 툼스톤은 보통 레코드처럼 보관 규칙을 따른다.

[4.6] FETCH 와 POLL 은 툼스톤의 값 자리에 `(null)` 을 쓴다. 값은 괄호를 담을 수 없으므로 보통 레코드와 헷갈리지 않는다.

### 키 없는 메시지

[4.7] 압축 정책 토픽에 키 없는 메시지를 쓰는 PRODUCE 는 `ERR KEY_REQUIRED` 를 출력한다. 이 확인은 `ERR UNKNOWN_TOPIC` 확인 뒤, `ERR RECORD_TOO_LARGE` 확인 앞에 한다. 압축 정책 토픽으로 가는 BATCH 에 키 없는 레코드가 하나라도 있으면 `ERR KEY_REQUIRED` 다. 이 확인은 `ERR UNKNOWN_PARTITION` 확인 뒤, `ERR RECORD_TOO_LARGE` 확인 앞에 한다.

### 깨끗한 부분과 더러운 부분

[4.8] 압축 정책 토픽의 파티션마다 첫 더러운 오프셋이 있고 처음 값은 0 이다. PARTITIONS 로 생긴 파티션도 0 에서 시작한다. 닫힌 세그먼트 가운데 기준 오프셋이 첫 더러운 오프셋보다 작은 세그먼트를 깨끗한 세그먼트, 크거나 같은 세그먼트를 더러운 세그먼트라 한다. 활성 세그먼트는 어느 쪽도 아니다. 깨끗한 세그먼트의 크기를 모두 더한 값이 깨끗한 바이트 C 이고 더러운 세그먼트의 크기를 모두 더한 값이 더러운 바이트 D 다.

[4.9] TICK 은 시각을 움직이고 시간 초과를 처리한 뒤(3단계 4.20) 삭제 정책 토픽에는 1단계의 보관 규칙을, 압축 정책 토픽에는 압축 조건을 적용한다. 압축 정책 토픽의 파티션마다 C + D 가 0 보다 크고 100 × D 가 R × (C + D) 보다 크면 그 파티션을 지금 시각에 한 번 압축한다(4.10~4.13). 비교는 정수로 정확히 한다.

### 압축

[4.10] 압축은 먼저 키 맵을 만든다. 더러운 세그먼트에 있는 키마다 그 키를 가진 레코드 가운데 더러운 세그먼트에서 오프셋이 가장 큰 것의 오프셋을 맵에 둔다. 활성 세그먼트의 레코드는 키 맵에 쓰지 않는다.

[4.11] 키 맵을 만든 뒤 닫힌 세그먼트의 레코드마다 기준 오프셋 순서대로 남길지 정한다. 깨끗한 세그먼트와 더러운 세그먼트가 모두 대상이다. 키가 맵에 있고 레코드의 오프셋이 맵의 오프셋보다 작으면 지운다. 나머지 레코드는 남긴다. 다만 남길 레코드가 툼스톤이면 삭제 시각을 확인한다. 삭제 시각이 있으면 지금 시각이 삭제 시각 이상일 때 지우고 아니면 남긴다. 삭제 시각이 없으면 지금 시각에 그 토픽의 `delete.retention.ms` 를 더한 값을 삭제 시각으로 정하고 남긴다. 삭제 시각은 툼스톤이 지워질 때까지 바뀌지 않는다.

[4.12] 레코드를 지운 뒤 닫힌 세그먼트를 기준 오프셋 순서로 묶는다. 첫 세그먼트로 첫 묶음을 시작한다. 다음 세그먼트는 지금 묶음의 크기와 그 세그먼트의 크기를 더한 값이 `segment.bytes` 이하이면 지금 묶음에 더하고 아니면 그 세그먼트로 새 묶음을 시작한다. 크기는 레코드를 지운 뒤의 크기다. 묶음 하나는 세그먼트 하나가 된다. 그 세그먼트의 기준 오프셋은 묶음의 첫 세그먼트의 기준 오프셋이고 레코드는 묶음에 남은 레코드를 오프셋 순서대로 담는다.

[4.13] 압축이 끝나면 첫 더러운 오프셋은 활성 세그먼트의 기준 오프셋이 된다. 활성 세그먼트와 그 레코드는 바뀌지 않는다. 로그 시작 오프셋과 로그 끝 오프셋도 바뀌지 않는다(1단계 4.16). 압축은 2단계의 프로듀서 상태와 3단계의 커밋 오프셋을 바꾸지 않는다.

[4.14] 압축으로 지운 레코드는 더는 읽을 수 없다. FETCH 와 POLL 은 앞 단계 규칙대로 오프셋이 주어진 오프셋이나 위치 이상인 레코드를 받으므로 지워진 오프셋은 건너뛴다. 지워진 오프셋을 FETCH 해도 그 오프셋이 로그 시작 오프셋 이상이고 로그 끝 오프셋 이하이면 오류가 아니다. 압축으로 지워진 오프셋이 컨슈머의 위치나 커밋 오프셋이어도 로그 시작 오프셋 이상이면 위치를 `auto.offset.reset` 으로 다시 정하지 않는다(3단계 4.15).

### DIRTY

[4.15] `DIRTY <토픽> <파티션>` 은 `first_dirty=<첫 더러운 오프셋> clean_bytes=<C> dirty_bytes=<D>` 를 출력한다. 파티션은 0 이상 999 이하인 정수다. 토픽이 없으면 `ERR UNKNOWN_TOPIC`, 파티션 번호가 토픽의 파티션 수 이상이면 `ERR UNKNOWN_PARTITION`, 삭제 정책 토픽이면 `ERR NOT_COMPACTED` 다.

## 5. 오류 규칙

[5.1] 1단계의 5.1 과 5.5 를 그대로 따른다.

[5.2] 명령어가 1단계부터 3단계까지의 열아홉 명령과 TOMBSTONE, DIRTY 가운데 어느 것과도 정확히 같지 않으면 `ERR UNKNOWN_COMMAND` 를 출력한다.

[5.3] 인자 수가 맞지 않거나 인자가 형식에 맞지 않으면 `ERR BAD_ARGUMENT` 를 출력한다. TOMBSTONE 과 DIRTY 의 인자 수는 2 다. 토픽 인자는 1단계 3.12 의 이름이어야 하고 TOMBSTONE 의 키는 3.2 를 따라야 한다. CREATE 의 설정 인자는 1단계 4.4 와 이 문서 4.1 의 설정이어야 한다.

[5.4] 이 단계에서 새로 생기는 오류 줄은 아래와 같다.

| 오류 줄 | 뜻 |
|---|---|
| `ERR KEY_REQUIRED` | 압축 정책 토픽에 키 없는 레코드를 쓰려 함 |
| `ERR NOT_COMPACTED` | 삭제 정책 토픽에 DIRTY 를 실행함 |

## 6. 예시

[6.1] 예시의 표기는 1단계 6.1 과 같다.

[6.2] 기본 압축이다. TICK 전에는 닫힌 세그먼트 두 개가 모두 더러운 세그먼트이고 활성 세그먼트의 5바이트는 어느 쪽에도 들지 않는다. 키 맵은 a → 2, b → 1, c → 3 이므로 오프셋 0 만 지워진다. 오프셋 2 의 `a 3333` 은 활성 세그먼트에 더 새로운 `a 5555` 가 있어도 남는다. 남은 두 세그먼트는 5 + 10 = 15 바이트라 합치지 않는다. 오프셋 0 부터 읽으면 남아 있는 첫 레코드인 오프셋 1 부터 받는다. 컨슈머도 마찬가지다.

```
> CREATE users 1 cleanup.policy=compact segment.bytes=10
> PRODUCE users a 1111
partition=0 offset=0
> PRODUCE users b 2222
partition=0 offset=1
> PRODUCE users a 3333
partition=0 offset=2
> PRODUCE users c 4444
partition=0 offset=3
> PRODUCE users a 5555
partition=0 offset=4
> DIRTY users 0
first_dirty=0 clean_bytes=0 dirty_bytes=20
> TICK 1
> SEGMENTS users 0
base=0 records=1 bytes=5 max_time=0
base=2 records=2 bytes=10 max_time=0
base=4 records=1 bytes=5 max_time=0 active
> FETCH users 0 0 10
1 b 2222
2 a 3333
3 c 4444
4 a 5555
> FETCH users 0 0 1
1 b 2222
> DIRTY users 0
first_dirty=4 clean_bytes=15 dirty_bytes=0
> OFFSETS users 0
start=0 end=5
> GROUP g range users auto.offset.reset=earliest
> JOIN g c1
> POLL c1 2
users 0 1 b 2222
users 0 2 a 3333
> POLL c1 5
users 0 3 c 4444
users 0 4 a 5555
```

[6.3] 더러운 비율이다. 첫 TICK 에서는 더러운 비율이 12/12 라 압축하지만 지울 레코드가 없고 6 + 6 이 `segment.bytes` 6 을 넘어 합치지도 않는다. 둘째 TICK 에서는 100 × 6 = 600 이 50 × 18 = 900 보다 크지 않아 압축하지 않는다. 셋째 TICK 에서도 1200 과 1200 이 같아 압축하지 않는다. 넷째 TICK 에서 1800 이 1500 보다 커서 압축한다. 기준 오프셋 0 과 1 의 세그먼트는 비워지고 기준 오프셋 2 의 세그먼트와 한 묶음이 되므로 그 세그먼트의 기준 오프셋은 0 이다.

```
> CREATE k 1 cleanup.policy=compact segment.bytes=6
> PRODUCE k a 11111
partition=0 offset=0
> PRODUCE k b 22222
partition=0 offset=1
> PRODUCE k c 33333
partition=0 offset=2
> TICK 1
> PRODUCE k a 44444
partition=0 offset=3
> TICK 1
> DIRTY k 0
first_dirty=2 clean_bytes=12 dirty_bytes=6
> PRODUCE k b 55555
partition=0 offset=4
> TICK 1
> DIRTY k 0
first_dirty=2 clean_bytes=12 dirty_bytes=12
> PRODUCE k d 66666
partition=0 offset=5
> TICK 1
> SEGMENTS k 0
base=0 records=1 bytes=6 max_time=0
base=3 records=1 bytes=6 max_time=1
base=4 records=1 bytes=6 max_time=2
base=5 records=1 bytes=6 max_time=3 active
> FETCH k 0 0 10
2 c 33333
3 a 44444
4 b 55555
5 d 66666
> DIRTY k 0
first_dirty=5 clean_bytes=18 dirty_bytes=0
```

[6.4] 툼스톤의 삭제 시각이다. `min.cleanable.dirty.ratio` 가 0 이라 더러운 세그먼트가 있으면 TICK 마다 압축한다. 시각 500 의 첫 압축에서 시각 0 에 쓴 툼스톤은 삭제 시각 1500 을 받고 그보다 앞선 `x 111` 은 지워진다. 시각 1201 의 압축에서는 툼스톤을 쓴 지 1000 밀리초가 넘었지만 삭제 시각 전이라 툼스톤이 남는다. 시각 1601 에는 더러운 세그먼트가 없어 압축이 일어나지 않으므로 삭제 시각이 지났어도 툼스톤이 남는다. 시각 1602 의 압축에서 툼스톤이 지워지고 4 + 4 = 8 바이트가 된 앞의 두 세그먼트가 한 세그먼트로 합쳐진다.

```
> CREATE acct 1 cleanup.policy=compact segment.bytes=8 delete.retention.ms=1000 min.cleanable.dirty.ratio=0
> PRODUCE acct x 111
partition=0 offset=0
> PRODUCE acct y 222
partition=0 offset=1
> TOMBSTONE acct x
partition=0 offset=2
> PRODUCE acct z 333
partition=0 offset=3
> PRODUCE acct w 4444
partition=0 offset=4
> TICK 500
> FETCH acct 0 0 10
1 y 222
2 x (null)
3 z 333
4 w 4444
> TICK 700
> PRODUCE acct v 5555
partition=0 offset=5
> TICK 1
> FETCH acct 0 2 1
2 x (null)
> TICK 400
> FETCH acct 0 2 1
2 x (null)
> PRODUCE acct u 6666
partition=0 offset=6
> TICK 1
> FETCH acct 0 0 10
1 y 222
3 z 333
4 w 4444
5 v 5555
6 u 6666
> SEGMENTS acct 0
base=0 records=2 bytes=8 max_time=0
base=4 records=1 bytes=5 max_time=0
base=5 records=1 bytes=5 max_time=1200
base=6 records=1 bytes=5 max_time=1601 active
```

[6.5] 오류와 삭제 정책 토픽의 툼스톤이다. 삭제 정책 토픽에도 툼스톤을 쓸 수 있지만 DIRTY 는 압축 정책 토픽에만 쓴다. 압축 정책 토픽으로 가는 배치에 키 없는 레코드가 있으면 배치를 만들지 않는다.

```
> CREATE c 1 cleanup.policy=compact
> PRODUCE c - v
ERR KEY_REQUIRED
> CREATE d 1
> TOMBSTONE d k
partition=0 offset=0
> FETCH d 0 0 1
0 k (null)
> DIRTY d 0
ERR NOT_COMPACTED
> CREATE e 1 cleanup.policy=compacted
ERR BAD_ARGUMENT
> CREATE e 1 min.cleanable.dirty.ratio=0.555
ERR BAD_ARGUMENT
> CREATE e 1 min.cleanable.dirty.ratio=.5
ERR BAD_ARGUMENT
> TOMBSTONE c -
ERR BAD_ARGUMENT
> DIRTY c 1
ERR UNKNOWN_PARTITION
> INIT p
pid=1
> BATCH p b c 0 k1 v1 - v2
ERR KEY_REQUIRED
> DIRTY c 0
first_dirty=0 clean_bytes=0 dirty_bytes=0
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계 7.1~7.5 와 7.7 과 같다.

[7.2] 대규모 테스트는 압축 정책 토픽 4개(파티션 합계 8개)에 메시지 1,000,000개를 쓰면서 TICK 200,000개, FETCH 400,000개, DIRTY 50,000개, SEGMENTS 500개 안팎을 섞은 입력이다. 메시지의 10% 안팎은 툼스톤이고 FETCH 의 최대 개수는 2 이하다. 파티션마다 키가 수만 개라서 압축한 뒤에도 파티션마다 살아 있는 레코드가 수만 개이고 압축은 파티션마다 여러 번 일어난다. 세그먼트 크기와 압축 설정은 파티션마다 세그먼트가 200개 이하로 남도록 정한다. 삭제 정책 토픽, BATCH 와 DELIVER, 컨슈머 그룹도 조금씩 섞인다. 표준 출력은 모두 합쳐 48MB 이하다. 제한 시간은 30초다. TICK 마다 레코드를 모두 훑어 더러운 비율을 다시 계산하는 풀이와 FETCH 마다 첫 레코드부터 차례로 찾아가는 풀이는 이 제한 시간을 넘기도록 규모를 정했다. FETCH 마다 첫 레코드부터 찾아가면 레코드를 모두 합쳐 200억 번 넘게 지난다.

[7.3] 4단계를 검사할 때 1단계부터 3단계까지의 테스트도 다시 실행한다.
