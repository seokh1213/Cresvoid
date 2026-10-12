# 3단계: 컨슈머 그룹과 재조정

## 1. 개요

[1.1] 3단계는 컨슈머 그룹을 더한다. 그룹의 멤버들은 구독한 토픽의 파티션을 나누어 읽고 읽은 위치를 그룹에 커밋한다. 멤버가 들어오거나 나가거나 시간 초과에 걸리면 그룹은 재조정해 파티션을 다시 나눈다. 프로그램은 컨슈머가 레코드를 받는 일, 커밋하는 일, 프로세스가 멈추는 일을 서로 다른 명령으로 받는다. 레코드를 처리하는 명령은 따로 없고 처리는 출력에 나타나지 않는다. 입력에서는 컨슈머가 커밋한 뒤에 멈추었는지 커밋하기 전에 멈추었는지를 알 수 있다. 그 차이는 파티션을 넘겨받은 컨슈머가 받는 레코드로 출력에 나타난다. 레코드를 잃거나 두 번 처리하는 장애가 어느 순서에서 생기는지는 애플리케이션이 처리를 커밋 앞에 두는지 뒤에 두는지에 달려 있다(2.6).

[1.2] 1단계와 2단계의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 5.2 와 2단계 5.2 는 명령어가 GROUP, JOIN, LEAVE, CRASH, POLL, COMMIT, ASSIGNMENT, COMMITTED 인 줄이 나오면 그 줄부터 입력 끝까지의 출력을 정하지 않았다. 이 문서는 그 출력을 정한다. 그런 입력에서는 TICK 이 시간 초과로 멤버를 그룹에서 뺄 때 줄을 출력하고(4.20) PARTITIONS 가 그룹을 재조정한다(4.23). 그룹이 없는 입력에서는 두 명령의 출력과 동작이 1단계와 같다.

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `GROUP <그룹> <전략> <토픽 목록> [<설정>=<값>]...` | 그룹을 만든다 | 없음 |
| `JOIN <그룹> <컨슈머>` | 컨슈머를 시작해 그룹에 추가한다 | 없음 |
| `LEAVE <컨슈머>` | 컨슈머를 정상 종료한다 | 없음 |
| `CRASH <컨슈머>` | 컨슈머 프로세스를 멈춘다 | 없음 |
| `POLL <컨슈머> <최대 개수>` | 배정된 파티션에서 레코드를 받는다 | 1줄 이상 |
| `COMMIT <컨슈머>` | 컨슈머의 위치를 그룹에 커밋한다 | 없음 |
| `ASSIGNMENT <그룹>` | 세대와 멤버별 배정을 보여 준다 | 1줄 이상 |
| `COMMITTED <그룹>` | 그룹의 커밋 오프셋을 보여 준다 | 1줄 이상 |

## 2. 배경 개념

[2.1] 같은 그룹 ID 를 쓰는 컨슈머들은 컨슈머 그룹을 이루어 구독한 토픽의 파티션을 나누어 읽는다. 한 그룹 안에서 한 파티션은 한 순간에 한 멤버에게만 배정된다. 그룹이 다르면 서로 상관없이 같은 레코드를 각자 모두 읽는다. 브로커 가운데 하나가 그룹 코디네이터를 맡아 멤버 구성과 세대를 관리한다. Kafka 의 기존 그룹 규약(classic)에서는 멤버 하나가 리더가 되어 `partition.assignment.strategy` 가 정한 배정 전략으로 배정을 계산한다.

[2.2] 멤버가 들어오거나 나가거나 실패하면 그룹은 재조정한다. 구독한 토픽의 파티션 수가 바뀌어도 재조정한다. 범위 배정이나 순환 배정 같은 전략을 쓰는 즉시(eager) 재조정에서는 모든 멤버가 맡던 파티션을 모두 반납하고 다시 가입해 새 배정을 받는다. 재조정할 때마다 그룹의 세대 번호가 1 늘어난다. 컨슈머는 커밋할 때 자기가 아는 세대를 함께 보내고 코디네이터는 세대가 낡은 커밋을 거절한다(ILLEGAL_GENERATION). 그룹에서 이미 빠진 컨슈머의 커밋도 거절한다(UNKNOWN_MEMBER_ID). Kafka 4.0 은 브로커가 배정을 점진적으로 계산하는 새 규약(KIP-848)을 정식 기능으로 내놓았지만 이 트랙은 기존 규약의 즉시 재조정을 흉내 낸다.

