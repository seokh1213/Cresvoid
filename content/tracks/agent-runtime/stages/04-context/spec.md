# 4단계: 컨텍스트 예산

## 1. 개요

[1.1] 4단계는 모델에 보내는 요청의 크기를 토큰으로 센다. 요청에는 도구 정의와 대화 기록이 모두 들어간다. 요청의 토큰 수가 설정한 기준을 넘으면 실행기는 오래된 도구 결과의 내용을 자리표시로 바꿔 요청을 줄이고 무엇을 바꿨는지 출력한다. 그래도 요청이 모델의 컨텍스트 창보다 크면 모델이 요청을 거절한다.

[1.2] 1~3단계의 입출력 규칙과 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 3.13 이 출력을 정하지 않은 필드 가운데 이 문서가 뜻을 정하는 것은 아래 필드뿐이다. 나머지 필드와 1단계 3.12 의 명령어는 이 문서도 출력을 정하지 않는다.

| 명령 | 새 필드 | 뜻 |
|---|---|---|
| `config` | `context_window` | 모델이 받는 요청의 최대 토큰 수 |
| `config` | `clear_trigger` | 요청의 토큰 수가 이 값보다 크면 결과 지우기를 시작하는 기준 |
| `config` | `clear_target` | 요청의 토큰 수가 이 값 이하가 되면 결과 지우기를 멈추는 기준 |
| `config` | `clear_keep` | 지우지 않을 최근 결과 블록의 수 |
| `config` | `clear_exclude` | 결과를 지우지 않을 도구 이름의 목록 |

## 2. 배경 개념

[2.1] 모델이 응답을 만들 때 참조할 수 있는 글의 양에는 상한이 있다. 이 상한을 컨텍스트 창이라 하고 토큰 수로 잰다. Claude API 문서는 시스템 프롬프트, 도구 결과를 포함한 모든 메시지, 도구 정의가 모두 컨텍스트 창에 들어간다고 적는다. 입력만으로 창을 넘는 요청은 "prompt is too long" 오류로 거절된다. 모델이 만드는 출력도 창에 들어가지만 입력과 출력 한도의 합이 창을 넘을 때 요청을 받는지는 모델마다 다르다. 이 트랙은 입력만 센다.

[2.2] 토큰은 모델이 글을 나누는 단위다. 같은 글도 모델의 토크나이저에 따라 토큰 수가 다르므로 실행기는 보내기 전에 정확한 수를 알기 어렵다. Claude API 는 요청을 보내기 전에 토큰 수를 어림하는 토큰 세기 API 를 따로 둔다. 이 트랙은 실제 토크나이저 대신 손으로 셀 수 있는 규칙(3.3, 4.1~4.3)으로 토큰 수를 정한다. 이 규칙이 실제 토크나이저와 같은 수를 내지는 않는다.

[2.3] 창 안에 들어간다고 해서 다 좋은 것은 아니다. Anthropic 의 글 "Effective context engineering for AI agents" 는 컨텍스트의 토큰이 늘수록 모델이 그 안의 정보를 정확히 떠올리는 능력이 떨어진다고 설명하고 이를 context rot 라 부른다. 같은 글은 컨텍스트를 한계 효용이 줄어드는 유한한 자원으로 다루라고 권한다. 그래서 실행기는 창을 넘기 전에도 쓸모가 다한 내용을 덜어 낸다.

[2.4] 에이전트의 컨텍스트를 채우는 것은 주로 도구 정의와 쌓인 도구 결과다. Claude 문서 "Manage tool context" 는 도구가 많거나 차례가 많은 에이전트가 일을 끝내기 전에 컨텍스트를 다 쓸 수 있다고 적는다. 도구 정의는 요청마다 통째로 들어가므로 도구가 많으면 대화가 짧아도 요청이 크다. 도구 결과는 차례마다 쌓인다.

