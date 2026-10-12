# 3단계: 직렬화 실패 감지

## 1. 개요

[1.1] 3단계는 격리 수준 SERIALIZABLE 을 더한다. SERIALIZABLE 트랜잭션은 REPEATABLE READ 트랜잭션과 똑같이 실행하면서 다른 SERIALIZABLE 트랜잭션과의 읽기-쓰기 의존을 기록한다. 커밋하려는 트랜잭션이 의존 두 개가 이어진 위험한 구조에 들어 있고 그 구조의 다른 트랜잭션이 이미 커밋했으면 커밋하려는 쪽을 실패시킨다. 1단계 6.6 의 쓰기 비틀림은 두 트랜잭션이 모두 SERIALIZABLE 이면 나중에 커밋하려는 쪽의 실패로 바뀐다.

[1.2] 1단계와 2단계의 규칙은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 4.24 와 2단계 5.2 가 출력을 정하지 않은 BEGIN, 곧 인자가 둘이고 둘째 인자가 `SERIALIZABLE` 인 BEGIN 의 출력을 이 문서가 정한다.

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `BEGIN <트랜잭션> SERIALIZABLE` | SERIALIZABLE 트랜잭션을 시작한다 | 1줄 |
| `COMMIT <트랜잭션>` | SERIALIZABLE 트랜잭션이면 직렬화 실패도 판정한다 | 1줄 |

[1.3] 새 출력 줄은 직렬화 실패를 알리는 `<트랜잭션> ABORT SERIALIZATION` 하나다.

## 2. 배경 개념

[2.1] 여러 트랜잭션을 동시에 실행한 결과가 그 트랜잭션들을 어떤 차례로 하나씩 실행한 결과와 같으면 그 실행은 직렬 가능하다. SQL 표준의 SERIALIZABLE 은 이것을 약속한다. 1단계의 스냅숏 격리는 쓰기 비틀림을 허용하므로 직렬 가능하지 않은 결과를 낼 수 있다.

[2.2] 직렬 가능한지는 트랜잭션 사이의 의존으로 따진다. T1 이 만든 버전을 T2 가 읽으면 wr 의존, T1 이 만든 버전을 T2 가 새 버전으로 덮으면 ww 의존이다. T1 이 읽은 키를 T2 가 새 버전으로 고쳤는데 T1 은 그 버전을 보지 못했으면 rw 의존(읽기-쓰기 의존)이다. 의존이 T1 에서 T2 로 향하면 하나씩 실행하는 차례에서 T1 이 T2 보다 앞에 와야 한다. 트랜잭션을 점으로, 의존을 화살표로 그린 그래프에 고리가 있으면 어떤 차례로도 그 결과를 낼 수 없으므로 직렬 가능하지 않다.

[2.3] 스냅숏 격리에서 wr 의존과 ww 의존은 늘 먼저 커밋한 트랜잭션에서 나중에 커밋한 트랜잭션으로 향한다. 커밋 순서를 거슬러 갈 수 있는 것은 동시에 실행된 두 트랜잭션 사이의 rw 의존뿐이다. Cahill 외(2008)가 기댄 정리에 따르면 스냅숏 격리에서 직렬 가능하지 않은 실행의 의존 그래프에는 동시에 실행된 트랜잭션 사이의 rw 의존 두 개가 이어진 부분이 반드시 있다.

[2.4] 이렇게 이어진 두 rw 의존 T_in → T_pivot → T_out 을 위험한 구조라 한다. T_in 과 T_out 은 같은 트랜잭션일 수 있다. 쓰기 비틀림에서는 두 트랜잭션이 저마다 상대가 고칠 키를 읽으므로 T1 → T2 → T1 의 구조가 생긴다. 직렬화 스냅숏 격리(SSI)는 의존 그래프의 고리 전체를 추적하지 않고 위험한 구조만 찾아 트랜잭션 하나를 실패시킨다. 위험한 구조가 실제 고리로 이어지지 않아 이상이 없었을 실행에서도 실패시킬 수 있다. 그 대신 그래프 전체를 유지하고 고리를 찾는 것보다 비용이 적다.