[2.3] 코디네이터는 두 시간 초과로 실패를 알아챈다. 컨슈머의 백그라운드 스레드는 하트비트를 주기적으로 보내고 코디네이터는 `session.timeout.ms`(기본 45초) 동안 하트비트를 받지 못한 멤버를 그룹에서 뺀다. 프로세스가 멈추면 하트비트도 멈추므로 코디네이터는 세션 시간 초과로 그 컨슈머를 그룹에서 뺀다. 애플리케이션이 `max.poll.interval.ms`(기본 5분) 안에 poll 을 다시 호출하지 않으면 컨슈머는 자신이 실패했다고 보고 그룹을 떠난다. 처리가 오래 걸리는 동안에도 하트비트는 계속 전송되므로 이 시간 초과가 따로 있다. 그렇게 떠난 컨슈머는 다음 poll 에서 다시 가입한다. 이 트랙에서 멈춘 프로세스는 세션 시간 초과만, 실행 중인 프로세스는 poll 간격 초과만 겪는다(4.19).

[2.4] 범위 배정(RangeAssignor)은 토픽마다 따로 나눈다. 멤버를 정렬하고 파티션을 번호 순서로 늘어놓은 뒤 파티션 수를 멤버 수로 나눈 몫만큼 앞 멤버부터 연속으로 주고 나머지 파티션은 앞 멤버부터 하나씩 더 준다. 토픽이 여럿이면 매번 같은 앞 멤버가 하나씩 더 받으므로 치우침이 쌓인다. 순환 배정(RoundRobinAssignor)은 모든 토픽의 파티션을 한 줄로 늘어놓고 멤버에게 차례로 돌아가며 준다. Kafka 는 코디네이터가 준 멤버 ID 로 멤버를 정렬하지만 이 트랙은 컨슈머 이름으로 정렬한다.

[2.5] 컨슈머는 파티션마다 다음에 읽을 오프셋을 그룹에 커밋한다. 파티션이 다른 멤버에게 넘어가면 그 멤버는 커밋 오프셋부터 읽는다. 커밋 오프셋이 없거나 로그 시작 오프셋보다 작아 이미 지워진 오프셋이면 `auto.offset.reset` 이 시작 위치를 정한다. `earliest` 는 로그 시작 오프셋, `latest`(기본값)는 로그 끝 오프셋이다. 그래서 `latest` 를 쓰는 새 그룹은 이미 쓰인 레코드를 읽지 않는다. 즉시 재조정에서는 파티션을 반납할 때 컨슈머의 위치도 버리므로 같은 파티션을 다시 받은 멤버도 커밋 오프셋부터 다시 읽는다.

[2.6] 처리하기 전에 커밋하면 커밋과 처리 사이에 멈추었을 때 그 레코드는 처리되지 않으므로 최대 한 번이 된다. 처리한 뒤에 커밋하면 처리와 커밋 사이에 멈추었을 때 넘겨받은 멤버가 그 레코드를 다시 처리하므로 최소 한 번이 된다. 이 트랙의 입력에서 앞의 장애는 POLL 뒤에 COMMIT 과 CRASH 가 차례로 오는 순서로 나타나고 뒤의 장애는 POLL 뒤에 COMMIT 없이 CRASH 가 오는 순서로 나타난다. 재조정 뒤 아직 새 세대를 모르는 멤버가 처리를 마치고 커밋하면 그 커밋은 거절되고 새 담당 멤버가 같은 레코드를 다시 받는다.

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 따른다.

[3.2] 그룹 이름과 컨슈머 이름은 1단계 3.12 의 이름이다.

[3.3] 토픽 목록 인자는 토픽 이름 하나 이상을 쉼표(`,`)로 이은 문자열이다. 토픽 이름이 하나이면 쉼표가 없다. 쉼표로 나눈 조각은 모두 1단계 3.12 의 이름이어야 한다. 같은 이름이 두 번 나오면 형식에 맞지 않는 인자다. 빈 조각도 이름이 아니므로 `a,,b` 나 `a,` 도 형식에 맞지 않는다.