[2.5] "Effective context engineering for AI agents" 는 창이 거의 찬 대화를 요약해 그 요약으로 새 창을 시작하는 일을 컴팩션이라 부르고 오래된 도구 결과 지우기를 가장 안전하고 가벼운 컴팩션의 하나로 꼽는다. 기록 깊숙이 묻힌 도구 결과를 에이전트가 원문 그대로 다시 볼 필요는 거의 없기 때문이다. Claude API 의 context editing 은 요청이 기준을 넘으면 가장 오래된 도구 결과부터 지우고 지운 결과를 자리표시 글로 바꾼다. 최근 결과 몇 개를 남기거나 어떤 도구의 결과는 지우지 않거나 한 번에 적어도 얼마를 지울지 정하는 설정도 있다. Claude API 는 이 편집을 서버에서 하고 클라이언트는 원래 기록을 그대로 둔다. 이 트랙의 실행기는 같은 일을 자기 기록을 고쳐서 한다.

[2.6] 결과 블록을 기록에서 없애면 그 결과가 답하던 도구 사용 블록의 짝이 사라져 요청이 1단계 4.29 의 짝 규칙을 어긴다. 도구 사용 블록까지 함께 없애면 모델 메시지가 비거나 같은 쪽의 메시지가 잇달아 나올 수 있다. 그래서 지우기는 블록을 그 자리에 두고 내용만 자리표시로 바꾼다. 식별자와 오류 표시가 남으므로 짝은 깨지지 않고 모델은 그 자리에 결과가 있었다는 것을 안다. 모델이 아직 받아 보지 못한 결과는 지우지 않는다. 지우면 도구를 실행한 의미가 없어진다. 내용을 자리표시로 바꿔도 블록의 토큰 수가 줄지 않는 결과도 지우지 않는다. 그런 결과를 지우면 요청은 작아지지 않고 모델이 볼 정보만 사라진다.

[2.7] 지우기를 언제 시작해 언제 멈출지도 정해야 한다. 기준을 넘을 때마다 기준 바로 아래까지만 지우면 다음 차례에 결과 하나만 더해져도 다시 기준을 넘으므로 차례마다 지우기가 일어난다. Claude API 문서는 지우기가 캐시해 둔 프롬프트 앞부분을 무효로 만들므로 한 번에 충분히 지우라고 권한다. 이 트랙은 지우기를 시작하는 기준보다 낮은 목표까지 지우게 해서 지우기가 차례마다 되풀이되지 않게 한다.

[2.8] 지워도 요청이 창보다 크면 그대로는 보낼 수 없다. 실제 API 는 이런 요청을 거절하므로 실행기는 대화를 요약하거나 새로 시작하는 것 같은 더 큰 처리를 해야 한다. 이 트랙에서는 모델이 요청을 거절하고 실행기는 1단계 4.32 대로 기록을 비운다. 마지막 메시지의 결과처럼 지울 수 없는 내용이 크거나 도구 정의만으로 창이 거의 찰 때 이런 일이 생긴다.

## 3. 입출력 규칙

[3.1] 1~3단계의 입출력 규칙을 그대로 따른다.

[3.2] 이 문서에서 글자 수는 유니코드 코드 포인트의 수다. U+FFFF 보다 큰 코드 포인트도 한 글자다. `🙂`(U+1F642)도 `가` 처럼 한 글자다.

[3.3] 글자열의 토큰 수는 그 글자열의 글자 수를 4 로 나눈 값을 올림한 정수다. 글자 수가 1~4 이면 1 토큰이고 5~8 이면 2 토큰이다.

[3.4] 이 문서가 출력하는 토큰 수는 10진수로 쓰고 앞에 0 을 붙이지 않는다.

## 4. 명령 규칙

### 요청의 토큰 수