[2.5] rw 의존을 찾으려면 누가 무엇을 읽었는지 기록해야 한다. SSI 는 읽은 키에 아무것도 막지 않는 표식(SIREAD 잠금)을 남긴다. 쓰는 쪽은 쓰려는 키에 다른 트랜잭션의 표식이 있는지 확인하고 읽는 쪽은 자기 스냅숏에 보이지 않는 새 버전이나 커밋되지 않은 쓰기가 있는지 확인한다. 범위를 읽으면 범위 전체에 표식을 남기므로 그 범위에 새로 들어오는 키에 쓰는 것도 의존으로 드러난다. 커밋한 트랜잭션의 표식도 바로 지울 수 없다. 그 트랜잭션과 동시에 실행된 트랜잭션이 아직 쓰기를 할 수 있기 때문이다. 표식을 지운 뒤에도 이미 드러난 rw 의존은 위험한 구조를 찾을 때 계속 쓰인다.

[2.6] PostgreSQL 의 SERIALIZABLE 은 PostgreSQL 의 REPEATABLE READ 와 똑같이 실행하면서 SERIALIZABLE 트랜잭션 사이의 의존을 감시하는 SSI 다. 직렬화 이상으로 이어질 수 있는 구조를 찾으면 트랜잭션 하나를 SQLSTATE 40001 로 실패시킨다. 애플리케이션은 그 트랜잭션을 처음부터 다시 실행해야 한다. PostgreSQL 은 구조에 얽힌 트랜잭션 가운데 하나가 커밋 단계에 들어선 뒤에야 다른 트랜잭션을 실패시킨다. 곧바로 다시 실행한 트랜잭션이 커밋된 결과를 새 스냅숏에서 보고 같은 상대와 다시 충돌하지 않게 하려는 것이다. SERIALIZABLE 이 아닌 트랜잭션은 감시하지 않으므로 규칙을 SSI 에 맡기려면 그 데이터를 다루는 트랜잭션을 모두 SERIALIZABLE 로 실행해야 한다.

[2.7] 이 트랙의 판정은 더 단순하다. 위험한 구조는 동시에 실행된 SERIALIZABLE 트랜잭션 사이의 rw 의존만으로 정하고 커밋 순서 같은 조건으로 거르지 않는다. 판정은 COMMIT 에서만 하고 커밋하려는 트랜잭션이 든 구조에 이미 커밋한 다른 트랜잭션이 있으면 커밋하려는 쪽을 실패시킨다.

## 3. 입출력 규칙

[3.1] 1단계와 2단계의 입출력 규칙을 그대로 따른다.

## 4. 명령 규칙

### SERIALIZABLE

[4.1] `BEGIN <트랜잭션> SERIALIZABLE` 은 SERIALIZABLE 트랜잭션을 시작하고 `<트랜잭션> BEGIN` 을 출력한다. 격리 수준 SERIALIZABLE 은 낱말 하나다.

[4.2] SERIALIZABLE 트랜잭션은 1단계와 2단계에서 REPEATABLE READ 트랜잭션이 따르는 규칙을 따른다. BEGIN 을 실행한 순간의 스냅숏 하나로 읽고 COMMIT 에서 쓰기 충돌을 판정한다. 잠금을 걸고 기다리고 교착의 희생자가 되는 규칙도 같다. 다만 COMMIT 의 결과는 4.10~4.12 가 정한다. 1단계 4.16 과 4.20 으로는 커밋할 트랜잭션도 4.11 로 실패할 수 있다.

[4.3] SERIALIZABLE 이 아닌 트랜잭션에는 이 문서의 판정이 없다. 그런 트랜잭션은 rw 의존에 들지 않는다(4.8).

### 읽기와 쓰기

[4.4] 트랜잭션이 키에 READ 를 실행하면 그 트랜잭션은 그 키를 읽었다. FOR 절이 있든 없든, 읽은 값이 자기가 쓴 값이든 값이 없었든 같다. 트랜잭션이 SCAN 을 실행하면 범위에 든 모든 키를 읽은 것이다. SCAN 을 실행할 때 값이 없던 키도 범위에 들면 읽은 키다.

[4.5] 트랜잭션이 키에 WRITE 를 실행하면 그 트랜잭션은 그 키를 썼다.

[4.6] 기다리는 명령(2단계 4.9)과 대기열에 넣은 명령(2단계 4.10)은 실행되기 전까지 아무것도 읽거나 쓰지 않은 것이다.

[4.7] 두 트랜잭션 가운데 어느 쪽도 다른 쪽이 BEGIN 하기 전에 커밋하지 않았으면 두 트랜잭션은 동시에 실행되었다고 한다. 둘 다 활동 중이면 동시에 실행된 것이다. 한쪽이 커밋한 뒤에 다른 쪽이 BEGIN 했으면 동시에 실행되지 않았다.

