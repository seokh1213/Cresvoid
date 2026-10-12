# 1단계: 도구 호출 루프

## 1. 개요

[1.1] 이 트랙에서는 표준 입력으로 명령을 받는 에이전트 실행기를 만든다. 실행기는 모델을 호출하고 모델이 요청한 도구를 실행한 뒤 그 결과를 다음 호출에 실어 보내는 일을 되풀이한다. 실제 모델 API 는 쓰지 않는다. 입력에 든 각본이 모델 대신 응답하고 가짜 결과가 도구 대신 결과를 내므로 같은 입력에는 언제나 같은 출력이 나온다.

[1.2] 1단계의 명령은 아래 여섯 가지다.

| 명령 | 하는 일 | 정상 출력 |
|---|---|---|
| `tool` | 도구를 정의하거나 같은 이름의 정의를 바꾼다 | 없음 |
| `fake` | 도구 이름과 입력의 짝에 가짜 결과를 정한다 | 없음 |
| `reply` | 각본 끝에 모델 응답 하나를 더한다 | 없음 |
| `config` | 최대 차례 수와 응답 한도를 바꾼다 | 없음 |
| `reset` | 대화 기록을 비운다 | 없음 |
| `ask` | 사용자 메시지를 기록에 더하고 루프를 실행한다 | 여러 줄 |

[1.3] 2단계는 도구 입력 스키마 검증과 도구 실패를 더하고 3단계는 한 응답에 든 여러 도구 사용을 가상 시계로 동시에 실행한다. 4단계와 5단계는 컨텍스트 예산과 도구 권한을 다룬다. 이 문서의 입출력 규칙은 마지막 단계까지 그대로 쓴다. 뒤 단계는 이 문서가 정한 출력을 바꾸지 않는다. 거꾸로 뒤 단계에서 뜻이 생기는 입력은 이 문서가 출력을 정하지 않으며 그런 입력은 3.12 와 3.13 에 적는다.

[1.4] 시작 코드에는 `Main.kt` 와 `Json.kt` 가 있다. `Json.kt` 는 3.6, 3.7 대로 JSON 을 읽는 `parseJson` 과 3.9, 3.10 대로 JSON 을 쓰는 `toJson` 을 담고 있다. JSON 값이 같은지 판정하는 일(3.8)은 담고 있지 않다. 이 파일을 그대로 써도 되고 직접 만들어도 된다.

## 2. 배경 개념

[2.1] 에이전트는 언어 모델이 환경에서 받은 피드백을 보며 도구를 고르고 쓰는 일을 되풀이하는 시스템이다. 모델은 텍스트를 만들 뿐 파일을 열거나 API 를 호출하지 못한다. 모델이 어떤 도구를 어떤 입력으로 실행해 달라고 응답하면 모델 바깥의 프로그램이 도구를 실행하고 그 결과를 다음 호출에 실어 보낸다. 이 바깥 프로그램이 실행기다. Anthropic 의 글 "Building effective agents" 는 에이전트를 대개 루프 안에서 환경의 피드백에 따라 도구를 쓰는 LLM 이라고 설명한다.

[2.2] 모델 API 는 대개 호출 사이에 대화를 기억하지 않는다. 그래서 실행기는 호출할 때마다 처음부터 지금까지의 대화를 통째로 보낸다. 대화는 메시지의 목록이고 메시지마다 보낸 쪽(사용자 또는 모델)과 블록 목록이 있다. 모델의 응답은 모델 메시지 하나가 되어 대화 끝에 붙고 다음 호출에는 그 메시지까지 들어간다.

[2.3] 모델이 도구를 쓰려 할 때는 응답에 도구 사용 블록을 담고 도구 사용을 정지 이유로 내놓는다. 도구 사용 블록에는 식별자, 도구 이름, 입력이 있다. 실행기는 도구를 실행하고 다음 사용자 메시지에 결과 블록을 담는다. 결과 블록에는 자신이 답하는 도구 사용 블록의 식별자를 적는다. 한 응답에 도구 사용 블록이 여럿이어도 모델은 이 식별자로 요청과 결과를 짝짓는다.

[2.4] 실제 API 는 이 짝에 엄격한 규칙을 둔다. Claude API 문서는 결과 블록이 도구 사용 메시지 바로 다음 메시지에 와야 하고 그 메시지 안에서는 모든 텍스트 블록보다 앞에 와야 한다고 적는다. 어기면 요청은 400 오류로 거절된다. 한 응답의 도구 사용에 대한 결과는 다음 사용자 메시지 하나에 모두 담는다. 이 트랙의 규약은 여기에 규칙 하나를 더해 결과 블록을 도구 사용 블록과 같은 순서로 놓게 한다.

[2.5] 실행하지 않은 도구 사용에도 결과는 보내야 한다. 실행기가 어떤 이유로 도구를 실행하지 않았어도 그 도구 사용 블록에 답하는 결과 블록이 없으면 다음 요청은 규약을 어긴다. Claude API 문서도 실행하지 않기로 한 호출에 오류 표시와 짧은 설명을 단 결과를 보내라고 적는다. 오류 결과는 오류 표시가 붙은 결과 블록이다. 오류 표시가 빠지면 모델은 "실행하지 않음" 같은 글을 도구가 정상으로 돌려준 값으로 읽는다.