[4.1] 요청의 토큰 수는 요청에 든 도구 정의 하나하나의 토큰 수(4.3)와 기록의 블록 하나하나의 토큰 수(4.2)를 모두 더한 값이다. 1단계 4.22 대로 요청에는 그때 정의된 도구 정의 전체와 대화 기록 전체가 들어간다. 응답 한도는 요청의 토큰 수에 들어가지 않는다.

[4.2] 기록에 든 블록의 토큰 수는 그 블록을 1단계 4.44, 4.45 의 형식으로 쓴 줄의 토큰 수다. 블록이 사용자 메시지에 있으면 줄은 `user` 로, 모델 메시지에 있으면 `model` 로 시작한다. 줄 끝의 LF 는 세지 않는다. 줄은 출력할 때와 같은 글자로 센다. 문자열 안의 LF 는 출력에서 `\n` 이 되므로 두 글자이고 `"` 는 `\"` 가 되므로 두 글자다. 4.13 에서 내용을 바꾼 결과 블록은 바뀐 내용으로 쓴 줄로 센다.

[4.3] 도구 정의의 토큰 수는 그 도구를 마지막으로 정의한 tool 명령의 인자 객체를 1단계 3.9, 3.10 의 형식으로 쓴 JSON 의 토큰 수다. 인자 객체의 필드는 종류를 가리지 않고 모두 들어간다. `tool {"name":"read", "side_effect":false}` 로 정의한 도구의 JSON 은 `{"name":"read","side_effect":false}` 이고 35 글자이므로 토큰 수는 9 다.

### 컨텍스트 창

[4.4] `config` 의 인자에는 `context_window` 필드가 있을 수 있다. context_window 는 0 이상 1,000,000,000 이하의 정수이고 기본값은 0 이다. 1 이상이면 그 값이 모델의 컨텍스트 창 크기이고 0 이면 창 크기에 한도가 없다.

[4.5] context_window 가 1 이상이면 실행기는 차례마다 `context <요청의 토큰 수>` 를 출력한다. 그 차례에 4.8~4.14 의 지우기를 했으면 이 줄의 토큰 수는 지우기를 마친 뒤의 값이다. 이 값은 모델이 4.17 에서 검사하는 값과 같다. context_window 가 0 이면 이 줄을 출력하지 않는다.

### 결과 지우기의 설정

[4.6] `config` 의 인자에는 아래 네 필드가 있을 수 있다.

| 필드 | 뜻 | 범위 | 기본값 |
|---|---|---|---|
| clear_trigger | 요청의 토큰 수가 이 값보다 크면 지우기를 한다. 0 이면 지우지 않는다 | 0~1,000,000,000 | 0 |
| clear_target | 요청의 토큰 수가 이 값 이하가 되면 지우기를 멈춘다 | 0~1,000,000,000 | 0 |
| clear_keep | 지우지 않는 최근 결과 블록의 수 | 0~1,000,000 | 0 |
| clear_exclude | 결과를 지우지 않는 도구 이름의 목록 | 문자열의 배열 | 빈 배열 |

[4.7] clear_trigger 와 clear_target 은 0 이상 1,000,000,000 이하, clear_keep 은 0 이상 1,000,000 이하의 정수다. clear_exclude 는 원소가 모두 문자열인 배열이다. 빈 배열이어도 되고 같은 문자열이 둘 이상 있어도 된다. 기본값은 clear_trigger, clear_target, clear_keep 이 0 이고 clear_exclude 가 빈 배열이다. clear_exclude 가 있는 config 는 이전 목록을 모두 버리고 새 목록으로 바꾼다. 실행기는 인자의 필드를 지금 설정에 적용한 결과를 먼저 만든다. 그 결과에서 clear_trigger 가 1 이상이고 clear_target 이 clear_trigger 이상이면 오류다. clear_trigger 가 0 이면 4.8 에 따라 지우기를 하지 않으므로 clear_target 과의 관계를 따지지 않는다. 1단계 4.13 처럼 필드 하나라도 오류이면 config 는 어떤 필드도 바꾸지 않는다.

### 결과 지우기

