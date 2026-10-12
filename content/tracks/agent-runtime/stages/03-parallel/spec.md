# 3단계: 병렬 도구 실행

## 1. 개요

[1.1] 3단계는 한 응답에 든 여러 도구 사용을 가상 시계 위에서 동시에 실행한다. 도구 실행마다 걸리는 시간이 있고 실행기는 동시에 실행하는 수의 상한과 시도 한 번의 시간 제한을 지킨다. 실패한 실행은 정한 시간만큼 기다렸다가 다시 시도하되 부수효과가 있는 도구는 다시 시도하지 않는다. 기다리는 동안에는 동시 실행 자리를 내놓으므로 실행 일정은 다른 실행이 언제 끝나는지에 따라 달라진다. 결과는 끝난 순서와 상관없이 도구 사용 블록의 순서대로 한 사용자 메시지에 담는다.

[1.2] 1단계와 2단계의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 3.13 이 출력을 정하지 않은 필드 가운데 이 문서가 뜻을 정하는 것은 아래 필드뿐이다. 나머지 필드와 1단계 3.12 의 명령어는 이 문서도 출력을 정하지 않는다.

| 명령 | 새 필드 | 뜻 |
|---|---|---|
| `tool` | `side_effect` | 도구에 부수효과가 있는지 |
| `fake` | `ms` | 도구 실행에 걸리는 가상 시간 |
| `fake` | `attempts` | 시도마다 다른 가짜 결과 |
| `config` | `concurrency` | 한 도구 단계에서 동시에 실행할 수 있는 수 |
| `config` | `timeout_ms` | 시도 한 번의 시간 제한 |
| `config` | `retries` | 실패한 실행을 다시 시도하는 최대 횟수 |
| `config` | `backoff_ms` | 실패한 실행을 다시 시도하기 전에 기다리는 시간 |
| `config` | `trace` | exec 줄과 clock 줄을 출력할지 |

## 2. 배경 개념

[2.1] 모델은 한 응답에 도구 사용 블록을 여럿 담을 수 있다. 서로 관계없는 조회 셋을 한꺼번에 요청하는 식이다. Claude API 문서는 이런 호출을 어떤 순서로 실행할지 API 가 정하지 않는다고 적는다. API 규약은 호출을 동시에 실행하는 실행기와 블록 순서대로 하나씩 실행하는 실행기를 모두 허용한다. 문서는 서로 독립인 읽기 작업은 대개 병렬로 실행해도 안전하고 부수효과나 공유 상태나 순서 요구가 있는 도구는 하나씩 순서대로 실행하는 편이 나을 수 있다고 덧붙인다. 이 트랙의 실행기는 4.9, 4.10, 4.21~4.23 이 정한 일정대로 호출을 실행한다.

[2.2] 어떤 순서로 실행하든 결과는 도구 사용 블록마다 하나씩 다음 사용자 메시지 하나에 모아 보낸다. 동시에 실행하면 먼저 시작한 호출이 나중에 끝나기도 하므로 끝난 순서는 요청 순서와 다르다. 이 트랙의 규약은 결과를 요청 순서로 놓게 하므로 실행기는 끝난 순서를 결과 순서로 쓰면 안 된다.

[2.3] 실제 시계로 시간을 재는 프로그램은 테스트할 때마다 결과가 달라진다. 가상 시계는 프로그램이 직접 관리하는 시각이다. 실행에 300ms 가 걸리면 가상 시각이 300 만큼 지난 뒤 그 실행이 끝나고 프로그램은 실제로 기다리지 않는다. kotlinx-coroutines-test 의 가상 시간도 같은 생각이다. 가상 시계를 쓰는 시뮬레이션은 시각을 1 씩 늘려 가지 않고 다음 일이 일어나는 시각으로 곧바로 건너뛴다.

[2.4] 동시에 실행하는 수에는 대개 상한이 있다. 외부 API 의 처리율 제한이나 연결 수 제한 때문이다. 상한까지 실행 중이면 다음 호출은 실행 하나가 끝나 자리가 날 때까지 기다린다.