[3.4] 이 문서에서 이름 순서는 유니코드 코드 포인트 순서다. 이름에는 ASCII 문자만 들어가므로 이 순서는 바이트 순서와 같고 `c10` 이 `c2` 보다 앞선다.

## 4. 명령 규칙

### 그룹

[4.1] `GROUP <그룹> <전략> <토픽 목록> [<설정>=<값>]...` 은 그룹을 만든다. 전략은 `range` 나 `roundrobin` 이다. 토픽 목록의 토픽이 그룹이 구독하는 토픽이다. 같은 이름의 그룹이 이미 있으면 `ERR GROUP_EXISTS` 다. 그다음 토픽 목록의 토픽 가운데 아직 만들지 않은 토픽이 하나라도 있으면 `ERR UNKNOWN_TOPIC` 이다. 새 그룹의 세대는 0 이고 멤버와 커밋 오프셋이 없다. 성공하면 아무것도 출력하지 않는다.

[4.2] 그룹 설정은 아래 세 가지다. 설정 인자를 나누고 확인하는 규칙은 1단계 4.5 와 같고 설정 이름이 이 표에 있어야 한다.

| 설정 | 값 | 기본값 |
|---|---|---|
| `session.timeout.ms` | 1 이상 1,000,000,000 이하인 정수 | 45000 |
| `max.poll.interval.ms` | 1 이상 1,000,000,000 이하인 정수 | 300000 |
| `auto.offset.reset` | `earliest` 나 `latest` | `latest` |

[4.3] 그룹의 파티션은 구독한 토픽의 지금 파티션을 모두 모은 것이다. 그룹의 파티션 순서는 토픽 이름 순서이고 같은 토픽 안에서는 파티션 번호 순서다. 토픽 목록에 적은 순서는 상관없다.

### 컨슈머

[4.4] 컨슈머는 JOIN 으로 시작하고 사라질 때까지 그 JOIN 에서 정한 그룹에 속한다. 컨슈머는 세 상태 가운데 하나다. 실행 중인 멤버는 그룹의 멤버이고 명령을 받는다. 멈춘 멤버는 CRASH 로 프로세스가 멈추었지만 세션 시간 초과 전이라 아직 그룹의 멤버다. 빠진 컨슈머는 poll 간격 초과로 그룹에서 빠졌지만 프로세스는 실행 중이다. 그룹의 멤버는 실행 중인 멤버와 멈춘 멤버다.

[4.5] 컨슈머는 세 경우에 사라진다. LEAVE 로 정상 종료할 때, 빠진 컨슈머가 CRASH 로 멈출 때, 멈춘 멤버가 세션 시간 초과에 걸릴 때다(4.20). 사라진 컨슈머의 이름은 다시 JOIN 할 수 있다. 사라지지 않은 컨슈머는 그룹이 달라도 이름이 겹치지 않는다.

[4.6] 컨슈머는 아는 세대와 파티션별 위치와 마감 시각을 가진다. 위치는 그 파티션에서 다음에 받을 오프셋이고 위치가 없는 파티션도 있다. 마감 시각은 4.19 에서 정한다.

### 재조정과 배정

[4.7] 재조정하면 그룹의 세대가 1 늘고 그때의 멤버로 배정을 새로 계산한다. 멤버를 이름 순서로 늘어놓은 것을 멤버 목록이라 하고 멤버 수를 C 라 한다. 멤버가 없으면 배정도 없다. 재조정은 아래 경우에만 일어난다.

- JOIN 이 성공할 때(4.10)
- 실행 중인 멤버가 LEAVE 할 때(4.11)
- 빠진 컨슈머가 POLL 로 다시 가입할 때(4.14)
- 시간 초과로 멤버를 그룹에서 뺄 때(4.20)
- 멤버가 하나 이상인 그룹이 구독한 토픽에 PARTITIONS 가 성공할 때(4.23)

