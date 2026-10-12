# 2단계: 입력 스키마 검증

## 1. 개요

[1.1] 2단계는 도구 정의에 입력 스키마를 더한다. 실행기는 도구를 실행하기 전에 도구 입력을 스키마로 검증하고 검증 실패와 도구 실패를 오류 결과로 모델에 돌려준다. 같은 오류가 정해진 횟수만큼 나오면 루프를 멈춘다.

[1.2] 1단계의 입출력 규칙과 여섯 명령은 그대로다. 이 문서는 새로 생기는 규칙만 설명한다. 1단계 3.13 이 출력을 정하지 않은 필드 가운데 이 문서가 뜻을 정하는 것은 아래 세 필드뿐이다. 나머지 필드와 1단계 3.12 의 명령어는 이 문서도 출력을 정하지 않는다.

| 명령 | 새 필드 | 뜻 |
|---|---|---|
| `tool` | `schema` | 도구 입력의 스키마 |
| `fake` | `error` | 이 입력에서 도구가 실패할 때의 오류 메시지 |
| `config` | `repeat_limit` | 같은 오류를 몇 번 받으면 루프를 멈출지 |

## 2. 배경 개념

[2.1] JSON Schema 는 JSON 값이 갖춰야 할 모양을 JSON 으로 적는 방법이다. 스키마는 키워드를 필드로 가진 객체이고 키워드마다 값의 한 측면을 검사한다. `{"type":"string"}` 은 문자열만 받고 `{"type":"integer","minimum":1}` 은 1 이상의 정수만 받는다. 모델 API 의 도구 정의에는 대개 입력 스키마가 들어 있고 모델은 그 스키마를 읽고 도구 입력을 만든다.

[2.2] 모델은 스키마를 어긴 입력을 만들기도 한다. 필수 필드를 빠뜨리거나 수 자리에 문자열을 넣는다. Claude API 문서는 이런 잘못된 도구 사용에 오류 표시를 단 결과를 돌려주면 모델이 빠진 정보를 채워 다시 시도한다고 적는다. 입력이 스키마를 따르도록 보장하는 엄격 모드를 둔 API 도 있지만 모든 모델과 설정에서 쓸 수 있지는 않다. 그래서 실행기는 도구에 입력을 넘기기 전에 직접 검증한다.

[2.3] 키워드 가운데 일부는 특정 종류의 값에만 적용된다. minimum 과 maximum 은 값이 수일 때만, required 와 properties 는 값이 객체일 때만, items 는 값이 배열일 때만 검사한다. 해당하지 않는 종류의 값은 그 키워드를 통과한다. 예를 들어 `{"minimum":3}` 만 있는 스키마는 문자열 `"a"` 를 받는다. 종류를 제한하려면 type 을 함께 쓴다.

[2.4] JSON Schema 2020-12 의 검증 명세에서 integer 는 소수 부분이 0 인 모든 수에 맞는다. `2.0` 과 `2e0` 은 integer 이고 `2.5` 는 아니다. enum 은 값이 목록의 원소 가운데 하나와 같은지 확인하며 이 판정에도 JSON 값의 같음을 쓴다. enum 이 `[1]` 이면 `1.0` 도 통과한다.

[2.5] properties 는 이름이 적힌 필드의 값을 검사할 뿐 다른 필드를 막지 않는다. JSON Schema 는 다른 필드를 막는 키워드를 따로 두는데 이 트랙은 그 키워드를 쓰지 않으므로 스키마에 없는 필드는 언제나 통과한다. 다만 가짜 결과를 찾을 때는 입력 전체를 비교하므로 필드 하나가 더 있으면 다른 입력이 된다.

[2.6] 검증 실패를 알리는 오류 결과는 모델이 무엇을 고쳐야 하는지 알 수 있게 써야 한다. Claude API 문서는 "failed" 같은 막연한 오류 대신 무엇이 잘못됐고 다음에 무엇을 해야 하는지 적으라고 권한다. 이 트랙의 오류 내용은 잘못된 자리의 경로와 이유를 함께 적는다.