[2.5] 시간 제한은 실행기가 결과를 기다리는 시간의 상한이다. 시간 제한을 넘기면 실행기는 그 실행을 실패로 판정하고 더 기다리지 않는다. 그 순간 외부 시스템에서는 작업이 끝났을 수도 있고 끝나지 않았을 수도 있다. 실행기는 둘 가운데 어느 쪽인지 알 수 없다.

[2.6] 실패한 실행을 다시 시도하면 일시적인 장애를 넘길 수 있다. 다시 시도해도 안전한 것은 읽기처럼 부수효과가 없는 작업이다. 결제나 메일 발송처럼 부수효과가 있는 작업은 시간 제한으로 실패했어도 실제로는 이미 처리됐을 수 있으므로 다시 시도하면 두 번 결제하거나 메일을 두 번 보낼 수 있다. 그래서 이 트랙의 실행기는 부수효과가 있는 도구를 다시 시도하지 않는다. 그런 도구가 시간 제한으로 실패하면 결과를 모른다는 사실을 오류 결과로 알린다.

[2.7] 일시적인 장애가 났을 때 곧바로 다시 시도하면 또 실패하기 쉽다. 그래서 다시 시도하기 전에 잠시 기다리고 다시 시도할 때마다 기다리는 시간을 늘린다. 기다리는 시간을 두 배씩 늘리는 방식을 지수 백오프라 한다. 기다리는 동안 동시 실행 자리를 붙잡고 있으면 다른 호출이 그 자리를 쓰지 못하므로 이 트랙의 실행기는 자리를 내놓고 기다린다. 그러면 다시 시도할 호출이 언제 자리를 얻는지는 다른 호출이 언제 끝나는지에 달려 있다.

## 3. 입출력 규칙

[3.1] 1단계와 2단계의 입출력 규칙을 그대로 따른다.

[3.2] 시각과 시간은 가상 밀리초 단위의 0 이상인 정수다. 출력할 때는 10진수로 쓰고 앞에 0 을 붙이지 않는다.

## 4. 명령 규칙

### 새 필드

[4.1] `tool` 의 인자에는 `side_effect`(불리언) 필드가 있을 수 있다. 없으면 false 다. side_effect 가 true 인 도구를 부수효과가 있는 도구라 한다.

[4.2] `fake` 의 인자에는 `ms` 필드가 있을 수 있다. ms 는 0 이상 1,000,000,000 이하의 정수이고 없으면 0 이다. ms 는 이 가짜 결과를 내는 도구 실행에 걸리는 가상 시간이다.

[4.3] `fake` 의 인자에는 output, error, ms 대신 `attempts` 필드가 있을 수 있다. attempts 는 시도 객체를 하나 이상 담은 배열이다. 시도 객체에는 `output`(문자열)과 `error`(문자열) 가운데 정확히 하나가 있고 `ms` 가 있을 수 있다. 시도 객체의 output, error, ms 의 뜻은 fake 의 같은 이름 필드와 같다. 시도 객체에 다른 필드가 있으면 이 문서는 그 줄의 출력을 정하지 않는다.

[4.4] attempts 가 없는 fake 는 output 이나 error 하나와 ms 로 된 시도 객체 하나를 담은 attempts 와 같다. 도구 사용 블록 하나를 실행할 때 k 번째 시도는 attempts 의 k 번째 시도 객체를 쓴다. k 가 attempts 의 길이보다 크면 마지막 시도 객체를 쓴다. k 는 1 부터 센다. 같은 도구 이름과 같은 입력의 가짜 결과를 다시 정하면 이전 가짜 결과의 시도 객체를 모두 버리고 새 fake 의 attempts 로 바꾼다. 새 fake 에 ms 가 없으면 ms 는 이전 값을 이어받지 않고 0 이다.

[4.5] `config` 의 인자에는 아래 다섯 필드가 있을 수 있다.