[4.8] 범위 배정(`range`)은 구독한 토픽마다 따로 나눈다. 토픽의 파티션 수를 P 라 하고 q 와 r 을 P 를 C 로 나눈 몫과 나머지라 하자. 멤버 목록에서 0 부터 센 i 번째 멤버는 그 토픽의 파티션 i × q + min(i, r) 부터 연속한 파티션을 받는다. 받는 파티션 수는 i 가 r 보다 작으면 q + 1 이고 아니면 q 다.

[4.9] 순환 배정(`roundrobin`)은 그룹의 파티션을 4.3 의 순서로 늘어놓고 0 부터 센 k 번째 파티션을 멤버 목록에서 k 를 C 로 나눈 나머지 번째 멤버에게 준다.

### JOIN

[4.10] `JOIN <그룹> <컨슈머>` 는 컨슈머를 시작해 그룹에 실행 중인 멤버로 추가한다. 그룹이 없으면 `ERR UNKNOWN_GROUP` 이다. 그다음 같은 이름의 컨슈머가 있으면 그 컨슈머가 어느 그룹의 어떤 상태이든 `ERR CONSUMER_EXISTS` 다. 성공하면 그룹을 재조정하고 새 컨슈머의 아는 세대는 재조정 뒤의 세대가 된다. 새 컨슈머에는 위치가 없다. 아무것도 출력하지 않는다.

### LEAVE 와 CRASH

[4.11] `LEAVE <컨슈머>` 는 컨슈머를 정상 종료한다. 그런 컨슈머가 없으면 `ERR UNKNOWN_CONSUMER` 이고 멈춘 멤버이면 `ERR NOT_RUNNING` 이다. 실행 중인 멤버는 그룹을 떠나 사라지고 그룹은 재조정한다. 빠진 컨슈머는 이미 그룹의 멤버가 아니므로 재조정 없이 사라진다. 아무것도 출력하지 않는다.

[4.12] `CRASH <컨슈머>` 는 컨슈머 프로세스를 멈춘다. 오류는 LEAVE 와 같다. 실행 중인 멤버는 멈춘 멤버가 되고 배정은 그대로 남는다. CRASH 는 재조정을 일으키지 않는다. 빠진 컨슈머는 사라진다. 아무것도 출력하지 않는다.

### POLL

[4.13] `POLL <컨슈머> <최대 개수>` 는 컨슈머에게 배정된 파티션에서 레코드를 최대 개수까지 받는다. 최대 개수는 1 이상 1,000 이하인 정수다. 그런 컨슈머가 없으면 `ERR UNKNOWN_CONSUMER` 이고 멈춘 멤버이면 `ERR NOT_RUNNING` 이다.

[4.14] POLL 은 먼저 가입 상태를 맞춘다. 빠진 컨슈머는 다시 가입해 실행 중인 멤버가 되고 그룹은 재조정한다. 실행 중인 멤버의 아는 세대가 그룹의 세대와 다르면 새 세대의 배정을 받는다. 두 경우 모두 컨슈머의 위치를 모두 버리고 아는 세대를 그룹의 지금 세대로 바꾼다. 가입 상태가 바뀌었는지와 상관없이 POLL 은 마감 시각을 새로 정한다(4.19).

[4.15] 가입 상태를 맞춘 뒤 컨슈머에게 배정된 파티션마다 위치를 확인한다. 위치가 없으면 그룹의 커밋 오프셋을 위치로 삼고 커밋 오프셋이 없으면 `auto.offset.reset` 으로 정한다. `earliest` 면 로그 시작 오프셋이고 `latest` 면 로그 끝 오프셋이다. 이렇게 정한 위치나 원래 있던 위치가 로그 시작 오프셋보다 작으면 `auto.offset.reset` 으로 다시 정한다. 이 확인은 레코드를 받기 전에 배정된 파티션 모두에 한다.

[4.16] 위치를 모두 확인한 뒤 배정된 파티션을 4.3 의 순서로 하나씩 골라 그 파티션에서 오프셋이 위치 이상인 레코드를 오프셋 순서로 받는다. 받은 레코드가 최대 개수에 이르면 멈춘다. 레코드를 받은 파티션의 위치는 그 파티션에서 마지막으로 받은 레코드의 오프셋 + 1 이 된다.