[2.7] 도구 자체가 실패하기도 한다. 네트워크가 끊기거나 외부 서비스가 오류를 돌려주면 도구는 결과 대신 오류 메시지를 낸다. 실행기는 그 메시지를 오류 결과의 내용으로 돌려준다. 오류 표시를 빠뜨리면 모델은 오류 메시지를 도구가 정상으로 돌려준 데이터로 읽는다.

[2.8] 오류 결과를 받은 모델이 원인을 고치지 못한 채 같은 도구를 다시 호출하면 루프는 최대 차례 수에 닿을 때까지 같은 오류만 되풀이한다. 입력이 조금 달라도 같은 이유로 실패하면 같은 오류가 나온다. 그래서 실행기는 한 ask 에서 같은 오류가 몇 번 나왔는지 세고 정한 횟수에 닿으면 루프를 멈춘다.

## 3. 입출력 규칙

[3.1] 1단계의 입출력 규칙을 그대로 따른다.

[3.2] 이 문서에서 경로는 도구 입력 안의 한 자리를 가리키는 문자열이다. 입력 객체 자체의 경로는 빈 문자열이다. 경로가 p 인 객체의 필드 n 의 경로는 p 뒤에 `/` 와 n 을 붙인 문자열이다. 경로가 p 인 배열의 i 번째 원소의 경로는 p 뒤에 `/` 와 i 를 10진수로 붙인 문자열이다. i 는 0 부터 센다. 필드 이름은 바꾸지 않고 그대로 붙인다. `{"guests":[{"age":-1}]}` 에서 -1 의 경로는 `/guests/0/age` 다.

## 4. 명령 규칙

### tool 의 schema

[4.1] `tool` 의 인자에는 `schema` 필드가 있을 수 있고 그 값은 객체다. 실행기는 schema 를 도구 정의와 함께 그대로 보관한다. tool 을 처리할 때는 schema 가 4.3~4.11 의 스키마 형식에 맞는지 확인하지 않는다.

[4.2] schema 가 없는 도구는 입력을 검증하지 않는다. 같은 이름의 도구를 다시 정의하면 스키마도 새 정의의 것으로 바뀐다. 새 정의에 schema 가 없으면 그 도구는 스키마가 없는 도구가 된다.

### 스키마 형식

[4.3] 스키마는 객체다. 스키마의 필드 이름은 `type`, `description`, `properties`, `required`, `enum`, `minimum`, `maximum`, `items` 가운데 하나여야 한다. 필드는 모두 선택이다.

[4.4] type 은 문자열 `object`, `array`, `string`, `integer`, `number`, `boolean`, `null` 가운데 하나다.

[4.5] description 은 문자열이다. description 은 검증에 쓰지 않는다.

[4.6] properties 는 객체이고 그 필드의 값은 모두 스키마 형식에 맞는 스키마다.

[4.7] required 는 문자열을 담은 배열이다. 원소 가운데 같은 문자열이 둘 이상 있으면 안 된다. 빈 배열도 되고 properties 에 없는 이름이 있어도 된다. 같은 문자열인지는 이스케이프를 푼 값으로 판단한다. `["a","\u0061"]` 에는 같은 문자열이 둘 있다.

[4.8] enum 은 원소가 하나 이상인 배열이다. 원소는 어떤 JSON 값이어도 되고 같은 값이 둘 이상 있어도 된다.

[4.9] minimum 과 maximum 은 수다. minimum 이 maximum 보다 커도 스키마 형식에는 맞다.

[4.10] items 는 스키마 형식에 맞는 스키마다.

[4.11] 도구 정의의 schema 필드 값을 최상위 스키마라 한다. 최상위 스키마는 4.3~4.10 을 지키는 것에 더해 type 필드가 반드시 있고 그 값이 `object` 여야 하며 enum, minimum, maximum, items 필드가 없어야 한다. 4.3 과 달리 최상위 스키마에서는 type 을 생략할 수 없으므로 `{}` 나 type 없이 properties 만 있는 스키마는 최상위 스키마가 아니다.