[4.8] 차례를 시작할 때 실행기는 1단계 4.23 의 turn 줄을 출력한 다음 지우기를 할지 정한다. clear_trigger 가 1 이상이고 그때 요청의 토큰 수가 clear_trigger 보다 크면 지우기를 한다. 그렇지 않으면 하지 않는다. 지우기는 4.9~4.14 대로 기록에 든 결과 블록 가운데 일부의 내용을 바꾼다.

[4.9] 지우기는 기록의 결과 블록을 오래된 것부터 하나씩 살핀다. 앞 메시지의 블록이 뒤 메시지의 블록보다 오래되었고 같은 메시지 안에서는 블록 순서가 앞선 블록이 오래되었다.

[4.10] 실행기는 결과 블록 하나를 살피기 전에 요청의 토큰 수가 clear_target 이하인지 확인한다. 이하이면 지우기를 멈추고 남은 블록은 살피지 않는다. 마지막 결과 블록까지 살피면 지우기가 끝난다. 지우기를 마친 뒤 요청의 토큰 수는 clear_target 보다 클 수 있다.

[4.11] 살피는 결과 블록이 아래 다섯 가지 가운데 하나라도 해당하면 지우지 않고 다음 블록으로 넘어간다. 첫째, 이미 지운 블록이다. 둘째, 기록의 마지막 메시지에 든 블록이다. 셋째, 기록의 결과 블록을 모두 4.9 의 순서로 늘어놓았을 때 뒤에서 clear_keep 개 안에 드는 블록이다. 이미 지운 블록과 마지막 메시지의 블록도 이 개수에 들어간다. 넷째, 블록의 도구 이름(4.12)이 clear_exclude 의 원소 가운데 하나와 같다. 다섯째, 내용을 자리표시로 바꾸면 블록의 토큰 수가 지금보다 작아지지 않는다. 다섯 가지 가운데 어느 것에도 해당하지 않으면 그 블록을 지운다.

[4.12] 결과 블록의 도구 이름은 그 결과 블록이 답하는 도구 사용 블록의 name 이다. 사용자 메시지의 k 번째 블록인 결과 블록은 바로 앞 모델 메시지의 k 번째 도구 사용 블록에 답한다(1단계 4.29). 내용이 `unknown tool: ` 으로 시작하는 오류 결과와 1단계 4.19 의 `not executed` 오류 결과도 같은 방법으로 도구 이름을 정한다.

[4.13] 결과 블록을 지우면 블록의 내용이 자리표시 `[cleared]` 로 바뀐다. 블록은 기록의 같은 자리에 남고 id 와 오류 표시는 바뀌지 않는다. 오류 결과를 지우면 내용이 `[cleared]` 인 오류 결과가 된다. 블록을 하나 지우면 요청의 토큰 수는 그 블록의 토큰 수가 준 만큼 준다.

[4.14] 지운 내용은 되살리지 않는다. 지운 블록은 그 뒤의 요청과 ask 에도 바뀐 내용으로 들어간다. reset 이나 모델의 거절로 기록을 비우면 지운 블록도 함께 사라진다. 지운 블록의 줄은 다시 출력하지 않는다.

[4.15] 지우기에서 블록을 하나 이상 지웠으면 실행기는 `clear <지우기 전 토큰 수> <지운 뒤 토큰 수> <id> <id> ...` 한 줄을 출력한다. 지우기 전 토큰 수는 지우기를 시작할 때의 요청의 토큰 수이고 지운 뒤 토큰 수는 지우기를 마친 뒤의 요청의 토큰 수다. id 자리에는 지운 결과 블록의 id 를 지운 순서대로 공백 하나씩으로 나눠 쓴다. 지운 블록이 없으면 이 줄을 출력하지 않는다.