### rw 의존과 위험한 구조

[4.8] 아래 조건을 모두 만족하면 트랜잭션 A 에서 트랜잭션 B 로 가는 rw 의존이 있다. A 와 B 는 서로 다른 SERIALIZABLE 트랜잭션이다. A 와 B 는 저마다 활동 중이거나 커밋했다. 롤백한 트랜잭션, 커밋에 실패한 트랜잭션, 교착의 희생자는 rw 의존에 들지 않는다. A 와 B 는 동시에 실행되었다. A 가 읽은 키 가운데 B 가 쓴 키가 하나 이상 있다. A 가 읽은 때와 B 가 쓴 때의 앞뒤는 상관없고 A 가 그 키를 쓴 적이 있어도 상관없다. A 와 B 가 모두 커밋한 뒤에도 A 에서 B 로 가는 rw 의존은 남는다. A 나 B 와 동시에 실행된 다른 트랜잭션이 모두 끝난 뒤에도 같다.

[4.9] rw 의존 X → Y 와 Y → Z 가 이어진 것을 위험한 구조라 한다. X 와 Z 는 같은 트랜잭션이어도 된다. X, Y, Z 를 그 구조에 든 트랜잭션이라 한다.

### COMMIT

[4.10] SERIALIZABLE 트랜잭션 T 의 COMMIT 은 먼저 1단계 4.17 의 쓰기 충돌을 판정한다. 충돌한 키가 있으면 1단계대로 `<트랜잭션> ABORT WRITE_CONFLICT <키>` 를 출력하며 실패하고 4.11 의 판정은 하지 않는다.

[4.11] 쓰기 충돌이 없으면 T 가 든 위험한 구조를 모두 확인한다. 그 가운데 T 가 아닌 트랜잭션 하나라도 이미 커밋한 구조가 있으면 T 는 커밋에 실패한다. T 가 쓴 값은 모두 버려지고 잠금은 모두 풀리며 T 는 끝난다. 이때 `<트랜잭션> ABORT SERIALIZATION` 을 출력한다. 키를 하나도 쓰지 않은 T 도 위험한 구조의 X 자리에 들 수 있으므로 이 규칙으로 실패할 수 있다.

[4.12] 그런 구조가 없으면 T 는 커밋한다. T 가 든 구조의 다른 트랜잭션이 모두 활동 중이어도 T 는 커밋한다. 4.11 의 판정에는 T 의 COMMIT 을 실행하는 순간에 있는 구조만 쓴다. 이미 커밋한 트랜잭션이 들었던 구조라도 4.13 에 따라 없어졌으면 T 는 그 구조 때문에 실패하지 않는다.

[4.13] 커밋에 실패한 T 는 4.8 에 따라 rw 의존에서 빠지므로 T 가 들었던 위험한 구조도 모두 없어진다. 롤백한 트랜잭션과 교착의 희생자도 같다.

[4.14] 직렬화 실패는 COMMIT 에서만 판정한다. READ, WRITE, SCAN 은 rw 의존 때문에 실패하거나 기다리지 않는다. 읽은 키의 기록은 잠금이 아니므로 2단계의 잠금 충돌과 대기 그래프에 들지 않는다.

[4.15] 4.11 로 실패한 트랜잭션의 잠금이 풀리면 2단계 4.13 의 차례대로 그 COMMIT 을 처리한 뒤 재개(2단계 4.12)를 한다.

## 5. 오류 규칙

[5.1] 1단계와 2단계의 오류 규칙을 그대로 따른다. 1단계 5.3 의 BEGIN 인자 형식에 트랜잭션 뒤에 `SERIALIZABLE` 한 낱말이 오는 두 개가 더해진다. `BEGIN t1 SERIALIZABLE READ` 와 `BEGIN t1 serializable` 은 형식에 맞지 않는다.

[5.2] 이 단계에서 새로 생기는 오류 줄은 없다.

## 6. 예시

[6.1] 예시의 표기는 1단계 6.1 과 같다.