[4.12] 모델의 요청 검사에 규칙 하나가 더해진다. 코드는 tool_schema 이고 1단계 4.25 의 tool_name 규칙 바로 다음, 1단계 4.26 의 role_order 규칙 앞에서 검사한다. 요청에 든 도구 정의 가운데 schema 가 있고 그 schema 가 4.11 의 최상위 스키마가 아닌 것이 하나라도 있으면 이 규칙을 어긴다. 1단계 4.31 의 다섯 규칙은 이 단계부터 tool_schema 를 더한 여섯 규칙이다. 각본에 남은 응답이 있는지는 요청이 여섯 규칙을 모두 지켰을 때만 확인한다. 그래서 각본이 비어 있어도 모델은 tool_schema 를 어긴 요청을 tool_schema 로 거절한다.

### 입력 검증

[4.13] 도구 단계에서 실행기는 도구 사용 블록마다 1단계 4.41 의 도구 정의 확인을 먼저 한다. 도구 정의가 있고 그 정의에 schema 가 있으면 블록의 input 을 schema 로 검증한다. 검증 오류가 하나 이상 나오면 가짜 결과를 찾지 않고 오류 결과를 만든다. 오류 결과의 내용은 `invalid input: ` 뒤에 검증 오류를 나온 순서대로 `; `(세미콜론과 공백)로 이어 붙인 문자열이다.

[4.14] 검증은 스키마 s 와 값 v 와 경로 p 를 받아 검증 오류를 내놓는 일이다. 도구 입력의 검증은 최상위 스키마, 블록의 input, 빈 경로로 시작한다. 실행기는 4.15~4.21 의 단계를 이 순서대로 거치며 오류를 모은다. 검증 오류 하나는 경로와 이유를 공백 하나로 이은 문자열이다.

[4.15] s 에 type 이 있으면 v 의 종류가 type 에 맞는지 확인한다. object 는 객체, array 는 배열, string 은 문자열, number 는 수, boolean 은 true 와 false, null 은 null 이 맞는다. integer 는 1단계 3.14 의 정수인 수가 맞는다. v 가 type 에 맞지 않으면 오류 `<p> expected <type>` 을 내고 이 s 와 v 의 검증을 여기서 끝낸다. 4.16~4.21 은 검사하지 않는다.

[4.16] s 에 enum 이 있고 v 가 enum 의 어떤 원소와도 같지 않으면 오류 `<p> not in enum` 을 낸다. 같은지는 1단계 3.8 로 판정한다.

[4.17] s 에 minimum 이 있고 v 가 수이며 v 가 minimum 보다 작으면 오류 `<p> below minimum <minimum>` 을 낸다. s 에 maximum 이 있고 v 가 수이며 v 가 maximum 보다 크면 오류 `<p> above maximum <maximum>` 을 낸다. 수의 크기는 수학적 값으로 비교한다. 오류에 적는 minimum 과 maximum 은 스키마에 쓰인 글자 그대로다. v 가 minimum 이나 maximum 과 같으면 오류가 아니다.

[4.18] minimum 오류를 maximum 오류보다 먼저 낸다. 4.16 의 enum 오류는 둘보다 먼저 낸다.

[4.19] s 에 required 가 있고 v 가 객체이면 required 의 이름을 배열 순서대로 확인해 v 에 없는 이름 n 마다 오류 `<p>/<n> required` 를 낸다. 경로 자리에는 빠진 필드의 경로를 적는다.

[4.20] s 에 properties 가 있고 v 가 객체이면 properties 의 필드를 properties 에 적힌 순서대로 하나씩 처리한다. 필드 이름 n 이 v 에도 있으면 그 필드의 스키마로 v 의 n 값을 경로 `<p>/<n>` 에서 검증한다. v 에 없는 이름은 건너뛴다. v 에만 있고 properties 에 없는 필드는 검증하지 않는다.