[4.17] POLL 은 받은 레코드를 받은 순서대로 한 줄에 하나씩 `<토픽> <파티션> <오프셋> <키> <값>` 으로 출력한다. 키가 없으면 키 자리에 `-` 를 쓴다. 받은 레코드가 없으면 `(empty)` 한 줄을 출력한다.

### COMMIT

[4.18] `COMMIT <컨슈머>` 는 컨슈머에게 배정된 파티션 가운데 위치가 있는 파티션마다 그 위치를 그룹의 커밋 오프셋으로 삼는다. 아래 순서로 확인한다. 그런 컨슈머가 없으면 `ERR UNKNOWN_CONSUMER`, 멈춘 멤버이면 `ERR NOT_RUNNING`, 빠진 컨슈머이면 `ERR UNKNOWN_MEMBER` 다. 아는 세대가 그룹의 세대와 다르면 `ERR ILLEGAL_GENERATION` 이다. 성공하면 아무것도 출력하지 않는다.

### 시간 초과

[4.19] 실행 중인 멤버의 마감 시각은 마지막으로 JOIN 이나 POLL 한 시각에 `max.poll.interval.ms` 를 더한 시각이다. 멈춘 멤버의 마감 시각은 CRASH 한 시각에 `session.timeout.ms` 를 더한 시각이다. 빠진 컨슈머에게는 마감 시각이 없다. 재조정은 마감 시각을 바꾸지 않는다.

[4.20] TICK 이 지금 시각을 t 에서 t′ 으로 움직일 때 마감 시각이 t 보다 크고 t′ 이하인 멤버를 마감 시각 순서로, 마감 시각이 같으면 컨슈머 이름 순서로 하나씩 그룹에서 뺀다. 실행 중인 멤버는 빠진 컨슈머가 되고 멈춘 멤버는 사라진다. 멤버를 하나 뺄 때마다 그 그룹을 재조정하고 `<마감 시각> <그룹> <컨슈머> <종류>` 한 줄을 출력한다. 종류는 실행 중인 멤버였으면 `poll`, 멈춘 멤버였으면 `session` 이다. 보관 규칙(1단계 4.17~4.20)은 이 일을 모두 마친 뒤 t′ 에서 적용한다.

### ASSIGNMENT 와 COMMITTED

[4.21] `ASSIGNMENT <그룹>` 은 첫 줄에 `generation=<세대>` 를 출력하고 멤버마다 한 줄씩 이름 순서로 `<컨슈머> <상태> <파티션 목록>` 을 출력한다. 상태는 실행 중인 멤버가 `alive`, 멈춘 멤버가 `crashed` 다. 파티션 목록은 그 멤버에게 배정된 파티션을 `<토픽>-<파티션>` 으로 써서 4.3 의 순서로 쉼표로 이은 것이고 배정된 파티션이 없으면 `-` 다. 그룹이 없으면 `ERR UNKNOWN_GROUP` 이다.

[4.22] `COMMITTED <그룹>` 은 그룹의 파티션마다 한 줄씩 4.3 의 순서로 `<토픽> <파티션> committed=<커밋 오프셋> end=<로그 끝 오프셋>` 을 출력한다. 커밋 오프셋이 없으면 `-` 를 쓴다. 그룹이 없으면 `ERR UNKNOWN_GROUP` 이다.

### PARTITIONS

[4.23] PARTITIONS 가 성공하면 그 토픽을 구독하고 멤버가 하나 이상인 그룹을 모두 재조정한다. PARTITIONS 는 여전히 아무것도 출력하지 않는다.

## 5. 오류 규칙

[5.1] 1단계의 5.1 과 5.5 를 그대로 따른다.

[5.2] 명령어가 1단계와 2단계의 열한 명령과 GROUP, JOIN, LEAVE, CRASH, POLL, COMMIT, ASSIGNMENT, COMMITTED 가운데 어느 것과도 정확히 같지 않으면 `ERR UNKNOWN_COMMAND` 를 출력한다. 다만 TOMBSTONE 과 DIRTY 는 4단계에서 명령이 되므로 명령어가 이 둘 가운데 하나인 줄이 나오면 이 문서는 그 줄부터 입력 끝까지의 출력을 정하지 않는다.