| 필드 | 뜻 | 범위 | 기본값 |
|---|---|---|---|
| concurrency | 한 도구 단계에서 동시에 실행하는 시도의 최대 수 | 1~1,000,000 | 4 |
| timeout_ms | 시도 한 번의 시간 제한. 0 이면 제한이 없다 | 0~1,000,000,000 | 0 |
| retries | 블록 하나를 다시 시도하는 최대 횟수 | 0~10 | 0 |
| backoff_ms | 첫 번째로 다시 시도하기 전에 기다리는 시간 | 0~1,000,000,000 | 0 |
| trace | exec 줄과 clock 줄을 출력할지 | true, false | false |

[4.6] concurrency 는 1 이상 1,000,000 이하, timeout_ms 와 backoff_ms 는 0 이상 1,000,000,000 이하, retries 는 0 이상 10 이하의 정수이고 trace 는 불리언이다. 기본값은 concurrency 가 4 이고 timeout_ms, retries, backoff_ms 가 0 이며 trace 가 false 다. 1단계 4.13 처럼 필드 하나라도 오류이면 config 는 어떤 필드도 바꾸지 않는다.

### 도구 단계

[4.7] 도구 단계에서 실행기는 먼저 블록마다 2단계 4.25 의 순서로 도구 정의, 스키마 검증, 가짜 결과를 확인한다. 도구 정의가 없거나 검증 오류가 있거나 가짜 결과가 없는 블록은 실행하지 않는다. 그런 블록의 결과는 1단계와 2단계가 정한 오류 결과이고 시간을 쓰지 않는다. 가짜 결과가 있는 블록을 실행할 블록이라 한다.

[4.8] 도구 단계의 가상 시각은 0 에서 시작한다. 단계마다 다시 0 에서 시작하고 앞 단계의 시각을 이어받지 않는다.

[4.9] 슬롯은 시도 하나를 실행하는 자리이고 한 도구 단계에 concurrency 개가 있다. 단계를 시작할 때 모든 슬롯은 비어 있다. 시도는 시작하는 시각에 빈 슬롯 하나를 차지하고 끝나는 시각에 그 슬롯을 비운다. 시도가 어느 빈 슬롯을 차지하는지는 출력에 영향을 주지 않는다.

[4.10] 블록 하나의 실행은 시도를 하나 이상 차례로 하는 것이다. 다음 시도를 시작할 수 있는 블록을 준비된 블록이라 한다. 단계를 시작하는 시각 0 에는 실행할 블록이 모두 준비된 블록이다. 빈 슬롯과 준비된 블록이 함께 있으면 실행기는 준비된 블록 가운데 블록 순서가 가장 앞선 블록의 다음 시도를 그 시각에 시작한다. 시도를 시작한 블록은 준비된 블록에서 빠진다. 실행기는 빈 슬롯이나 준비된 블록이 없어질 때까지 이 배정을 되풀이한다.

[4.11] 시도 객체의 ms 를 d 라 하자. timeout_ms 가 0 이거나 d 가 timeout_ms 이하이면 그 시도는 시작 시각에서 d 가 지난 시각에 끝난다. 시도 객체에 output 이 있으면 성공이고 error 가 있으면 실패다. timeout_ms 가 1 이상이고 d 가 timeout_ms 보다 크면 그 시도는 시작 시각에서 timeout_ms 가 지난 시각에 시간 초과로 실패한다. d 가 timeout_ms 와 같으면 시간 초과가 아니다.

[4.12] 시도가 성공하면 블록의 실행이 끝난다. 시도가 실패했을 때 도구에 부수효과가 없고 지금까지 한 시도의 수가 retries 이하이면 블록은 다시 시도한다. 도구에 부수효과가 있거나 지금까지 한 시도의 수가 retries 보다 크면 블록의 실행은 그 실패로 끝난다. 그래서 한 블록의 시도는 많아야 retries + 1 번이다. 다시 시도할 블록이 언제 다시 준비된 블록이 되는지는 4.21 이 정한다.