[4.21] s 에 items 가 있고 v 가 배열이면 원소를 0 번째부터 순서대로 items 로 검증한다. i 번째 원소의 경로는 `<p>/<i>` 다.

[4.22] 4.20 과 4.21 의 안쪽 검증이 낸 오류는 그 자리에서 바로 오류 목록에 들어간다. 그래서 오류는 바깥 값의 enum, minimum, maximum, required 오류, 안쪽 필드와 원소의 오류 순서로 나온다.

### fake 의 error

[4.23] `fake` 의 인자에는 `output` 대신 `error`(문자열)가 있을 수 있다. output 과 error 가운데 정확히 하나가 있어야 한다. error 가 있으면 그 도구 이름과 입력으로 도구를 실행할 때 도구가 실패한다.

[4.24] 같은 도구 이름과 같은 입력의 가짜 결과를 다시 정하면 output 과 error 에 상관없이 새 가짜 결과로 바뀐다.

### 도구 단계의 순서

[4.25] 도구 단계에서 블록 하나의 결과는 다음 순서로 정한다. 도구 정의가 없으면 1단계 4.41 의 `unknown tool: <name>` 오류 결과다. 정의에 schema 가 있고 검증 오류가 있으면 4.13 의 `invalid input: ...` 오류 결과다. 그다음 가짜 결과를 찾는다. 가짜 결과가 없으면 `no result for this input` 오류 결과다. 가짜 결과에 error 가 있으면 내용이 그 error 인 오류 결과이고 output 이 있으면 내용이 그 output 인 정상 결과다.

### 같은 오류의 되풀이

[4.26] `config` 의 인자에는 `repeat_limit` 필드가 있을 수 있다. repeat_limit 은 0 이상 1,000 이하의 정수이고 기본값은 0 이다. 0 이면 같은 오류가 몇 번 나와도 루프를 멈추지 않는다.

[4.27] 실행기는 ask 마다 도구 단계에서 만든 오류 결과를 센다. 블록의 name 과 오류 결과의 내용이 모두 같은 두 오류 결과는 같은 오류다. 블록의 id 와 input 은 따지지 않으므로 input 이 다른 두 블록의 오류 결과도 name 과 내용이 같으면 같은 오류다. 1단계 4.19 에서 ask 가 담은 `not executed` 오류 결과는 세지 않는다. 센 횟수는 ask 가 끝나면 버린다.

[4.28] 도구 단계가 끝나면 실행기는 결과를 담은 사용자 메시지를 기록에 더하고 출력한다. 그 뒤 repeat_limit 이 1 이상이고 이번 ask 에서 repeat_limit 번 이상 나온 오류가 하나라도 있으면 실행기는 다음 차례를 시작하지 않고 ask 를 종료 상태 repeated_error 로 끝낸다. done 줄의 문자열은 이 차례에서 받은 응답의 텍스트다.

[4.29] 1단계 4.43 의 종료 상태에 repeated_error 가 더해진다. repeated_error 로 끝난 뒤의 기록은 결과를 담은 사용자 메시지로 끝나므로 다음 ask 는 1단계 4.20 대로 그 메시지에 텍스트 블록을 더한다.

[4.30] 1단계 4.37 에 따라 차례 번호가 max_turns 와 같은 차례에서는 도구 단계를 거치지 않으므로 오류를 세지 않고 종료 상태는 max_turns 다.

## 5. 오류 규칙

[5.1] fake 의 인자에 output 과 error 가 둘 다 있거나 둘 다 없으면 `error bad_field` 를 출력한다. error 가 문자열이 아니어도 `error bad_field` 다.

[5.2] tool 의 schema 가 객체가 아니면 `error bad_field` 를 출력한다. schema 가 객체이지만 스키마 형식에 맞지 않으면 tool 은 오류가 아니다. 그런 도구가 정의되어 있으면 모델이 4.12 에 따라 요청을 거절한다.