[6.2] 1단계 6.6 의 쓰기 비틀림을 SERIALIZABLE 로 실행한다. t1 이 SCAN 으로 읽은 범위에 t2 가 쓴 bob 이 들어 t1 → t2 가 생기고 t2 가 읽은 범위에 t1 이 쓴 alice 가 들어 t2 → t1 이 생긴다. 이 둘이 이어진 위험한 구조 t1 → t2 → t1 에서 t1 이 커밋할 때는 t2 가 활동 중이므로 t1 은 커밋한다. t2 가 커밋할 때는 t1 이 이미 커밋했으므로 t2 가 실패한다. 다시 실행한 t3 은 t1 이 커밋한 뒤에 시작했으므로 t1 과 동시에 실행되지 않았고 alice 가 off 인 것을 본다.

```
> BEGIN init
init BEGIN
> WRITE init alice on
init OK
> WRITE init bob on
init OK
> COMMIT init
init COMMIT
> BEGIN t1 SERIALIZABLE
t1 BEGIN
> BEGIN t2 SERIALIZABLE
t2 BEGIN
> SCAN t1 a c
t1 alice=on bob=on
> SCAN t2 a c
t2 alice=on bob=on
> WRITE t1 alice off
t1 OK
> WRITE t2 bob off
t2 OK
> COMMIT t1
t1 COMMIT
> COMMIT t2
t2 ABORT SERIALIZATION
> BEGIN t3 SERIALIZABLE
t3 BEGIN
> SCAN t3 a c
t3 alice=off bob=on
> COMMIT t3
t3 COMMIT
```

[6.3] 범위에 새로 들어오는 키다. 회의실 예약에서 두 트랜잭션이 같은 시간대의 예약이 없음을 확인하고 저마다 새 예약을 넣는다. 두 SCAN 은 값이 있는 키를 하나도 고르지 못했지만 범위의 모든 키를 읽은 것이므로 r10a 와 r10b 를 쓴 상대와 rw 의존이 생긴다. BEGIN 은 m1 이 먼저 했지만 먼저 커밋한 m2 가 이긴다.

```
> BEGIN m1 SERIALIZABLE
m1 BEGIN
> BEGIN m2 SERIALIZABLE
m2 BEGIN
> SCAN m1 r10 r11
m1 (none)
> SCAN m2 r10 r11
m2 (none)
> WRITE m1 r10a kim
m1 OK
> WRITE m2 r10b lee
m2 OK
> COMMIT m2
m2 COMMIT
> COMMIT m1
m1 ABORT SERIALIZATION
```

[6.4] 세 트랜잭션의 구조다. t1 이 읽은 a 를 t2 가 쓰고 t2 가 읽은 b 를 t3 이 써서 t1 → t2 → t3 이 생긴다. t3 이 먼저 커밋하고 다음에 커밋하는 t2 가 실패한다. t2 가 빠지면 구조가 없어지므로 t1 은 커밋한다. 같은 모양의 u1 → u2 → u3 에서는 u2 가 먼저 커밋하므로 다음에 커밋하는 u1 이 실패하고 u3 은 커밋한다. u1 은 키를 하나도 쓰지 않았지만 실패한다.

```
> BEGIN init
init BEGIN
> WRITE init a 1
init OK
> WRITE init b 1
init OK
> COMMIT init
init COMMIT
> BEGIN t1 SERIALIZABLE
t1 BEGIN
> BEGIN t2 SERIALIZABLE
t2 BEGIN
> BEGIN t3 SERIALIZABLE
t3 BEGIN
> READ t1 a
t1 a=1
> WRITE t2 a 2
t2 OK
> READ t2 b
t2 b=1
> WRITE t3 b 2
t3 OK
> COMMIT t3
t3 COMMIT
> COMMIT t2
t2 ABORT SERIALIZATION
> COMMIT t1
t1 COMMIT
> BEGIN u1 SERIALIZABLE
u1 BEGIN
> BEGIN u2 SERIALIZABLE
u2 BEGIN
> BEGIN u3 SERIALIZABLE
u3 BEGIN
> READ u1 a
u1 a=1
> WRITE u2 a 3
u2 OK
> READ u2 b
u2 b=2
> WRITE u3 b 3
u3 OK
> COMMIT u2
u2 COMMIT
> COMMIT u1
u1 ABORT SERIALIZATION
> COMMIT u3
u3 COMMIT
```

[6.5] 동시에 실행되지 않은 트랜잭션 사이에는 rw 의존이 없다. p 는 q 가 BEGIN 하기 전에 커밋했으므로 p 가 읽은 x 를 q 가 써도 p → q 가 생기지 않는다. q → r 하나만으로는 위험한 구조가 아니므로 q 는 커밋한다.