[4.13] 블록의 결과는 마지막 시도로 정한다. 마지막 시도가 성공이면 내용이 그 시도의 output 인 정상 결과다. error 로 실패했으면 내용이 그 시도의 error 인 오류 결과다. 시간 초과로 실패했으면 오류 결과이고 내용은 부수효과가 없는 도구이면 `timeout after <timeout_ms> ms`, 부수효과가 있는 도구이면 `timeout after <timeout_ms> ms; outcome unknown` 이다. `<timeout_ms>` 자리에는 설정값을 10진수로 쓴다.

[4.14] 도구 단계가 끝나는 시각은 실행할 블록의 실행이 끝나는 시각 가운데 가장 늦은 것이다. 실행할 블록이 없으면 0 이다.

[4.15] 실행기는 실행할 블록의 결과와 실행하지 않은 블록의 결과를 모두 도구 사용 블록의 순서대로 사용자 메시지 하나에 담는다. 1단계 4.42 와 같은 순서다. 끝난 시각의 순서는 결과 순서에 영향을 주지 않는다.

[4.16] 2단계 4.27 의 같은 오류 세기는 블록의 결과만 센다. 다시 시도하기 전에 실패한 시도는 세지 않는다.

### exec 줄과 clock 줄

[4.17] trace 가 true 이면 실행기는 도구 단계마다 결과 블록 줄을 출력하기 전에 4.18~4.20 의 exec 줄과 clock 줄을 출력한다. trace 가 false 이면 두 줄을 출력하지 않는다.

[4.18] exec 줄은 실행할 블록마다 하나씩 `exec <id> <시작 시각> <끝난 시각> <결과> <시도 수>` 형식으로 출력한다. 시작 시각은 첫 시도가 시작한 시각이고 끝난 시각은 마지막 시도가 끝난 시각이다. 두 시각 사이에는 다시 시도하기 전에 기다린 시간과 빈 슬롯을 기다린 시간도 들어간다. 결과는 마지막 시도가 성공이면 `ok`, error 로 실패했으면 `error`, 시간 초과로 실패했으면 `timeout` 이다. 시도 수는 그 블록이 한 시도의 수다.

[4.19] exec 줄은 끝난 시각이 이른 블록부터 출력한다. 끝난 시각이 같으면 도구 사용 블록의 순서가 앞선 것부터 출력한다. 실행하지 않은 블록의 exec 줄은 없다.

[4.20] 실행기는 exec 줄을 모두 출력한 뒤 `clock <도구 단계가 끝나는 시각>` 을 출력한다. 실행할 블록이 없어도 trace 가 true 이면 `clock 0` 을 출력한다.

### 다시 시도 전 대기와 같은 시각의 일

[4.21] 다시 시도할 블록은 실패한 시도가 끝난 시각에서 대기 시간이 지난 시각에 다시 준비된 블록이 된다. 블록이 k 번째로 다시 시도하기 전의 대기 시간은 backoff_ms × 2^(k−1) 이다. 첫 번째로 다시 시도하기 전에는 backoff_ms 만큼, 두 번째로 다시 시도하기 전에는 그 두 배만큼 기다린다. backoff_ms 가 0 이면 대기 시간도 0 이므로 블록은 실패한 시각에 바로 다시 준비된 블록이 된다.

[4.22] 대기하는 블록은 슬롯을 차지하지 않는다. 대기하는 동안 빈 슬롯은 4.10 에 따라 다른 준비된 블록이 받는다. 다시 준비된 블록도 다른 준비된 블록과 함께 블록 순서로 빈 슬롯을 받으므로 빈 슬롯이 없으면 슬롯이 빌 때까지 준비된 채로 기다린다. 이 규칙들에 따르면 backoff_ms 가 0 일 때 다시 시도할 블록은 실패한 시각에 바로 다음 시도를 시작한다.