[5.3] 인자 수가 맞지 않거나 인자가 형식에 맞지 않으면 `ERR BAD_ARGUMENT` 를 출력한다. 인자 수는 LEAVE, CRASH, COMMIT, ASSIGNMENT, COMMITTED 가 1 이고 JOIN 과 POLL 이 2 다. GROUP 은 3 이상이다. 그룹과 컨슈머 인자는 1단계 3.12 의 이름이어야 하고 GROUP 의 전략은 4.1, 토픽 목록은 3.3, 넷째 인자부터는 4.2 를 따라야 한다.

[5.4] 1단계 5.4 의 CREATE 가 나오면 이 문서도 그 줄부터 입력 끝까지의 출력을 정하지 않는다.

[5.5] 이 단계에서 새로 생기는 오류 줄은 아래와 같다.

| 오류 줄 | 뜻 |
|---|---|
| `ERR GROUP_EXISTS` | 같은 이름의 그룹이 이미 있음 |
| `ERR UNKNOWN_GROUP` | 그룹이 없음 |
| `ERR CONSUMER_EXISTS` | 같은 이름의 컨슈머가 있음 |
| `ERR UNKNOWN_CONSUMER` | 컨슈머가 없음 |
| `ERR NOT_RUNNING` | 멈춘 멤버에게 명령을 줌 |
| `ERR UNKNOWN_MEMBER` | 그룹에서 빠진 컨슈머가 커밋함 |
| `ERR ILLEGAL_GENERATION` | 아는 세대가 그룹의 세대와 다른 멤버가 커밋함 |

## 6. 예시

[6.1] 예시의 표기는 1단계 6.1 과 같다.

[6.2] 배정 전략이다. 범위 배정에서는 토픽 a 와 b 의 남는 파티션이 모두 앞 멤버 c1 에게 간다. 순환 배정은 a-0, a-1, a-2, b-0, b-1, b-2 를 c3, c4 에게 번갈아 준다. 토픽 목록을 `b,a` 로 적어도 순서는 토픽 이름 순서다. c10 이 들어오면 멤버 목록은 c1, c10, c2 순서다.

```
> CREATE a 3
> CREATE b 3
> GROUP gr range a,b
> GROUP gq roundrobin b,a
> JOIN gr c1
> JOIN gr c2
> JOIN gq c3
> JOIN gq c4
> ASSIGNMENT gr
generation=2
c1 alive a-0,a-1,b-0,b-1
c2 alive a-2,b-2
> ASSIGNMENT gq
generation=2
c3 alive a-0,a-2,b-1
c4 alive a-1,b-0,b-2
> JOIN gr c10
> ASSIGNMENT gr
generation=3
c1 alive a-0,b-0
c10 alive a-1,b-1
c2 alive a-2,b-2
```

[6.3] 처리 뒤 커밋과 처리 전 커밋이다. 그룹 after 는 처리한 뒤 커밋하고 그룹 before 는 처리하기 전에 커밋한다. a1 은 o1, o2 를 받아 처리했지만 커밋하기 전에 멈춘다. b1 은 o1, o2 를 받자마자 커밋하고 처리하기 전에 멈춘다. 시각 1000 에 두 멤버가 세션 시간 초과로 사라진 뒤 a2 는 커밋 오프셋이 없어 o1 부터 다시 받으므로 o1 과 o2 는 두 번 처리된다. b2 는 커밋 오프셋 2 부터 받으므로 o1 과 o2 는 끝내 처리되지 않는다.

```
> CREATE orders 1
> PRODUCE orders - o1
partition=0 offset=0
> PRODUCE orders - o2
partition=0 offset=1
> PRODUCE orders - o3
partition=0 offset=2
> GROUP after range orders auto.offset.reset=earliest session.timeout.ms=1000
> GROUP before range orders auto.offset.reset=earliest session.timeout.ms=1000
> JOIN after a1
> JOIN after a2
> JOIN before b1
> JOIN before b2
> POLL a1 2
orders 0 0 - o1
orders 0 1 - o2
> CRASH a1
> POLL b1 2
orders 0 0 - o1
orders 0 1 - o2
> COMMIT b1
> CRASH b1
> TICK 1000
1000 after a1 session
1000 before b1 session
> POLL a2 5
orders 0 0 - o1
orders 0 1 - o2
orders 0 2 - o3
> POLL b2 5
orders 0 2 - o3
```