[5.3] config 의 repeat_limit 이 0 이상 1,000 이하의 정수가 아니면 `error bad_field` 를 출력한다. 1단계 4.13 대로 오류를 낸 config 는 어떤 필드도 바꾸지 않는다.

## 6. 예시

[6.1] 예시의 표기는 1단계와 같다. 예시 블록 하나는 프로그램을 한 번 실행한 기록이다.

[6.2] 검증의 기본이다. b1 은 city 가 없고 nights 의 0.5 는 integer 가 아니다. guests 의 첫 원소는 age 가 minimum 보다 작고 둘째 원소는 age 가 없다. b2 는 `2.0` 이 integer 이고 필드 순서만 다르므로 검증을 통과하고 가짜 결과도 찾는다. b3 은 스키마에 없는 memo 가 있어도 검증을 통과하지만 가짜 결과의 입력과 같지 않다. b4 는 required 를 통과하고 properties 순서대로 city, nights 의 오류가 나온다.

```
> tool {"name":"book","schema":{"type":"object","properties":{"city":{"type":"string","enum":["서울","부산"]},"nights":{"type":"integer","minimum":1,"maximum":14},"guests":{"type":"array","items":{"type":"object","properties":{"age":{"type":"integer","minimum":0}},"required":["age"]}}},"required":["city","nights"]}}
> fake {"tool":"book","input":{"city":"부산","nights":2},"output":"예약 번호 51"}
> reply {"stop":"tool","content":[{"type":"tool","id":"b1","name":"book","input":{"nights":0.5,"guests":[{"age":-1},{}]}}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"b2","name":"book","input":{"nights":2.0,"city":"부산"}},{"type":"tool","id":"b3","name":"book","input":{"city":"부산","nights":2,"memo":"창가"}},{"type":"tool","id":"b4","name":"book","input":{"city":"대구","nights":"2"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"부산 2박을 예약했다. 예약 번호는 51이다."}]}
> ask {"text":"부산 2박 예약해 줘"}
user text "부산 2박 예약해 줘"
turn 1 messages=1 limit=1000
model tool b1 book {"nights":0.5,"guests":[{"age":-1},{}]}
stop tool
user result b1 error "invalid input: /city required; /nights expected integer; /guests/0/age below minimum 0; /guests/1/age required"
turn 2 messages=3 limit=1000
model tool b2 book {"nights":2.0,"city":"부산"}
model tool b3 book {"city":"부산","nights":2,"memo":"창가"}
model tool b4 book {"city":"대구","nights":"2"}
stop tool
user result b2 ok "예약 번호 51"
user result b3 error "no result for this input"
user result b4 error "invalid input: /city not in enum; /nights expected integer"
turn 3 messages=5 limit=1000
model text "부산 2박을 예약했다. 예약 번호는 51이다."
stop end
done end "부산 2박을 예약했다. 예약 번호는 51이다."
```

[6.3] 키워드가 적용되는 종류다. label 의 스키마에는 type 이 없으므로 문자열 `"a"` 는 minimum 과 상관없이 통과한다. g2 의 `1.0` 은 enum 의 1 과 같아 enum 은 통과하지만 minimum 3 보다 작다. g3 의 객체는 enum 의 객체와 같다. g4 의 ids 에서 `2.0` 은 통과하고 `"3"` 과 `4.5` 는 type 에서 멈추므로 enum 오류가 나오지 않는다. 4 는 integer 이지만 enum 에 없다.