[4.23] 같은 시각에 여러 일이 일어나면 실행기는 그 시각에 끝나는 시도와 그 시각에 대기가 끝나는 블록을 모두 반영한 뒤에 4.10 의 배정을 한다. 그래서 시각 t 에 끝난 시도가 비운 슬롯과 시각 t 에 다시 준비된 블록이 모두 시각 t 의 배정에 들어간다. 시각 t 의 배정으로 시작한 시도가 4.11 에 따라 시각 t 에 끝나면 실행기는 그 끝을 반영한 뒤 시각 t 의 배정을 다시 한다.

## 5. 오류 규칙

[5.1] tool 의 side_effect 가 불리언이 아니면 `error bad_field` 를 출력한다.

[5.2] fake 의 ms 가 0 이상 1,000,000,000 이하의 정수가 아니면 `error bad_field` 를 출력한다.

[5.3] fake 에 attempts 와 output, error, ms 가운데 하나가 함께 있으면 `error bad_field` 를 출력한다. attempts 가 배열이 아니거나 빈 배열이어도 `error bad_field` 다. attempts 의 원소가 객체가 아니거나 output 과 error 가운데 정확히 하나를 가지지 않거나 원소의 ms 가 4.2 의 범위 밖이어도 `error bad_field` 다. 원소의 output 이나 error 가 문자열이 아니어도 `error bad_field` 다.

[5.4] config 의 concurrency, timeout_ms, retries, backoff_ms 가 4.6 의 범위 안의 정수가 아니거나 trace 가 불리언이 아니면 `error bad_field` 를 출력한다.

## 6. 예시

[6.1] 예시의 표기는 1단계와 같다. 예시 블록 하나는 프로그램을 한 번 실행한 기록이다.

[6.2] 슬롯이 둘이다. a1 과 b1 이 시각 0 에 시작하고 b1 이 끝난 100 에 c1 이, a1 이 끝난 300 에 p1 이 시작한다. c1 의 첫 시도는 800 이 걸려 500 뒤인 600 에 시간 초과로 실패하고 두 번째 시도가 600 부터 200 동안 실행되어 800 에 성공한다. p1 은 부수효과가 있는 도구라 시간 초과 뒤에 다시 시도하지 않는다. mail 은 정의되지 않은 도구라 실행하지 않는다. exec 줄은 끝난 순서로, 결과 줄은 블록 순서로 나온다.

```
> config {"concurrency":2,"timeout_ms":500,"retries":1,"trace":true}
> tool {"name":"fetch"}
> tool {"name":"pay","side_effect":true}
> fake {"tool":"fetch","input":{"url":"a"},"output":"A","ms":300}
> fake {"tool":"fetch","input":{"url":"b"},"output":"B","ms":100}
> fake {"tool":"fetch","input":{"url":"c"},"attempts":[{"output":"늦은 C","ms":800},{"output":"C","ms":200}]}
> fake {"tool":"pay","input":{"order":1},"output":"결제됨","ms":700}
> reply {"stop":"tool","content":[{"type":"tool","id":"a1","name":"fetch","input":{"url":"a"}},{"type":"tool","id":"b1","name":"fetch","input":{"url":"b"}},{"type":"tool","id":"c1","name":"fetch","input":{"url":"c"}},{"type":"tool","id":"p1","name":"pay","input":{"order":1}},{"type":"tool","id":"m1","name":"mail","input":{}}]}
> reply {"stop":"end","content":[{"type":"text","text":"결제가 됐는지 확인해야 한다."}]}
> ask {"text":"세 페이지를 읽고 결제해 줘"}
user text "세 페이지를 읽고 결제해 줘"
turn 1 messages=1 limit=1000
model tool a1 fetch {"url":"a"}
model tool b1 fetch {"url":"b"}
model tool c1 fetch {"url":"c"}
model tool p1 pay {"order":1}
model tool m1 mail {}
stop tool
exec b1 0 100 ok 1
exec a1 0 300 ok 1
exec c1 100 800 ok 2
exec p1 300 800 timeout 1
clock 800
user result a1 ok "A"
user result b1 ok "B"
user result c1 ok "C"
user result p1 error "timeout after 500 ms; outcome unknown"
user result m1 error "unknown tool: mail"
turn 2 messages=3 limit=1000
model text "결제가 됐는지 확인해야 한다."
stop end
done end "결제가 됐는지 확인해야 한다."
```