[4.16] 한 차례의 줄은 turn 줄, clear 줄, context 줄 순서로 나온다. 그 뒤에 1단계대로 모델의 응답이나 거절이 나온다. 1단계 4.38 의 다시 보내는 차례와 차례 번호가 max_turns 와 같은 차례도 이 순서를 따른다.

### 컨텍스트 창 검사

[4.17] 모델의 요청 검사에 규칙 하나가 더해진다. 코드는 context_window 이고 1단계 4.29 의 pairing 규칙 다음에 검사한다. context_window 가 1 이상이고 요청의 토큰 수가 context_window 보다 크면 이 규칙을 어긴다. 2단계 4.12 의 여섯 규칙은 이 단계부터 일곱 규칙이다. 각본에 남은 응답이 있는지는 요청이 일곱 규칙을 모두 지켰을 때만 확인한다.

[4.18] 모델은 4.8~4.14 의 지우기가 끝난 뒤의 요청을 검사한다. 지우기로도 요청이 창 안에 들지 않으면 모델이 context_window 로 요청을 거절한다. 실행기는 1단계 4.32 대로 `reject context_window` 와 `done rejected ""` 를 출력하고 기록을 비운다.

## 5. 오류 규칙

[5.1] config 의 context_window, clear_trigger, clear_target 이 0 이상 1,000,000,000 이하의 정수가 아니거나 clear_keep 이 0 이상 1,000,000 이하의 정수가 아니면 `error bad_field` 를 출력한다.

[5.2] config 의 clear_exclude 가 배열이 아니거나 문자열이 아닌 원소가 있으면 `error bad_field` 를 출력한다.

[5.3] 4.7 대로 적용한 결과에서 clear_trigger 가 1 이상이고 clear_target 이 clear_trigger 이상이면 `error bad_field` 를 출력한다. 오류를 낸 config 는 어떤 필드도 바꾸지 않는다.

## 6. 예시

[6.1] 예시의 표기는 1단계와 같다. 예시 블록 하나는 프로그램을 한 번 실행한 기록이다.

[6.2] 토큰 세기다. 도구 정의 JSON `{"name":"weather","description":"도시의 현재 날씨를 알려 준다"}` 는 51 글자라 13 토큰이고 `user text "서울 날씨 알려 줘 🙂"` 는 24 글자라 6 토큰이다. 🙂 는 한 글자다. 첫 차례의 요청은 둘을 더한 19 토큰이다. 둘째 차례에는 모델 텍스트 줄 6 토큰, 도구 사용 줄 9 토큰, 결과 줄 8 토큰이 더해져 42 토큰이다. 결과 줄의 `\n` 은 두 글자로 센다.

```
> config {"context_window":2000}
> tool {"name":"weather","description":"도시의 현재 날씨를 알려 준다"}
> fake {"tool":"weather","input":{"city":"서울"},"output":"맑음\n최고 24도"}
> reply {"stop":"tool","content":[{"type":"text","text":"날씨를 찾아보겠다."},{"type":"tool","id":"t1","name":"weather","input":{"city":"서울"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"서울은 맑고 최고 24도다."}]}
> ask {"text":"서울 날씨 알려 줘 🙂"}
user text "서울 날씨 알려 줘 🙂"
turn 1 messages=1 limit=1000
context 19
model text "날씨를 찾아보겠다."
model tool t1 weather {"city":"서울"}
stop tool
user result t1 ok "맑음\n최고 24도"
turn 2 messages=3 limit=1000
context 42
model text "서울은 맑고 최고 24도다."
stop end
done end "서울은 맑고 최고 24도다."
```