```
> BEGIN p SERIALIZABLE
p BEGIN
> READ p x
p x=(none)
> COMMIT p
p COMMIT
> BEGIN q SERIALIZABLE
q BEGIN
> BEGIN r SERIALIZABLE
r BEGIN
> WRITE q x 1
q OK
> READ q k
q k=(none)
> WRITE r k 1
r OK
> COMMIT r
r COMMIT
> COMMIT q
q COMMIT
```

[6.6] SERIALIZABLE 이 아닌 상대는 감시하지 않는다. 6.2 와 같은 일을 하지만 v2 가 REPEATABLE READ 이므로 rw 의존이 생기지 않고 쓰기 비틀림이 그대로 일어난다.

```
> BEGIN init
init BEGIN
> WRITE init alice on
init OK
> WRITE init bob on
init OK
> COMMIT init
init COMMIT
> BEGIN v1 SERIALIZABLE
v1 BEGIN
> BEGIN v2 REPEATABLE READ
v2 BEGIN
> SCAN v1 a c
v1 alice=on bob=on
> SCAN v2 a c
v2 alice=on bob=on
> WRITE v1 alice off
v1 OK
> WRITE v2 bob off
v2 OK
> COMMIT v2
v2 COMMIT
> COMMIT v1
v1 COMMIT
```

[6.7] 쓰기 충돌을 먼저 판정하고 롤백한 트랜잭션은 구조에서 빠진다. w1 과 w2 는 서로 상대가 쓴 키를 읽어 위험한 구조를 이룬다. w1 은 w2 가 BEGIN 한 뒤에 커밋했고 w2 와 같은 키 s 를 썼으므로 w2 의 COMMIT 은 직렬화 실패보다 쓰기 충돌로 먼저 실패한다. y1 과 y2 도 위험한 구조를 이루지만 y1 이 롤백해 구조가 없어지므로 y2 는 커밋한다.

```
> BEGIN w1 SERIALIZABLE
w1 BEGIN
> BEGIN w2 SERIALIZABLE
w2 BEGIN
> READ w1 a
w1 a=(none)
> READ w2 b
w2 b=(none)
> WRITE w1 b 1
w1 OK
> WRITE w1 s 1
w1 OK
> WRITE w2 a 1
w2 OK
> COMMIT w1
w1 COMMIT
> WRITE w2 s 2
w2 OK
> COMMIT w2
w2 ABORT WRITE_CONFLICT s
> BEGIN y1 SERIALIZABLE
y1 BEGIN
> BEGIN y2 SERIALIZABLE
y2 BEGIN
> READ y1 c
y1 c=(none)
> READ y2 d
y2 d=(none)
> WRITE y1 d 1
y1 OK
> WRITE y2 c 1
y2 OK
> ROLLBACK y1
y1 ROLLBACK
> COMMIT y2
y2 COMMIT
```

[6.8] 오류다.

```
> BEGIN t1 SERIALIZABLE READ
ERR BAD_ARGUMENT
> BEGIN t1 serializable
ERR BAD_ARGUMENT
> BEGIN t1 SERIALIZABLE
t1 BEGIN
> COMMIT t1
t1 COMMIT
```

## 7. 검사 방식

[7.1] 1단계 7.1~7.5, 7.7, 7.10 을 그대로 따른다.

[7.2] 대규모 테스트는 트랜잭션 110,000개 안팎과 입력 600,000줄 안팎으로 이뤄진다. 트랜잭션의 85% 안팎이 SERIALIZABLE 이고 그 가운데 90% 안팎이 SCAN 을 한 번 실행한다. 동시에 활동 중인 트랜잭션은 200개 안팎이다. 제한 시간은 15초다. 끝난 트랜잭션의 읽기와 쓰기 기록을 버리지 않고 COMMIT 마다 지금까지 시작한 SERIALIZABLE 트랜잭션을 모두 맞춰 보는 풀이는 이 제한 시간을 넘기도록 규모를 정했다. SCAN 으로 읽은 범위를 트랜잭션이 끝난 뒤에도 버리지 않고 WRITE 마다 모두 맞춰 보는 풀이도 이 제한 시간을 넘긴다.

[7.3] 3단계를 검사할 때는 1단계와 2단계 테스트를 먼저 다시 실행한다.