[6.3] concurrency 의 영향이다. 첫 ask 는 기본값 4 를 쓴다. r3 은 검증 오류, r6 은 가짜 결과가 없어 실행하지 않으므로 exec 줄이 없다. r2 와 r5 가 같은 시각 100 에 끝나 블록 순서로 나오고 r7 은 그때 빈 슬롯에서 시작한다. config 로 바꾼 concurrency 1 은 다음 ask 부터 쓰이므로 둘째 ask 는 블록을 하나씩 실행한다.

```
> config {"trace":true}
> tool {"name":"read"}
> tool {"name":"lint","schema":{"type":"object","required":["path"]}}
> fake {"tool":"read","input":{"path":"a.kt"},"output":"30줄","ms":400}
> fake {"tool":"read","input":{"path":"b.kt"},"output":"12줄","ms":100}
> fake {"tool":"read","input":{"path":"c.kt"},"output":"50줄","ms":250}
> fake {"tool":"read","input":{"path":"d.kt"},"output":"8줄","ms":100}
> fake {"tool":"read","input":{"path":"e.kt"},"output":"21줄","ms":50}
> reply {"stop":"tool","content":[{"type":"tool","id":"r1","name":"read","input":{"path":"a.kt"}},{"type":"tool","id":"r2","name":"read","input":{"path":"b.kt"}},{"type":"tool","id":"r3","name":"lint","input":{}},{"type":"tool","id":"r4","name":"read","input":{"path":"c.kt"}},{"type":"tool","id":"r5","name":"read","input":{"path":"d.kt"}},{"type":"tool","id":"r6","name":"read","input":{"path":"f.kt"}},{"type":"tool","id":"r7","name":"read","input":{"path":"e.kt"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"다섯 파일을 읽었다."}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"s1","name":"read","input":{"path":"a.kt"}},{"type":"tool","id":"s2","name":"read","input":{"path":"b.kt"}},{"type":"tool","id":"s3","name":"read","input":{"path":"c.kt"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"하나씩 읽었다."}]}
> ask {"text":"파일을 읽어 줘"}
user text "파일을 읽어 줘"
turn 1 messages=1 limit=1000
model tool r1 read {"path":"a.kt"}
model tool r2 read {"path":"b.kt"}
model tool r3 lint {}
model tool r4 read {"path":"c.kt"}
model tool r5 read {"path":"d.kt"}
model tool r6 read {"path":"f.kt"}
model tool r7 read {"path":"e.kt"}
stop tool
exec r2 0 100 ok 1
exec r5 0 100 ok 1
exec r7 100 150 ok 1
exec r4 0 250 ok 1
exec r1 0 400 ok 1
clock 400
user result r1 ok "30줄"
user result r2 ok "12줄"
user result r3 error "invalid input: /path required"
user result r4 ok "50줄"
user result r5 ok "8줄"
user result r6 error "no result for this input"
user result r7 ok "21줄"
turn 2 messages=3 limit=1000
model text "다섯 파일을 읽었다."
stop end
done end "다섯 파일을 읽었다."
> config {"concurrency":1}
> ask {"text":"하나씩 다시 읽어 줘"}
user text "하나씩 다시 읽어 줘"
turn 1 messages=5 limit=1000
model tool s1 read {"path":"a.kt"}
model tool s2 read {"path":"b.kt"}
model tool s3 read {"path":"c.kt"}
stop tool
exec s1 0 400 ok 1
exec s2 400 500 ok 1
exec s3 500 750 ok 1
clock 750
user result s1 ok "30줄"
user result s2 ok "12줄"
user result s3 ok "50줄"
turn 2 messages=7 limit=1000
model text "하나씩 읽었다."
stop end
done end "하나씩 읽었다."
```