[6.3] 지우기의 기본이다. read 의 정의는 4 토큰이고 첫 사용자 텍스트는 6 토큰이다. 도구 사용 줄은 9 토큰, 결과 줄은 21 토큰이라 차례마다 30 토큰씩 는다. 넷째 차례의 100 은 clear_trigger 110 보다 크지 않으므로 지우지 않는다. 다섯째 차례의 130 은 110 보다 크므로 r1 부터 살핀다. 결과 줄은 내용을 `[cleared]` 로 바꾸면 8 토큰이므로 하나를 지울 때마다 13 토큰이 준다. r2 까지 지운 104 가 clear_target 105 이하라서 r3 은 살피지 않는다. 마지막 메시지의 r4 도 남는다. 다음 ask 에서는 모델 텍스트 7 토큰과 사용자 텍스트 5 토큰이 더해져 116 이 되므로 r3 을 지운다. 이미 지운 r1 과 r2 는 다시 지우지 않는다.

```
> config {"context_window":500,"clear_trigger":110,"clear_target":105}
> tool {"name":"read"}
> fake {"tool":"read","input":{"path":"a.kt"},"output":"fun total(items: List<Item>) = items.sumOf { it.price * it.qty }"}
> fake {"tool":"read","input":{"path":"b.kt"},"output":"fun discount(total: Long) = if (total > 9_999) total / 10 else 0"}
> fake {"tool":"read","input":{"path":"c.kt"},"output":"fun shipping(express: Boolean) = if (express) 5_000 else 3_000"}
> fake {"tool":"read","input":{"path":"d.kt"},"output":"fun points(total: Long) = if (total > 99_999) total / 100 else 0"}
> reply {"stop":"tool","content":[{"type":"tool","id":"r1","name":"read","input":{"path":"a.kt"}}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"r2","name":"read","input":{"path":"b.kt"}}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"r3","name":"read","input":{"path":"c.kt"}}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"r4","name":"read","input":{"path":"d.kt"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"네 파일을 모두 읽었다."}]}
> reply {"stop":"end","content":[{"type":"text","text":"d.kt 는 10만 원부터 적립한다."}]}
> ask {"text":"네 파일을 읽어 줘"}
user text "네 파일을 읽어 줘"
turn 1 messages=1 limit=1000
context 10
model tool r1 read {"path":"a.kt"}
stop tool
user result r1 ok "fun total(items: List<Item>) = items.sumOf { it.price * it.qty }"
turn 2 messages=3 limit=1000
context 40
model tool r2 read {"path":"b.kt"}
stop tool
user result r2 ok "fun discount(total: Long) = if (total > 9_999) total / 10 else 0"
turn 3 messages=5 limit=1000
context 70
model tool r3 read {"path":"c.kt"}
stop tool
user result r3 ok "fun shipping(express: Boolean) = if (express) 5_000 else 3_000"
turn 4 messages=7 limit=1000
context 100
model tool r4 read {"path":"d.kt"}
stop tool
user result r4 ok "fun points(total: Long) = if (total > 99_999) total / 100 else 0"
turn 5 messages=9 limit=1000
clear 130 104 r1 r2
context 104
model text "네 파일을 모두 읽었다."
stop end
done end "네 파일을 모두 읽었다."
> ask {"text":"적립 기준은?"}
user text "적립 기준은?"
turn 1 messages=11 limit=1000
clear 116 103 r3
context 103
model text "d.kt 는 10만 원부터 적립한다."
stop end
done end "d.kt 는 10만 원부터 적립한다."
```

[6.4] 지우지 않는 결과 블록이다. 둘째 차례의 121 은 clear_trigger 60 보다 크지만 결과 블록이 모두 마지막 메시지에 있어 지울 블록이 없고 clear 줄도 없다. 셋째 차례에는 clear_target 이 기본값 0 이라 지울 수 있는 블록을 모두 지운다. m1 은 도구 이름 memo 가 clear_exclude 에 있다. k1 의 줄은 6 토큰인데 내용을 `[cleared]` 로 바꾸면 8 토큰이 되어 줄지 않는다. clear_keep 이 2 이므로 결과 블록 여섯 가운데 뒤의 둘인 s2 와 k2 는 남는다. s1 은 15 토큰에서 8 토큰이 되고 fx1 은 오류 표시가 남아 11 토큰에서 9 토큰이 된다.