```
> tool {"name":"tag","schema":{"type":"object","properties":{"label":{"minimum":3,"enum":["a",1,{"k":[1,2]}]},"count":{"type":"number","maximum":10},"ids":{"type":"array","items":{"type":"integer","enum":[1,2,3]}},"flag":{"type":"boolean"}}}}
> fake {"tool":"tag","input":{"label":"a"},"output":"a 로 붙였다"}
> fake {"tool":"tag","input":{"label":{"k":[1,2]}},"output":"k 로 붙였다"}
> reply {"stop":"tool","content":[{"type":"tool","id":"g1","name":"tag","input":{"label":"a"}},{"type":"tool","id":"g2","name":"tag","input":{"label":1.0}},{"type":"tool","id":"g3","name":"tag","input":{"label":{"k":[1.0,2e0]}}},{"type":"tool","id":"g4","name":"tag","input":{"count":10.5,"ids":[1,2.0,"3",4.5,4],"flag":0}}]}
> reply {"stop":"end","content":[{"type":"text","text":"둘은 붙였고 둘은 실패했다."}]}
> ask {"text":"태그를 붙여 줘"}
user text "태그를 붙여 줘"
turn 1 messages=1 limit=1000
model tool g1 tag {"label":"a"}
model tool g2 tag {"label":1.0}
model tool g3 tag {"label":{"k":[1.0,2e0]}}
model tool g4 tag {"count":10.5,"ids":[1,2.0,"3",4.5,4],"flag":0}
stop tool
user result g1 ok "a 로 붙였다"
user result g2 error "invalid input: /label below minimum 3"
user result g3 ok "k 로 붙였다"
user result g4 error "invalid input: /count above maximum 10; /ids/2 expected integer; /ids/3 expected integer; /ids/4 not in enum; /flag expected boolean"
turn 2 messages=3 limit=1000
model text "둘은 붙였고 둘은 실패했다."
stop end
done end "둘은 붙였고 둘은 실패했다."
```

[6.4] 도구 실패와 되풀이다. repeat_limit 이 2 다. 첫 차례에서 f1 의 도구 실패와 f2 의 검증 실패가 한 번씩 나온다. 둘째 차례에서 f1 과 같은 오류가 다시 나와 두 번이 되므로 셋째 차례를 시작하지 않는다. 둘째 응답에는 텍스트가 없어 done 줄의 문자열이 비었다. 다음 ask 는 결과를 담은 사용자 메시지에 텍스트를 더하므로 `messages=5` 다.

```
> config {"repeat_limit":2}
> tool {"name":"fetch","schema":{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}}
> fake {"tool":"fetch","input":{"url":"a.example"},"error":"connection refused: retry after 30 seconds"}
> fake {"tool":"fetch","input":{"url":"b.example"},"output":"<html>b</html>"}
> reply {"stop":"tool","content":[{"type":"tool","id":"f1","name":"fetch","input":{"url":"a.example"}},{"type":"tool","id":"f2","name":"fetch","input":{"link":"b.example"}}]}
> reply {"stop":"tool","content":[{"type":"tool","id":"f3","name":"fetch","input":{"url":"b.example"}},{"type":"tool","id":"f4","name":"fetch","input":{"url":"a.example"}}]}
> reply {"stop":"end","content":[{"type":"text","text":"a.example 은 접속이 거부됐다."}]}
> ask {"text":"두 페이지를 가져와 줘"}
user text "두 페이지를 가져와 줘"
turn 1 messages=1 limit=1000
model tool f1 fetch {"url":"a.example"}
model tool f2 fetch {"link":"b.example"}
stop tool
user result f1 error "connection refused: retry after 30 seconds"
user result f2 error "invalid input: /url required"
turn 2 messages=3 limit=1000
model tool f3 fetch {"url":"b.example"}
model tool f4 fetch {"url":"a.example"}
stop tool
user result f3 ok "<html>b</html>"
user result f4 error "connection refused: retry after 30 seconds"
done repeated_error ""
> ask {"text":"어떻게 됐어?"}
user text "어떻게 됐어?"
turn 1 messages=5 limit=1000
model text "a.example 은 접속이 거부됐다."
stop end
done end "a.example 은 접속이 거부됐다."
```