[2.6] 정지 이유는 모델이 응답을 멈춘 까닭이다. 끝(end)이면 모델이 할 말을 다 했고 도구 사용(tool)이면 모델이 결과를 기다린다. 길이 한도(length)이면 응답이 그 호출에 정한 응답 한도, 곧 출력 길이의 상한에 닿아 잘렸다. 모델이 도구 사용 블록을 쓰는 도중에 응답이 잘렸으면 그 블록의 입력은 미완성이라 실행할 수 없다. Claude API 문서는 잘린 응답에 미완성 도구 사용 블록이 있으면 한도를 높여 같은 요청을 다시 보내라고 권한다. 이 트랙의 각본에서는 미완성 도구 사용 블록의 incomplete 가 true 다(4.9). 잘린 응답의 마지막 블록이 도구 사용 블록이어도 incomplete 가 false 이면 입력을 다 쓴 블록이다.

[2.7] 모델이 도구 사용을 끝없이 이어 가면 루프가 끝나지 않는다. 그래서 실행기는 사용자 메시지 하나를 처리하는 동안 모델을 호출하는 횟수에 상한을 둔다. "Building effective agents" 도 작업이 끝나면 루프를 멈추되 최대 반복 횟수 같은 정지 조건을 함께 두는 일이 흔하다고 적는다.

[2.8] 테스트에서는 실제 모델과 실제 도구 대신 대역을 쓴다. 각본 모델은 미리 적어 둔 응답을 적힌 순서대로 내놓고 가짜 도구는 입력마다 정해 둔 결과를 돌려준다. 각본 모델은 실행기가 보낸 요청에 맞춰 응답을 바꾸지 않는다. 다만 실제 API 처럼 요청이 규약을 지켰는지는 검사하고 어긴 요청은 거절한다.

[2.9] 가짜 결과를 찾을 때는 도구 입력을 JSON 값으로 비교한다. 두 JSON 문서는 글자가 달라도 같은 값일 수 있다. 객체의 필드 순서는 값에 들어가지 않고 `1` 과 `1.0` 은 같은 수다. 모델은 같은 입력을 매번 같은 글자로 쓰지 않으므로 글자를 그대로 비교하는 대역은 같은 입력을 다른 입력으로 판정한다.

## 3. 입출력 규칙

[3.1] 프로그램은 표준 입력에서 명령을 한 줄씩 읽어 입력이 끝날 때까지 처리하고 결과를 표준 출력에 쓴다. 입력이 끝나면 종료 코드 0 으로 끝난다.

[3.2] 입력은 UTF-8 이다. 줄은 LF(U+000A)로 나눈다. 마지막 줄 뒤에 LF 가 없어도 그 줄을 처리한다.

[3.3] 이 문서에서 공백은 U+0020 한 글자다. 줄 앞뒤의 공백은 무시한다. 빈 줄과 공백만 있는 줄은 건너뛰고 아무것도 출력하지 않는다.

[3.4] 줄 맨 앞부터 첫 공백 바로 앞까지가 명령어다. 공백이 없으면 줄 전체가 명령어다. 명령어 뒤의 공백을 모두 건너뛴 나머지가 인자다. 인자가 비어 있으면 필드가 없는 객체 `{}` 를 인자로 쓴다.

[3.5] 인자 전체가 JSON 값 하나로 읽혀야 하고 그 값은 객체여야 한다. 값 앞뒤에는 3.6 이 토큰 사이에 허용하는 글자가 있어도 된다.

[3.6] JSON 문법은 RFC 8259 를 따른다. 토큰 사이에는 공백, 탭(U+0009), CR(U+000D), LF 를 넣을 수 있다. 수는 정규식 `-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?` 에 맞는 글자열이다. 문자열 안에는 U+0000~U+001F 를 그대로 쓸 수 없고 이스케이프 `\"`, `\\`, `\/`, `\b`, `\f`, `\n`, `\r`, `\t`, `\uXXXX` 를 쓸 수 있다.

[3.7] 이 트랙은 RFC 8259 에 제한 두 가지를 더한다. 첫째로 한 객체 안에서 이스케이프를 푼 필드 이름이 겹치면 JSON 이 아니다. `{"a":1,"\u0061":2}` 도 이름이 겹친다. 둘째로 `\uD800`~`\uDBFF` 이스케이프 바로 뒤에는 `\uDC00`~`\uDFFF` 이스케이프가 와서 두 이스케이프가 글자 하나를 이뤄야 한다. 짝 없이 홀로 나온 `\uD800`~`\uDFFF` 이스케이프가 있으면 JSON 이 아니다.

[3.8] 두 JSON 값이 같은지는 다음과 같이 정한다. 종류(객체, 배열, 문자열, 수, true, false, null)가 다르면 다르다. 문자열은 이스케이프를 푼 글자열이 같으면 같다. 수는 수학적 값이 같으면 같다. 배열은 길이가 같고 같은 자리의 원소가 모두 같으면 같다. 객체는 필드 이름의 집합이 같고 이름마다 값이 같으면 같으며 필드 순서는 따지지 않는다. `{"a":1,"b":[2]}` 와 `{"b":[2.0],"a":1e0}` 은 같은 값이다. `1` 과 `"1"` 은 다른 값이고 `-0` 과 `0` 은 같은 수다.

[3.9] 출력하는 JSON 값은 토큰 사이에 공백을 넣지 않고 쓴다. 객체의 필드는 입력에 나온 순서대로 쓴다. 수는 입력에 쓰인 글자 그대로 쓴다. `2.0` 은 `2.0` 으로, `1E+5` 는 `1E+5` 로 나온다.