```
> config {"context_window":1000,"clear_trigger":60,"clear_keep":2,"clear_exclude":["memo"]}
> tool {"name":"memo"}
> tool {"name":"search"}
> tool {"name":"stock"}
> fake {"tool":"memo","input":{"key":"plan"},"output":"할 일: 재고 확인, 가격 비교, 주문서 작성"}
> fake {"tool":"search","input":{"q":"키보드"},"output":"키보드 검색 결과 3건: 기계식 87키, 저소음 104키, 무선 접이식"}
> fake {"tool":"search","input":{"q":"마우스"},"output":"마우스 검색 결과 2건: 무선 버티컬, 게이밍 유선"}
> fake {"tool":"stock","input":{"sku":"K-87"},"output":"12"}
> fake {"tool":"stock","input":{"sku":"K-104"},"output":"0"}
> reply {"stop":"tool","content":[{"type":"tool","id":"m1","name":"memo","input":{"key":"plan"}},{"type":"tool","id":"s1","name":"search","input":{"q":"키보드"}},{"type":"tool","id":"k1","name":"stock","input":{"sku":"K-87"}},{"type":"tool","id":"fx1","name":"fetch","input":{"url":"shop.example"}},{"type":"tool","id":"s2","name":"search","input":{"q":"마우스"}}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"k2","name":"stock","input":{"sku":"K-104"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"87키 기계식만 재고가 있다."}]}
> ask {"text":"키보드와 마우스를 찾아 줘"}
user text "키보드와 마우스를 찾아 줘"
turn 1 messages=1 limit=1000
context 20
model tool m1 memo {"key":"plan"}
model tool s1 search {"q":"키보드"}
model tool k1 stock {"sku":"K-87"}
model tool fx1 fetch {"url":"shop.example"}
model tool s2 search {"q":"마우스"}
stop tool
user result m1 ok "할 일: 재고 확인, 가격 비교, 주문서 작성"
user result s1 ok "키보드 검색 결과 3건: 기계식 87키, 저소음 104키, 무선 접이식"
user result k1 ok "12"
user result fx1 error "unknown tool: fetch"
user result s2 ok "마우스 검색 결과 2건: 무선 버티컬, 게이밍 유선"
turn 2 messages=3 limit=1000
context 121
model tool k2 stock {"sku":"K-104"}
stop tool
user result k2 ok "0"
turn 3 messages=5 limit=1000
clear 136 127 s1 fx1
context 127
model text "87키 기계식만 재고가 있다."
stop end
done end "87키 기계식만 재고가 있다."
```

[6.5] 컨텍스트 창을 넘는 요청이다. 셋째 차례의 69 는 clear_trigger 40 보다 크다. g1 을 지워 61 이 되지만 g2 는 마지막 메시지에 있어 지우지 못한다. 61 이 context_window 60 보다 크므로 모델이 요청을 거절하고 실행기는 기록을 비운다. 거절한 차례에서는 각본을 쓰지 않으므로 다음 ask 는 빈 기록에서 시작해 셋째 응답을 받는다.