[6.5] 스키마 형식 검사다. 최상위 type 이 object 가 아닌 스키마, 최상위에 type 이 없는 스키마, type 값이 목록에 없는 스키마, 최상위에 minimum 이 있는 스키마, 지원하지 않는 키워드가 든 스키마는 모두 tool_schema 로 거절된다. 같은 이름으로 다시 정의해 스키마를 고치면 요청이 통과한다. 거절된 ask 는 각본을 쓰지 않는다.

```
> reply {"stop":"end","content":[{"type":"text","text":"계산했다."}]}
> tool {"name":"calc","schema":{"type":"array"}}
> ask {"text":"1+1"}
user text "1+1"
turn 1 messages=1 limit=1000
reject tool_schema
done rejected ""
> tool {"name":"calc","schema":{"properties":{"x":{"type":"number"}}}}
> ask {"text":"1+1"}
user text "1+1"
turn 1 messages=1 limit=1000
reject tool_schema
done rejected ""
> tool {"name":"calc","schema":{"type":"object","properties":{"x":{"type":"float"}}}}
> ask {"text":"1+1"}
user text "1+1"
turn 1 messages=1 limit=1000
reject tool_schema
done rejected ""
> tool {"name":"calc","schema":{"type":"object","minimum":0}}
> ask {"text":"1+1"}
user text "1+1"
turn 1 messages=1 limit=1000
reject tool_schema
done rejected ""
> tool {"name":"calc","schema":{"type":"object","properties":{"x":{"type":"number","exclusiveMinimum":0}}}}
> ask {"text":"1+1"}
user text "1+1"
turn 1 messages=1 limit=1000
reject tool_schema
done rejected ""
> tool {"name":"calc","schema":{"type":"object","description":"두 수를 더한다","properties":{"x":{"type":"number","description":"더할 수"}},"required":["x"]}}
> ask {"text":"1+1"}
user text "1+1"
turn 1 messages=1 limit=1000
model text "계산했다."
stop end
done end "계산했다."
```

[6.6] 입력 오류다. schema 의 type 값이 목록에 없어도 tool 자체는 오류가 아니다.

```
> fake {"tool":"fetch","input":{"url":"a"},"output":"A","error":"실패"}
error bad_field
> fake {"tool":"fetch","input":{"url":"a"}}
error bad_field
> fake {"tool":"fetch","input":{"url":"a"},"error":404}
error bad_field
> tool {"name":"fetch","schema":"object"}
error bad_field
> tool {"name":"fetch","schema":{"type":"nope"}}
> config {"repeat_limit":-1}
error bad_field
> config {"repeat_limit":1001}
error bad_field
> config {"repeat_limit":0}
```

## 7. 검사 방식

[7.1] 입력 방식, 종료 코드 검사, 표준 출력 비교, 일반 테스트 제한 시간 10초, 입력 한계는 1단계와 같다.

[7.2] 대규모 테스트는 스키마가 있는 도구 10개, 도구 사용 블록 30,000개 안팎으로 이뤄진다. 도구마다 enum 이 셋이다. 하나는 원소가 110,000개 안팎이고 items 스키마에 들어 있어 도구 입력의 배열 원소마다 검사한다. 나머지 둘은 원소가 3,000개다. 한 번 실행하는 동안 enum 검사는 450,000번 안팎이다. 도구 입력의 수와 객체는 enum 원소와 표기나 필드 순서를 다르게 쓴다. repeat_limit 을 크게 두고 서로 다른 오류를 많이 낸다. 제한 시간은 15초다. 검증할 때마다 enum 원소를 값과 하나씩 비교하는 풀이는 원소를 미리 정규화한 문자열로 바꿔 두고 그 문자열을 비교해도 이 제한 시간을 넘기도록 규모를 정했다.

[7.3] 2단계 검사 입력에는 1단계 3.12, 3.13 이 출력을 정하지 않은 줄 가운데 이 문서가 뜻을 정하지 않은 줄이 나오지 않는다.

[7.4] 3단계부터 마지막 단계까지 검사할 때도 2단계 테스트를 다시 실행한다.