[3.10] 출력하는 문자열은 큰따옴표로 감싼다. `"` 는 `\"` 로, `\` 는 `\\` 로 쓴다. U+0008, U+000C, U+000A, U+000D, U+0009 는 각각 `\b`, `\f`, `\n`, `\r`, `\t` 로 쓰고 나머지 U+0000~U+001F 는 `\u` 뒤에 소문자 16진수 네 자리를 붙여 쓴다. U+001F 는 `\u001f` 가 된다. 그 밖의 글자는 그대로 쓴다. 입력에 쓴 `\/` 와 `\u00e9` 는 출력에서 `/` 와 `é` 가 된다.

[3.11] 출력하는 줄은 모두 LF 로 끝낸다.

[3.12] 명령어가 1.2 의 여섯 명령 가운데 하나와 정확히 같지 않으면 모르는 명령어다. 모르는 명령어가 영문 소문자(a–z)와 밑줄(_)로만 이뤄졌으면 이 문서는 그 줄의 출력을 정하지 않는다. 뒤 단계가 이런 이름으로 명령을 더하기 때문이다. 그 밖의 모르는 명령어는 5.2 의 오류다. 명령어는 대소문자를 구분하므로 `Ask` 와 `ASK` 는 ask 가 아니다. `ask{"text":"안녕"}` 처럼 명령어와 인자 사이에 공백이 없으면 줄 전체가 명령어다.

[3.13] 명령의 인자 객체나 reply 의 블록 객체에 이 문서가 정하지 않은 필드가 하나라도 있으면 이 문서는 그 줄의 출력을 정하지 않는다. 뒤 단계가 이런 필드를 더하기 때문이다. 정한 필드인지는 명령마다, 블록 객체는 type 값마다 따진다. 명령의 인자에 정한 필드는 tool(name, description), fake(tool, input, output), reply(stop, content), config(max_turns, reply_limit, reply_limit_cap), ask(text) 이고 reset 에는 없다. 블록 객체에 정한 필드는 텍스트 블록이면 type, text 이고 도구 사용 블록이면 type, id, name, input, incomplete 다. type 이 없거나 type 값이 text 도 tool 도 아닌 블록 객체에는 type 말고 정한 필드가 없다. 위 목록에 없는 필드는 다른 명령이나 다른 종류의 블록에 같은 이름이 있어도 정하지 않은 필드다. 그래서 이 문서는 `reset {"text":"x"}` 줄과 텍스트 블록에 input 필드가 있는 reply 줄의 출력을 정하지 않는다. 도구 입력처럼 이 문서가 내용을 정하지 않는 JSON 값 안의 필드는 여기에 해당하지 않는다.

[3.14] 이 문서에서 정수는 수학적 값이 정수인 수다. `3`, `3.0`, `3e0` 은 모두 정수 3 이고 `3.5` 와 `"3"` 은 정수가 아니다. 문자열이 비었거나 공백, 탭, LF, CR 로만 이뤄졌으면 그 문자열은 빈 텍스트다.

[3.15] 프로그램은 도구 정의, 가짜 결과, 각본, 대화 기록이 모두 빈 상태로 시작하고 설정은 4.12 의 기본값으로 시작한다. 이전 실행의 상태를 이어받지 않는다.

## 4. 명령 규칙

### 이름 형식

[4.1] 이 문서에서 이름 형식은 정규식 `^[A-Za-z0-9_-]{1,64}$` 이고 문자열 전체에 적용한다. 영문자, 숫자, 밑줄, 하이픈으로 된 1~64 글자이며 한글은 들어갈 수 없다.

### tool

[4.2] `tool` 의 인자에는 `name`(문자열, 필수)과 `description`(문자열, 선택) 필드가 있다. 실행기는 name 을 이름으로 하는 도구 정의를 등록한다. 같은 이름의 정의가 이미 있으면 새 정의로 바꾼다. 정상이면 아무것도 출력하지 않는다.

[4.3] tool 은 name 이 이름 형식에 맞는지 확인하지 않는다. 이름 형식은 모델이 요청을 검사할 때 확인한다(4.25). description 은 이 단계의 출력에 영향을 주지 않는다.

### fake

[4.4] `fake` 의 인자에는 `tool`(문자열), `input`(객체), `output`(문자열) 필드가 모두 있어야 한다. 실행기는 도구 이름이 tool 이고 입력이 input 과 같은 도구 사용의 결과를 output 으로 정한다. 도구 이름이 같고 입력이 3.8 의 뜻으로 같은 가짜 결과가 이미 있으면 그 결과를 새 output 으로 바꾼다. 정상이면 아무것도 출력하지 않는다.

[4.5] fake 는 tool 이 정의된 도구인지 확인하지 않는다. 가짜 결과를 먼저 정하고 도구를 나중에 정의해도 된다.

### reply

[4.6] `reply` 의 인자에는 `stop`(문자열)과 `content`(배열) 필드가 모두 있어야 한다. 실행기는 이 응답을 각본 끝에 더한다. 정상이면 아무것도 출력하지 않는다.

[4.7] stop 은 정지 이유이고 `end`, `tool`, `length` 가운데 하나다. content 는 블록 객체를 하나 이상 담은 배열이다. 블록 객체의 `type` 필드는 `text` 나 `tool` 이다.

[4.8] 텍스트 블록에는 `type` 과 `text` 필드가 있다. text 는 빈 텍스트가 아닌 문자열이다.

[4.9] 도구 사용 블록에는 `type`, `id`, `name` 필드가 있고 `input` 과 `incomplete` 필드가 있을 수 있다. id 와 name 은 이름 형식에 맞는 문자열이다. incomplete 는 불리언이고 없으면 false 다. incomplete 가 false 인 블록은 input 에 객체가 있어야 한다. incomplete 가 true 인 블록은 응답 한도에 닿아 잘린 미완성 도구 사용이고 input 필드가 없어야 한다.

[4.10] 응답의 블록에는 다음 제약이 있다. 미완성 도구 사용 블록은 정지 이유가 length 인 응답의 마지막 블록으로만 올 수 있다. 정지 이유가 tool 인 응답에는 도구 사용 블록이 하나 이상 있어야 한다. 정지 이유가 end 인 응답에는 도구 사용 블록이 없어야 한다.

[4.11] reply 는 도구 사용 블록의 id 가 같은 응답이나 다른 응답의 id 와 겹치는지 확인하지 않는다. 겹친 id 는 모델이 요청을 검사할 때 드러난다(4.28).

### config

[4.12] `config` 의 인자에는 아래 세 필드가 있을 수 있다. 필드는 모두 선택이고 인자에 있는 필드만 바꾼다. 기본값은 max_turns 가 10 이고 reply_limit 이 1,000 이며 reply_limit_cap 이 4,000 이다.

| 필드 | 뜻 | 범위 | 기본값 |
|---|---|---|---|
| max_turns | ask 하나에서 모델을 호출하는 최대 횟수 | 1~1,000 | 10 |
| reply_limit | ask 의 첫 차례에 쓰는 응답 한도 | 1~1,000,000 | 1,000 |
| reply_limit_cap | 응답 한도를 올릴 수 있는 상한 | 1~1,000,000 | 4,000 |

[4.13] max_turns 는 1 이상 1,000 이하, reply_limit 과 reply_limit_cap 은 1 이상 1,000,000 이하의 정수다. 실행기는 인자의 필드를 지금 설정에 적용한 결과를 먼저 만든다. 그 결과에서 reply_limit 이 reply_limit_cap 보다 크면 오류다. 필드 하나라도 오류이면 config 는 어떤 필드도 바꾸지 않는다.

[4.14] 바뀐 설정은 다음에 시작하는 ask 부터 쓴다.

### reset

[4.15] `reset` 의 인자에는 필드가 없다. reset 은 대화 기록을 비운다. 도구 정의, 가짜 결과, 각본, 설정은 그대로 둔다. 아무것도 출력하지 않는다.

### ask 와 사용자 메시지

[4.16] `ask` 의 인자에는 `text`(문자열, 필수) 필드가 있다. text 는 빈 텍스트여도 된다. ask 는 text 를 텍스트 블록으로 대화 기록에 더한 뒤 루프를 실행한다.

[4.17] 대화 기록은 메시지의 목록이다. 메시지는 사용자 메시지와 모델 메시지 두 종류다. 사용자 메시지에는 텍스트 블록과 결과 블록이 들어가고 모델 메시지에는 텍스트 블록과 도구 사용 블록이 들어간다. 결과 블록에는 답하는 도구 사용 블록의 id, 오류 표시, 내용 문자열이 있다. 오류 표시가 있는 결과 블록이 오류 결과이고 없는 것이 정상 결과다.

[4.18] 대화 기록이 비었거나 마지막 메시지가 도구 사용 블록이 없는 모델 메시지이면 ask 는 text 의 텍스트 블록 하나로 된 사용자 메시지를 기록 끝에 더한다.

[4.19] 기록의 마지막 메시지가 도구 사용 블록이 있는 모델 메시지이면 그 도구 사용 블록들에는 아직 결과가 없다. ask 는 새 사용자 메시지를 기록 끝에 더하고 그 메시지에 모델 메시지의 도구 사용 블록마다 오류 결과를 블록 순서대로 하나씩 담는다. 텍스트 블록은 그 오류 결과들 뒤에 담는다. 이 오류 결과의 내용은 `not executed` 다.

[4.20] 기록의 마지막 메시지가 사용자 메시지이면 ask 는 그 메시지 끝에 text 의 텍스트 블록을 더한다. 기록의 메시지 수는 늘지 않는다.

[4.21] 4.18~4.20 에서 기록에 더한 블록은 더한 순서대로 한 줄씩 출력한다. 줄의 형식은 4.44 에 있다.

### 차례

[4.22] ask 의 루프는 차례를 되풀이한다. 차례는 모델 호출 한 번이다. 차례 번호는 ask 마다 1 부터 센다. 실행기는 차례마다 지금 정의된 도구 정의 전체, 대화 기록 전체, 응답 한도를 요청으로 보낸다.

[4.23] 차례를 시작할 때 실행기는 `turn <차례 번호> messages=<기록의 메시지 수> limit=<응답 한도>` 를 출력한다. 첫 차례의 응답 한도는 그 ask 를 시작할 때의 reply_limit 이다. 응답 한도는 4.38 에서만 바뀐다. 이 줄의 세 수는 JSON 값이 아니므로 3.9 를 따르지 않는다. 세 수는 소수점과 지수 없이 10진수로 쓰고 앞에 0 을 붙이지 않는다. config 에 reply_limit 을 `1.5e3` 이나 `1500.0` 으로 써도 첫 차례의 줄에는 `limit=1500` 이 나온다.

### 요청 검사

[4.24] 모델은 응답하기 전에 요청을 4.25~4.29 의 규칙 순서대로 검사한다. 어긴 규칙이 있으면 처음 어긴 규칙의 코드로 요청을 거절하고 뒤 규칙은 검사하지 않는다.

[4.25] 첫째 규칙의 코드는 tool_name 이다. 요청에 든 도구 정의 가운데 이름이 이름 형식에 맞지 않는 것이 하나라도 있으면 이 규칙을 어긴다.

[4.26] 둘째 규칙의 코드는 role_order 다. 기록은 사용자 메시지로 시작해 사용자 메시지와 모델 메시지가 번갈아 나오고 사용자 메시지로 끝나야 한다.

[4.27] 셋째 규칙의 코드는 empty_content 다. 모든 메시지에 블록이 하나 이상 있어야 하고 텍스트 블록의 텍스트는 빈 텍스트가 아니어야 한다.

[4.28] 넷째 규칙의 코드는 duplicate_id 다. 기록에 든 도구 사용 블록의 id 는 모두 달라야 한다. 같은 응답 안에서 겹치든 서로 다른 응답 사이에서 겹치든 이 규칙을 어긴다.

[4.29] 다섯째 규칙의 코드는 pairing 이다. 도구 사용 블록이 n 개 있는 모델 메시지 바로 다음 사용자 메시지는 처음 n 개 블록이 결과 블록이어야 하고 k 번째 결과 블록의 id 는 그 모델 메시지의 k 번째 도구 사용 블록의 id 와 같아야 한다. 그 n 개 뒤에는 결과 블록이 더 없어야 한다. 첫 메시지에는 결과 블록이 없어야 한다. 도구 사용 블록이 없는 모델 메시지 바로 다음의 사용자 메시지에도 결과 블록이 없어야 한다.

[4.30] 실행기가 이 문서의 규칙대로 기록을 만들면 role_order 와 pairing 은 어길 일이 없고 블록이 없는 메시지도 생기지 않는다. 이런 검사는 기록을 잘못 만든 실행기를 드러낸다. 그래서 이 단계의 기대 출력에는 `reject role_order` 와 `reject pairing` 이 나오지 않고 블록이 없는 메시지 때문에 나오는 `reject empty_content` 도 없다.

[4.31] 요청이 다섯 규칙을 모두 지켰는데 각본에 남은 응답이 없으면 모델은 코드 script_exhausted 로 요청을 거절한다.

[4.32] 모델이 요청을 거절하면 실행기는 `reject <코드>` 와 `done rejected ""` 를 이 순서로 출력하고 대화 기록을 비운 뒤 ask 를 끝낸다. 거절한 차례에서는 각본에서 응답을 꺼내지 않는다.

### 응답

[4.33] 요청이 검사를 통과하면 모델은 각본의 맨 앞 응답을 꺼내 돌려준다. 꺼낸 응답은 각본에서 빠진다. 각본은 ask 와 reset 에 상관없이 하나로 이어지고 앞에서부터 쓰인다.

[4.34] 실행기는 응답을 받으면 응답의 블록을 블록 순서대로 한 줄씩 출력하고 이어서 `stop <정지 이유>` 를 출력한다.

[4.35] 응답의 텍스트는 응답에 든 텍스트 블록의 text 를 블록 순서대로 구분자 없이 이은 문자열이다. 텍스트 블록이 없으면 응답의 텍스트는 빈 문자열이다.

[4.36] 정지 이유가 end 이면 실행기는 응답을 모델 메시지로 기록 끝에 더하고 ask 를 종료 상태 end 로 끝낸다.

[4.37] 정지 이유가 tool 이면 실행기는 응답을 모델 메시지로 기록 끝에 더한다. 이 차례 번호가 max_turns 와 같으면 도구를 실행하지 않고 ask 를 종료 상태 max_turns 로 끝낸다. 결과가 없는 도구 사용 블록은 다음 ask 가 4.19 대로 처리한다. 차례 번호가 max_turns 보다 작으면 실행기는 4.41, 4.42 의 도구 단계를 거쳐 다음 차례를 시작한다.

[4.38] 정지 이유가 length 이고 마지막 블록이 미완성 도구 사용 블록이면 실행기는 `discard` 를 출력하고 응답을 기록에 더하지 않는다. 그다음 응답 한도가 reply_limit_cap 과 같으면 ask 를 종료 상태 length 로 끝낸다. 응답 한도가 reply_limit_cap 보다 작고 차례 번호가 max_turns 와 같으면 ask 를 종료 상태 max_turns 로 끝낸다. 둘 다 아니면 응답 한도를 지금 한도의 두 배와 reply_limit_cap 가운데 작은 값으로 바꾸고 다음 차례를 시작한다. 다음 차례의 요청에 든 기록은 이번 차례와 같다.

[4.39] 정지 이유가 length 이고 마지막 블록이 미완성 도구 사용 블록이 아니면 실행기는 응답을 모델 메시지로 기록 끝에 더하고 ask 를 종료 상태 length 로 끝낸다. 마지막 블록이 incomplete 가 false 인 도구 사용 블록이어도 같다. 그 응답에 도구 사용 블록이 있어도 실행하지 않는다. 결과가 없는 그 블록은 다음 ask 가 4.19 대로 처리한다.

[4.40] 4.38 에서 바꾼 응답 한도는 그 ask 가 끝날 때까지 유지된다. 다음 ask 의 첫 차례는 다시 그때의 reply_limit 을 쓴다.

### 도구 단계

[4.41] 도구 단계에서 실행기는 응답의 도구 사용 블록을 블록 순서대로 하나씩 처리해 결과 블록을 만든다. 블록의 name 을 이름으로 하는 도구 정의가 없으면 오류 결과를 만들고 내용은 `unknown tool: ` 뒤에 name 을 붙인 문자열이다. 도구 정의가 있고 도구 이름이 name 이며 입력이 블록의 input 과 같은 가짜 결과가 있으면 정상 결과를 만들고 내용은 그 가짜 결과의 output 이다. 도구 정의는 있지만 그런 가짜 결과가 없으면 오류 결과를 만들고 내용은 `no result for this input` 이다.

[4.42] 실행기는 만든 결과 블록을 도구 사용 블록과 같은 순서로 담은 사용자 메시지 하나를 기록 끝에 더하고 결과 블록을 그 순서대로 한 줄씩 출력한다. 이어서 다음 차례를 시작한다.

### 종료

[4.43] ask 를 끝낼 때 실행기는 `done <종료 상태> <문자열>` 을 출력한다. 종료 상태는 end, max_turns, length, rejected 가운데 하나다. 문자열은 ask 를 끝낸 차례에서 받은 응답의 텍스트다. 종료 상태가 rejected 이면 문자열은 빈 문자열 `""` 이다.

### 출력 줄의 형식

[4.44] 블록을 출력하는 줄의 형식은 다음과 같다. 사용자 메시지의 텍스트 블록은 `user text <text>` 로 쓴다. 결과 블록은 정상 결과이면 `user result <id> ok <내용>`, 오류 결과이면 `user result <id> error <내용>` 으로 쓴다. 모델 메시지의 텍스트 블록은 `model text <text>` 로 쓴다. 도구 사용 블록은 `model tool <id> <name> <input>` 으로 쓰고 미완성 도구 사용 블록은 `model tool <id> <name> incomplete` 로 쓴다.

[4.45] 4.43 과 4.44 의 <text>, <내용>, <문자열> 은 3.10 형식의 JSON 문자열이고 <input> 은 3.9 형식의 JSON 객체다. id 와 name 은 따옴표 없이 그대로 쓴다. 줄의 각 부분은 공백 하나로 나눈다.

```
user text "서울 날씨 알려 줘"
user result t1 ok "맑음"
user result t2 error "not executed"
model text "날씨를 찾아보겠다."
model tool t1 weather {"city":"서울"}
model tool t3 search incomplete
```

## 5. 오류 규칙

[5.1] 명령이 오류이면 실행기는 `error <코드>` 한 줄만 출력한다. 오류를 낸 명령은 상태를 바꾸지 않는다. 프로그램은 다음 줄을 이어서 처리한다.

[5.2] 3.12 의 모르는 명령어 가운데 출력을 정한 것은 `error unknown_command` 를 출력한다.

[5.3] 인자가 3.5~3.7 을 만족하지 않으면 `error bad_json` 을 출력한다. 문법 오류, 겹친 필드 이름, 짝 없는 서로게이트 이스케이프, 객체가 아닌 값, 값 뒤에 남은 글자가 여기에 해당한다.

[5.4] 인자가 JSON 객체이지만 필수 필드가 없거나 필드 값의 종류나 범위가 이 문서가 정한 것과 다르면 `error bad_field` 를 출력한다. 4.7~4.10 의 제약을 어긴 reply 와 4.13 의 제약을 어긴 config 도 여기에 해당한다.

[5.5] 실행기는 명령어, JSON, 필드 순서로 확인한다. `Ask {` 는 JSON 이 깨졌어도 `error unknown_command` 이고 `ask {"text":1` 은 `error bad_json` 이다.

[5.6] ask 의 text 가 빈 텍스트여도 오류가 아니다. 빈 텍스트는 대화 기록에 들어간 뒤 모델이 empty_content 로 거절한다.

## 6. 예시

[6.1] 예시 블록 하나는 프로그램을 한 번 실행한 기록이다. `> ` 로 시작하는 줄은 입력이고 나머지 줄은 바로 위 입력의 출력이다. 실제 입력에는 `> ` 가 없다.

[6.2] 기본 루프다. 모델이 도구를 한 번 쓰고 결과를 받은 뒤 답한다. 두 번째 차례의 기록에는 사용자 메시지, 모델 메시지, 결과를 담은 사용자 메시지가 있어 `messages=3` 이다. 가짜 결과의 LF 는 출력에서 `\n` 으로 나온다.

```
> tool {"name":"weather","description":"도시의 현재 날씨를 알려 준다"}
> fake {"tool":"weather","input":{"city":"서울"},"output":"맑음\n최고 24도"}
> reply {"stop":"tool","content":[{"type":"text","text":"날씨를 찾아보겠다."},{"type":"tool","id":"t1","name":"weather","input":{"city":"서울"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"서울은 맑고 최고 24도다."}]}
> ask {"text":"서울 날씨 알려 줘"}
user text "서울 날씨 알려 줘"
turn 1 messages=1 limit=1000
model text "날씨를 찾아보겠다."
model tool t1 weather {"city":"서울"}
stop tool
user result t1 ok "맑음\n최고 24도"
turn 2 messages=3 limit=1000
model text "서울은 맑고 최고 24도다."
stop end
done end "서울은 맑고 최고 24도다."
```

[6.3] 한 응답에 도구 사용이 넷이다. c1 의 입력은 필드 순서와 수 표기가 가짜 결과와 다르지만 같은 값이다. c3 의 `"2"` 는 문자열이라 수 2 와 다르다. ship 은 정의되지 않은 도구다. 결과는 도구 사용 블록의 순서대로 나온다. 마지막 응답의 텍스트 블록 둘은 구분자 없이 이어진다.

```
> tool {"name":"stock"}
> tool {"name":"price"}
> fake {"tool":"stock","input":{"sku":"A-1","warehouse":2},"output":"12"}
> fake {"tool":"price","input":{"sku":"A-1"},"output":"15000"}
> reply {"stop":"tool","content":[{"type":"tool","id":"c1","name":"stock","input":{"warehouse":2.0,"sku":"A-1"}},{"type":"tool","id":"c2","name":"ship","input":{"sku":"A-1"}},{"type":"tool","id":"c3","name":"stock","input":{"sku":"A-1","warehouse":"2"}},{"type":"tool","id":"c4","name":"price","input":{"sku":"A-1"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"재고는 12개이고 "},{"type":"text","text":"가격은 15000원이다."}]}
> ask {"text":"A-1 재고와 가격은?"}
user text "A-1 재고와 가격은?"
turn 1 messages=1 limit=1000
model tool c1 stock {"warehouse":2.0,"sku":"A-1"}
model tool c2 ship {"sku":"A-1"}
model tool c3 stock {"sku":"A-1","warehouse":"2"}
model tool c4 price {"sku":"A-1"}
stop tool
user result c1 ok "12"
user result c2 error "unknown tool: ship"
user result c3 error "no result for this input"
user result c4 ok "15000"
turn 2 messages=3 limit=1000
model text "재고는 12개이고 "
model text "가격은 15000원이다."
stop end
done end "재고는 12개이고 가격은 15000원이다."
```

[6.4] 최대 차례 수다. max_turns 가 2 라서 두 번째 차례의 도구 사용 s2, s3 은 실행하지 않는다. 다음 ask 는 두 블록에 답하는 `not executed` 오류 결과를 먼저 담고 텍스트 블록을 그 뒤에 담는다.

```
> config {"max_turns":2}
> tool {"name":"search"}
> fake {"tool":"search","input":{"q":"코루틴"},"output":"문서 3건"}
> reply {"stop":"tool","content":[{"type":"tool","id":"s1","name":"search","input":{"q":"코루틴"}}]}
> reply {"stop":"tool","content":[{"type":"text","text":"더 찾아보겠다."},{"type":"tool","id":"s2","name":"search","input":{"q":"구조적 동시성"}},{"type":"tool","id":"s3","name":"search","input":{"q":"취소"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"찾은 문서는 3건이다."}]}
> ask {"text":"코루틴 자료를 찾아 줘"}
user text "코루틴 자료를 찾아 줘"
turn 1 messages=1 limit=1000
model tool s1 search {"q":"코루틴"}
stop tool
user result s1 ok "문서 3건"
turn 2 messages=3 limit=1000
model text "더 찾아보겠다."
model tool s2 search {"q":"구조적 동시성"}
model tool s3 search {"q":"취소"}
stop tool
done max_turns "더 찾아보겠다."
> ask {"text":"찾은 것만으로 정리해 줘"}
user result s2 error "not executed"
user result s3 error "not executed"
user text "찾은 것만으로 정리해 줘"
turn 1 messages=5 limit=1000
model text "찾은 문서는 3건이다."
stop end
done end "찾은 문서는 3건이다."
```

[6.5] 응답 한도다. 미완성 도구 사용으로 잘린 응답은 기록에 들어가지 않으므로 다시 보낸 요청도 `messages=1` 이다. 한도는 1500, 3000 으로 두 배씩 오르다가 6000 대신 상한 5000 이 된다. 세 번째 응답은 텍스트에서 잘렸으므로 기록에 들어가고 그 안의 도구 사용 w3 은 실행하지 않는다. 다음 ask 의 첫 차례 한도는 다시 1500 이다.

```
> config {"reply_limit":1500,"reply_limit_cap":5000}
> tool {"name":"save"}
> reply {"stop":"length","content":[{"type":"text","text":"회의록을 저장하겠다."},{"type":"tool","id":"w1","name":"save","incomplete":true}]}
> reply {"stop":"length","content":[{"type":"tool","id":"w2","name":"save","incomplete":true}]}
> reply {"stop":"length","content":[{"type":"tool","id":"w3","name":"save","input":{"title":"회의록"}},{"type":"text","text":"저장한 뒤 요약하면"}]}
> reply {"stop":"end","content":[{"type":"text","text":"저장하지 못했다."}]}
> ask {"text":"회의록을 저장해 줘"}
user text "회의록을 저장해 줘"
turn 1 messages=1 limit=1500
model text "회의록을 저장하겠다."
model tool w1 save incomplete
stop length
discard
turn 2 messages=1 limit=3000
model tool w2 save incomplete
stop length
discard
turn 3 messages=1 limit=5000
model tool w3 save {"title":"회의록"}
model text "저장한 뒤 요약하면"
stop length
done length "저장한 뒤 요약하면"
> ask {"text":"어떻게 됐어?"}
user result w3 error "not executed"
user text "어떻게 됐어?"
turn 1 messages=3 limit=1500
model text "저장하지 못했다."
stop end
done end "저장하지 못했다."
```

[6.6] 한도가 이미 상한이라 다시 보내지 못하고 끝난 경우다. 버린 응답의 텍스트가 done 줄에 나온다. 기록은 결과를 담은 사용자 메시지로 끝나 있으므로 다음 ask 는 그 메시지에 텍스트 블록을 더하고 `messages=3` 이 그대로다.

```
> config {"reply_limit":2000,"reply_limit_cap":2000}
> tool {"name":"order"}
> fake {"tool":"order","input":{"id":7},"output":"주문 7: 배송 중"}
> reply {"stop":"tool","content":[{"type":"tool","id":"k1","name":"order","input":{"id":7}}]}
> reply {"stop":"length","content":[{"type":"text","text":"배송 상태를 표로"},{"type":"tool","id":"k2","name":"order","incomplete":true}]}
> reply {"stop":"end","content":[{"type":"text","text":"주문 7은 배송 중이다."}]}
> ask {"text":"주문 7은 어디쯤 왔어?"}
user text "주문 7은 어디쯤 왔어?"
turn 1 messages=1 limit=2000
model tool k1 order {"id":7}
stop tool
user result k1 ok "주문 7: 배송 중"
turn 2 messages=3 limit=2000
model text "배송 상태를 표로"
model tool k2 order incomplete
stop length
discard
done length "배송 상태를 표로"
> ask {"text":"짧게 답해 줘"}
user text "짧게 답해 줘"
turn 1 messages=3 limit=2000
model text "주문 7은 배송 중이다."
stop end
done end "주문 7은 배송 중이다."
```

[6.7] 요청 거절이다. 두 응답이 같은 id a1 을 쓰므로 세 번째 차례의 요청은 duplicate_id 로 거절되고 기록이 비워진다. 공백만 있는 text 는 empty_content 로 거절된다. 각본이 다 떨어지면 script_exhausted 다. 한글 이름의 도구를 정의하면 그 뒤의 모든 요청이 tool_name 으로 거절된다.

```
> tool {"name":"weather"}
> reply {"stop":"tool","content":[{"type":"tool","id":"a1","name":"weather","input":{"city":"부산"}}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"a1","name":"weather","input":{"city":"대구"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"다시 시작했다."}]}
> ask {"text":"부산과 대구 날씨"}
user text "부산과 대구 날씨"
turn 1 messages=1 limit=1000
model tool a1 weather {"city":"부산"}
stop tool
user result a1 error "no result for this input"
turn 2 messages=3 limit=1000
model tool a1 weather {"city":"대구"}
stop tool
user result a1 error "no result for this input"
turn 3 messages=5 limit=1000
reject duplicate_id
done rejected ""
> ask {"text":"  "}
user text "  "
turn 1 messages=1 limit=1000
reject empty_content
done rejected ""
> ask {"text":"처음부터 다시"}
user text "처음부터 다시"
turn 1 messages=1 limit=1000
model text "다시 시작했다."
stop end
done end "다시 시작했다."
> ask {"text":"하나 더"}
user text "하나 더"
turn 1 messages=3 limit=1000
reject script_exhausted
done rejected ""
> tool {"name":"날씨"}
> ask {"text":"또"}
user text "또"
turn 1 messages=1 limit=1000
reject tool_name
done rejected ""
```

[6.8] 입력 오류다. 첫째 config 는 바꾼 뒤의 reply_limit 1000 이 reply_limit_cap 500 보다 커서 오류다. 둘째 config 는 max_turns 0 이 범위 밖이라 오류다. 오류를 낸 config 는 아무 필드도 바꾸지 않으므로 둘째 config 의 reply_limit 2000 도 적용되지 않고 마지막 ask 의 한도는 기본값 1000 이다. 셋째 config 의 `1.0` 은 정수 1 이다. 입력의 `\/` 와 `\u00e9` 는 출력에서 `/` 와 `é` 로 바뀐다. max_turns 가 1 이라 첫 차례의 도구 사용은 실행하지 않고 응답에 텍스트가 없어 done 줄의 문자열이 비었다.

```
> Ask {"text":"안녕"}
error unknown_command
> ask{"text":"안녕"}
error unknown_command
> ask {"text":"안녕"
error bad_json
> ask {"text":"가","text":"나"}
error bad_json
> ask ["안녕"]
error bad_json
> ask {"text":3}
error bad_field
> reply {"stop":"end","content":[{"type":"tool","id":"x1","name":"memo","input":{}}]}
error bad_field
> reply {"stop":"tool","content":[{"type":"text","text":"도구를 쓰겠다."}]}
error bad_field
> reply {"stop":"length","content":[{"type":"tool","id":"x1","name":"memo","incomplete":true},{"type":"text","text":"계속"}]}
error bad_field
> config {"reply_limit_cap":500}
error bad_field
> config {"reply_limit":2000,"max_turns":0}
error bad_field
> config {"max_turns":1.0}
> reply {"stop":"tool","content":[{"type":"tool","id":"x1","name":"memo","input":{"note":"a\/b \u00e9"}}]}
> ask {"text":"메모해 줘"}
user text "메모해 줘"
turn 1 messages=1 limit=1000
model tool x1 memo {"note":"a/b é"}
stop tool
done max_turns ""
```

## 7. 검사 방식

[7.1] 엔진은 테스트마다 풀이의 `run.sh` 를 새 프로세스로 실행한다. 작업 디렉터리는 풀이 디렉터리다.

[7.2] 엔진은 테스트의 입력 줄을 LF 로 잇고 마지막 줄 뒤에도 LF 를 붙여 표준 입력에 쓴 뒤 표준 입력을 닫는다.

[7.3] 프로세스가 0 이 아닌 종료 코드로 끝나면 실패다.

[7.4] 표준 출력만 기대 출력과 비교하고 표준 오류는 비교하지 않는다. 각 줄 끝의 공백, 탭, CR 을 지우고 끝에 붙은 빈 줄을 지운 뒤 줄 수와 각 줄의 내용이 정확히 같아야 한다.

[7.5] 일반 테스트의 제한 시간은 10초다. 제한 시간을 넘기면 엔진이 프로세스를 끝내고 실패로 판정한다.

[7.6] 대규모 테스트는 가짜 결과 390,000개, ask 5,000개, 도구 사용 블록 50,000개 안팎으로 이뤄진다. 가짜 결과의 대부분은 도구 하나에 몰려 있고 도구 입력은 필드 순서와 수 표기를 가짜 결과와 다르게 쓴다. 제한 시간은 15초다. 도구 사용 블록마다 가짜 결과를 하나씩 비교하는 풀이는 이 제한 시간을 넘기도록 규모를 정했다.

[7.7] 검사 입력의 한 줄은 UTF-8 로 1,000,000바이트 이하다. JSON 값의 중첩 깊이는 20 이하이고 수 하나는 40글자 이하이며 지수 부분의 절댓값은 100 이하다. 한 대화 기록의 메시지는 200개를 넘지 않는다.

[7.8] 검사 입력에는 3.12 와 3.13 이 출력을 정하지 않은 줄이 나오지 않는다. 검사 입력에는 CR 이 없다.

[7.9] 2단계부터 마지막 단계까지 검사할 때도 1단계 테스트를 다시 실행한다.

[7.10] 3.2 의 마지막 문장과 3.11 은 엔진이 검사하지 않는다. 엔진은 입력의 마지막 줄 뒤에도 LF 를 붙이고(7.2) 출력 줄 끝의 CR 과 끝에 붙은 빈 줄을 지운 뒤 비교하기(7.4) 때문이다. 두 규칙은 표준 입출력 프로그램의 관례라서 적어 둔다.