[6.4] 재조정 뒤의 커밋이다. c2 가 들어와 세대가 2 가 되었지만 c1 은 아직 POLL 하지 않아 세대 1 을 알고 있으므로 커밋이 거절된다. c1 은 다음 POLL 에서 세대 2 의 배정을 받는다. c1 은 계속 맡은 t-0 의 위치도 버렸고 커밋 오프셋이 없으므로 t-0 을 오프셋 0 부터 다시 받는다. t-1 을 넘겨받은 c2 도 오프셋 0 부터 받아 c1 이 이미 받은 두 레코드를 다시 받는다.

```
> CREATE t 2
> PRODUCE t k1 x
partition=1 offset=0
> PRODUCE t k2 y
partition=0 offset=0
> PRODUCE t k3 z
partition=1 offset=1
> GROUP g roundrobin t auto.offset.reset=earliest
> JOIN g c1
> POLL c1 10
t 0 0 k2 y
t 1 0 k1 x
t 1 1 k3 z
> JOIN g c2
> COMMIT c1
ERR ILLEGAL_GENERATION
> POLL c1 10
t 0 0 k2 y
> COMMIT c1
> POLL c2 10
t 1 0 k1 x
t 1 1 k3 z
> COMMITTED g
t 0 committed=1 end=1
t 1 committed=- end=2
```

[6.5] poll 간격 초과다. c1 은 시각 0 에 가입한 뒤 POLL 하지 않아 시각 500 에 빠진다. 세대 3 에서는 c2 가 s-0 을 맡는다. 빠진 c1 의 커밋은 거절된다. c1 이 POLL 하면 다시 가입하므로 세대가 4 가 되고 이름이 앞선 c1 이 s-0 을 다시 맡는다. 마지막 TICK 에서는 시각 300 에 POLL 한 c2 가 800 에, 시각 500 에 POLL 한 c1 이 1000 에 빠진다.

```
> CREATE s 1
> GROUP g range s max.poll.interval.ms=500
> JOIN g c1
> JOIN g c2
> TICK 300
> POLL c2 1
(empty)
> TICK 200
500 g c1 poll
> COMMIT c1
ERR UNKNOWN_MEMBER
> ASSIGNMENT g
generation=3
c2 alive s-0
> POLL c1 1
(empty)
> ASSIGNMENT g
generation=4
c1 alive s-0
c2 alive -
> TICK 500
800 g c2 poll
1000 g c1 poll
```

[6.6] `latest` 와 파티션 늘리기다. c1 은 이미 있던 `old` 를 건너뛰고 로그 끝에서 시작한다. 파티션을 늘리면 그룹이 재조정된다. c1 이 새 세대를 알기 전에 새 파티션 1 에 `hi` 가 쓰인다. c1 은 다음 POLL 에서 파티션 1 의 위치를 `latest` 로 정하므로 `hi` 를 받지 않는다.

```
> CREATE e 1
> PRODUCE e - old
partition=0 offset=0
> GROUP g range e
> JOIN g c1
> POLL c1 5
(empty)
> PRODUCE e - new
partition=0 offset=1
> POLL c1 5
e 0 1 - new
> COMMIT c1
> PARTITIONS e 2
> PRODUCE e k1 hi
partition=1 offset=0
> POLL c1 5
(empty)
> COMMITTED g
e 0 committed=2 end=2
e 1 committed=- end=1
```

[6.7] 커밋 오프셋이 보관 규칙으로 지워진 경우다. TICK 뒤 로그 시작 오프셋은 2 가 되어 커밋 오프셋 1 의 레코드는 지워졌다. 새로 가입한 c2 는 커밋 오프셋 1 을 위치로 삼았다가 로그 시작 오프셋보다 작으므로 `earliest` 에 따라 2 부터 받는다.