```
> config {"context_window":60,"clear_trigger":40,"clear_target":20}
> tool {"name":"logs"}
> fake {"tool":"logs","input":{"day":1},"output":"09:00 결제 서버 재시작, 09:05 주문 큐 정상, 09:07 알림 지연"}
> fake {"tool":"logs","input":{"day":2},"output":"02:13 결제 서버 응답 시간 3초 초과, 02:14 주문 큐 적체 1,200건, 02:20 재시도 폭증으로 DB 연결 고갈, 02:31 수동 재시작 뒤 회복"}
> reply {"stop":"tool","content":[{"type":"tool","id":"g1","name":"logs","input":{"day":1}}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"g2","name":"logs","input":{"day":2}}]}
> reply {"stop":"end","content":[{"type":"text","text":"어떤 날의 로그를 요약할까?"}]}
> ask {"text":"이틀 치 로그를 보여 줘"}
user text "이틀 치 로그를 보여 줘"
turn 1 messages=1 limit=1000
context 11
model tool g1 logs {"day":1}
stop tool
user result g1 ok "09:00 결제 서버 재시작, 09:05 주문 큐 정상, 09:07 알림 지연"
turn 2 messages=3 limit=1000
context 34
model tool g2 logs {"day":2}
stop tool
user result g2 ok "02:13 결제 서버 응답 시간 3초 초과, 02:14 주문 큐 적체 1,200건, 02:20 재시도 폭증으로 DB 연결 고갈, 02:31 수동 재시작 뒤 회복"
turn 3 messages=5 limit=1000
clear 69 61 g1
context 61
reject context_window
done rejected ""
> ask {"text":"요약만 해 줘"}
user text "요약만 해 줘"
turn 1 messages=1 limit=1000
context 9
model text "어떤 날의 로그를 요약할까?"
stop end
done end "어떤 날의 로그를 요약할까?"
```

[6.6] 입력 오류다. 첫째 config 는 clear_target 이 clear_trigger 와 같아서 오류다. 셋째 config 는 clear_trigger 만 50 으로 바꾸지만 적용한 결과에서 clear_target 99 가 50 이상이라 오류다. 넷째 config 처럼 clear_trigger 를 0 으로 바꾸면 clear_target 과의 관계를 따지지 않는다. 마지막 config 의 `1e9` 는 정수 1,000,000,000 이고 `1E1` 은 정수 10 이다.

```
> config {"clear_trigger":100,"clear_target":100}
error bad_field
> config {"clear_trigger":100,"clear_target":99}
> config {"clear_trigger":50}
error bad_field
> config {"clear_trigger":0}
> config {"clear_trigger":50,"clear_target":10}
> config {"context_window":-1}
error bad_field
> config {"context_window":1000000001}
error bad_field
> config {"clear_keep":1.5}
error bad_field
> config {"clear_keep":1000001}
error bad_field
> config {"clear_exclude":"memo"}
error bad_field
> config {"clear_exclude":["memo",1]}
error bad_field
> config {"clear_exclude":[]}
> config {"context_window":1e9,"clear_keep":1000000,"clear_target":1E1}
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계와 같다.

[7.2] 대규모 테스트는 도구 정의 30개 안팎과 차례 37,000번 안팎으로 이뤄진다. 도구 정의를 1단계 3.9 형식으로 쓴 JSON 의 글자 수는 모두 합해 1,200만 안팎이고 그 대부분이 description 에 있다. 정의에는 한글과 U+FFFF 보다 큰 코드 포인트의 글자가 섞여 있다. 도구를 다시 정의하는 tool 명령, 결과 지우기, context_window 거절이 섞여 나온다. 결과 블록 24개가 지워지지 않고 기록에 남아 있는 동안 모델 응답이 미완성 도구 사용으로 잘려 버려지는 차례(1단계 4.38)가 29,000번 안팎 이어진다. 24개 결과 블록의 내용은 모두 합해 350만 글자 안팎이고 한글과 U+FFFF 보다 큰 코드 포인트의 글자가 섞여 있다. 제한 시간은 15초다. 차례마다 도구 정의의 토큰 수를 다시 세는 풀이는 정의를 JSON 글자열로 미리 바꿔 두어도, 차례마다 기록에 든 블록의 토큰 수를 모두 다시 세는 풀이는 블록의 줄을 글자열로 미리 만들어 두어도 이 제한 시간을 넘기도록 규모를 정했다.

[7.3] 4단계 검사 입력에는 1단계 3.12, 3.13 이 출력을 정하지 않은 줄 가운데 2단계, 3단계와 이 문서가 뜻을 정하지 않은 줄이 나오지 않는다.

[7.4] 5단계를 검사할 때도 4단계 테스트를 다시 실행한다.