[6.4] 시간 제한과 다시 시도다. q1 은 300 이 걸리고 시간 제한도 300 이라 성공한다. q2 의 첫 시도는 50 에 error 로 실패하고 둘째 시도는 시간 초과로 350 에 실패한다. 셋째 시도는 attempts 의 마지막 시도 객체를 다시 써서 650 에 시간 초과로 실패하고 retries 2 를 다 써서 끝난다. o1 은 부수효과가 있는 도구라 error 뒤에 다시 시도하지 않는다. 같은 오류를 셀 때는 블록의 결과만 센다. q2 의 둘째 시도가 350 에 낸 시간 초과는 블록의 결과가 아니므로 첫 차례에 센 `timeout after 300 ms` 는 q2 의 결과 한 번이다. 둘째 차례에서 q3 의 결과로 같은 오류가 두 번이 되어 루프가 멈춘다.

```
> config {"timeout_ms":300,"retries":2,"trace":true,"repeat_limit":2}
> tool {"name":"quote"}
> tool {"name":"order","side_effect":true}
> fake {"tool":"quote","input":{"sku":"A"},"output":"1200원","ms":300}
> fake {"tool":"quote","input":{"sku":"B"},"attempts":[{"error":"busy","ms":50},{"output":"900원","ms":400}]}
> fake {"tool":"order","input":{"sku":"A"},"error":"out of stock","ms":100}
> reply {"stop":"tool","content":[{"type":"tool","id":"q1","name":"quote","input":{"sku":"A"}},{"type":"tool","id":"q2","name":"quote","input":{"sku":"B"}},{"type":"tool","id":"o1","name":"order","input":{"sku":"A"}}]}
> reply {"stop":"tool","content":[{"type":"text","text":"B 가격을 한 번 더 묻는다."},{"type":"tool","id":"q3","name":"quote","input":{"sku":"B"}}]}
> ask {"text":"A 와 B 가격을 보고 A 를 주문해 줘"}
user text "A 와 B 가격을 보고 A 를 주문해 줘"
turn 1 messages=1 limit=1000
model tool q1 quote {"sku":"A"}
model tool q2 quote {"sku":"B"}
model tool o1 order {"sku":"A"}
stop tool
exec o1 0 100 error 1
exec q1 0 300 ok 1
exec q2 0 650 timeout 3
clock 650
user result q1 ok "1200원"
user result q2 error "timeout after 300 ms"
user result o1 error "out of stock"
turn 2 messages=3 limit=1000
model text "B 가격을 한 번 더 묻는다."
model tool q3 quote {"sku":"B"}
stop tool
exec q3 0 650 timeout 3
clock 650
user result q3 error "timeout after 300 ms"
done repeated_error "B 가격을 한 번 더 묻는다."
```

[6.5] 다시 시도 전 대기다. 슬롯이 둘이고 backoff_ms 가 100 이다. a1 의 첫 시도는 50 에 실패하고 a1 은 슬롯을 비운 채 150 까지 기다린다. 그 슬롯은 그때 준비된 블록 가운데 블록 순서가 가장 앞선 c1 이 50 에 받는다. 150 에는 c1 이 끝나고 a1 이 다시 준비된다. 실행기는 두 일을 모두 반영한 뒤 배정하므로 블록 순서가 앞선 a1 이 d1 보다 먼저 슬롯을 받는다. d1 은 a1 이 끝난 250 에 시작한다. d1 의 첫 시도는 걸리는 시간이 0 이라 250 에 실패하고 d1 은 100 을 기다린다. 둘째 시도는 350 부터 50 동안 실행되어 400 에 실패한다. 두 번째로 다시 시도하기 전에는 200 을 기다리므로 셋째 시도는 600 에 시작해 610 에 성공한다.