```
> CREATE r 1 segment.bytes=4 retention.bytes=4
> GROUP g range r auto.offset.reset=earliest
> JOIN g c1
> PRODUCE r - aaaa
partition=0 offset=0
> POLL c1 1
r 0 0 - aaaa
> COMMIT c1
> PRODUCE r - bbbb
partition=0 offset=1
> PRODUCE r - cccc
partition=0 offset=2
> TICK 1
> LEAVE c1
> JOIN g c2
> COMMITTED g
r 0 committed=1 end=3
> POLL c2 5
r 0 2 - cccc
```

[6.8] 오류다. 멈춘 멤버는 세션 시간 초과로 사라지기 전까지 POLL, COMMIT, LEAVE 를 받지 않고 그 이름으로 다시 JOIN 할 수도 없다.

```
> GROUP g range nope
ERR UNKNOWN_TOPIC
> CREATE t 1
> GROUP g sticky t
ERR BAD_ARGUMENT
> GROUP g range t,t
ERR BAD_ARGUMENT
> GROUP g range t session.timeout.ms=0
ERR BAD_ARGUMENT
> GROUP g range t
> GROUP g roundrobin t
ERR GROUP_EXISTS
> JOIN h c1
ERR UNKNOWN_GROUP
> JOIN g c1
> JOIN g c1
ERR CONSUMER_EXISTS
> POLL c9 1
ERR UNKNOWN_CONSUMER
> CRASH c1
> POLL c1 1
ERR NOT_RUNNING
> COMMIT c1
ERR NOT_RUNNING
> LEAVE c1
ERR NOT_RUNNING
> JOIN g c1
ERR CONSUMER_EXISTS
> TICK 45000
45000 g c1 session
> JOIN g c1
> ASSIGNMENT g
generation=3
c1 alive t-0
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계 7.1~7.5 와 7.7 과 같다.

[7.2] 대규모 테스트는 토픽 20개(처음 파티션 합계 160개)에 메시지 1,000,000개를 쓰면서 그룹 10,000개와 컨슈머 200,000개 안팎이 JOIN, LEAVE, CRASH, POLL, COMMIT 을 실행하는 입력이다. 컨슈머는 처음 메시지 200,000개를 쓰는 동안 모두 가입한다. 그룹의 `max.poll.interval.ms` 는 50,000,000 이상이라서 가입이 끝난 뒤부터 마지막 TICK 11개 전까지 모든 그룹의 멤버를 합치면 늘 150,000명이 넘는다. TICK 300,000개 안팎 가운데 마지막 11개를 뺀 TICK 은 한 번에 3,000밀리초 미만을 움직인다. 마지막 TICK 11개는 각각 1,000,000,000밀리초를 움직이므로 시각의 합은 10,000,000,000밀리초를 넘는다. 남은 멤버는 마지막 TICK 11개 가운데 첫 TICK 에서 모두 시간 초과로 빠진다. 시간 초과로 멤버를 그룹에서 빼는 일은 마지막 TICK 11개 전에도 수만 번 일어난다. POLL 은 80,000개 안팎이고 최대 개수는 3 이하다. PARTITIONS 도 섞인다. ASSIGNMENT 와 COMMITTED 는 합쳐 4,000개 안팎이다. 표준 출력은 모두 합쳐 48MB 이하다. 제한 시간은 30초다. TICK 마다 그룹의 멤버를 모두 훑어 마감 시각을 확인하는 풀이와 시각을 1밀리초씩 움직이며 확인하는 풀이는 이 제한 시간을 넘기도록 규모를 정했다. 멤버를 모두 훑는 풀이는 마감 시각을 모두 합쳐 500억 번 넘게 확인한다. 1밀리초씩 움직이는 풀이는 시각을 100억 번 넘게 움직인다.

[7.3] 3단계 검사 입력에는 명령어가 TOMBSTONE 이나 DIRTY 인 줄과 1단계 5.4 의 CREATE 가 나오지 않는다.

[7.4] 3단계를 검사할 때 1단계와 2단계 테스트도 다시 실행한다. 4단계를 검사할 때도 3단계 테스트를 다시 실행한다.