```
> config {"concurrency":2,"retries":2,"backoff_ms":100,"trace":true}
> tool {"name":"crawl"}
> fake {"tool":"crawl","input":{"url":"a"},"attempts":[{"error":"busy","ms":50},{"output":"A","ms":100}]}
> fake {"tool":"crawl","input":{"url":"b"},"output":"B","ms":300}
> fake {"tool":"crawl","input":{"url":"c"},"output":"C","ms":100}
> fake {"tool":"crawl","input":{"url":"d"},"attempts":[{"error":"busy"},{"error":"busy","ms":50},{"output":"D","ms":10}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"a1","name":"crawl","input":{"url":"a"}},{"type":"tool","id":"b1","name":"crawl","input":{"url":"b"}},{"type":"tool","id":"c1","name":"crawl","input":{"url":"c"}},{"type":"tool","id":"d1","name":"crawl","input":{"url":"d"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"네 페이지를 모두 읽었다."}]}
> ask {"text":"네 페이지를 읽어 줘"}
user text "네 페이지를 읽어 줘"
turn 1 messages=1 limit=1000
model tool a1 crawl {"url":"a"}
model tool b1 crawl {"url":"b"}
model tool c1 crawl {"url":"c"}
model tool d1 crawl {"url":"d"}
stop tool
exec c1 50 150 ok 1
exec a1 0 250 ok 2
exec b1 0 300 ok 1
exec d1 250 610 ok 3
clock 610
user result a1 ok "A"
user result b1 ok "B"
user result c1 ok "C"
user result d1 ok "D"
turn 2 messages=3 limit=1000
model text "네 페이지를 모두 읽었다."
stop end
done end "네 페이지를 모두 읽었다."
```

[6.6] 입력 오류다. 마지막 config 의 `1e9` 는 정수 1,000,000,000 이다.

```
> fake {"tool":"read","input":{"path":"a"},"output":"A","attempts":[{"output":"A"}]}
error bad_field
> fake {"tool":"read","input":{"path":"a"},"attempts":[]}
error bad_field
> fake {"tool":"read","input":{"path":"a"},"attempts":[{"output":"A","error":"x"}]}
error bad_field
> fake {"tool":"read","input":{"path":"a"},"attempts":[{"ms":10}]}
error bad_field
> fake {"tool":"read","input":{"path":"a"},"output":"A","ms":1000000001}
error bad_field
> fake {"tool":"read","input":{"path":"a"},"output":"A","ms":2.5}
error bad_field
> tool {"name":"read","side_effect":"yes"}
error bad_field
> config {"concurrency":0}
error bad_field
> config {"timeout_ms":-1}
error bad_field
> config {"retries":11}
error bad_field
> config {"trace":1}
error bad_field
> config {"backoff_ms":-1}
error bad_field
> config {"backoff_ms":1000000001}
error bad_field
> config {"concurrency":1000000,"timeout_ms":1e9,"retries":10,"backoff_ms":1e9,"trace":false}
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계와 같다.

[7.2] 대규모 테스트는 도구 단계 4,000번 안팎과 도구 사용 블록 40,000개 안팎으로 이뤄진다. concurrency 는 1 부터 1,000,000 까지, ms 와 backoff_ms 는 1,000,000,000 까지, retries 는 10 까지 섞어 쓰고 도구 단계의 대부분에서 backoff_ms 가 1 이상이다. 도구 단계의 80% 이상에서 concurrency 가 1,000,000 이고 이런 단계에서 실행하는 블록은 20,000개가 넘는다. 한 응답에 도구 사용 블록이 2,000개 넘게 든 차례도 있다. trace 를 켠 ask 와 끈 ask 가 섞여 있다. 제한 시간은 15초다. 가상 시각을 1 씩 늘려 가며 시뮬레이션하는 풀이와 시도나 대기가 끝날 때마다 슬롯 concurrency 개를 모두 살피는 풀이는 이 제한 시간을 넘기도록 규모를 정했다.

[7.3] 3단계 검사 입력에는 1단계 3.12, 3.13 이 출력을 정하지 않은 줄 가운데 2단계와 이 문서가 뜻을 정하지 않은 줄이 나오지 않는다.

[7.4] 4단계부터 마지막 단계까지 검사할 때도 3단계 테스트를 다시 실행한다.
