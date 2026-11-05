# 03. 오류 응답과 프로토콜 버전

## 1. 개요

[1.1] 앞의 두 단계에서 정상 입력으로 제한했던 JSON 문법, 메시지 외형, 메서드, 버전과 클라이언트 기능을 검증한다. 서로 다른 오류가 한 요청에 겹쳐도 같은 응답을 내고, 오류 뒤의 다음 요청을 계속 처리한다. 목표 시간은 1~2시간이다. 앞 단계의 메서드와 정상 출력, 카탈로그와 직렬화 규칙은 바꾸지 않는다.

[1.2] 근거는 MCP `2026-07-28` 판의 [기본 메시지·오류·메타데이터](https://modelcontextprotocol.io/specification/2026-07-28/basic/index), [버전 규약](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning), [JSON-RPC 오류 객체](https://www.jsonrpc.org/specification#error_object)다. [고정한 schema.ts](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/271ecc9accafdd9b83a3c869fa67c22953b2af80/schema/2026-07-28/schema.ts)의 `ParseError`, `InvalidRequestError`, `MethodNotFoundError`, `InvalidParamsError`, `UnsupportedProtocolVersionError`, `MissingRequiredClientCapabilityError`, `JSONRPCErrorResponse`를 근거로 삼는다. 오류를 고르는 우선순위와 message 문자열은 이 과제에서 고정한다.

## 2. 배경 개념

[2.1] JSON 문법 오류는 메시지의 필드에 의미를 붙이기 전의 문제다. 문법이 맞더라도 최상위 값이 숫자이거나 method 타입이 잘못되면 JSON-RPC 요청이 아니다. 요청 외형이 유효해도 메서드가 없거나 인자가 틀릴 수 있다. 이 구별은 클라이언트가 메시지 전체를 고쳐야 하는지, 호출 대상이나 인자만 고치면 되는지 알려 준다.

[2.2] 버전 문자열이 있다는 것과 그 버전을 서버가 지원한다는 것은 다르다. 필수 메타데이터가 빠지거나 타입이 틀리면 Invalid params이고, 문자열은 있지만 지원하지 않으면 Unsupported protocol version이다. 서버는 이전 요청에서 버전을 확인했더라도 현재 요청의 메타데이터를 다시 판단한다.

[2.3] 필요한 클라이언트 기능이 없다는 오류는 모르는 메서드라는 오류와 다르다. 전자는 서버가 메서드를 알지만 요청자가 필요한 기능을 선언하지 않은 경우다. 이 과제에서는 견적 도구가 그 사례다. 한 번 선언한 기능은 연결 전체의 설정이 아니며 뒤 요청에서 빠지면 그 뒤 요청은 기능 없이 처리한다.

[2.4] MCP 이 판의 `JSONRPCErrorResponse`는 id를 복구하지 못한 경우 id를 생략할 수 있다. 기본 JSON-RPC 문서의 id null 예시를 이 과제 출력에 적용하지 않는다. MCP 요청의 id는 null일 수 없고 이 과제의 오류 응답도 null id를 만들지 않는다. `-32020`은 HTTP 헤더 불일치 오류이므로 표준 입출력만 쓰는 이 트랙에서는 발생하지 않는다.

## 3. 입출력 규칙

[3.1] 이제 빈 줄, 공백만 있는 줄, 끝이 잘린 JSON, 잘못된 이스케이프, 후행 쉼표, 한 줄에 JSON 값이 둘인 경우도 들어올 수 있다. 각 물리적인 줄 전체가 JSON 값 하나로 파싱되지 않으면 코드 `-32700`이다. 앞뒤 JSON 공백은 허용한다. BOM은 입력에 없으며 객체 키 중복과 단독 서로게이트도 계속 입력에 없다. 마지막 입력 줄 뒤의 줄바꿈 유무는 메시지 의미를 바꾸지 않는다.

[3.2] JSON 문법이 유효하면 먼저 메시지 외형을 판단한다. 최상위는 객체여야 하고 jsonrpc는 문자열 `"2.0"`이어야 한다. method가 있으면 문자열이어야 하고 result와 error는 모두 없어야 한다. id까지 있으면 요청이며 id는 1단계의 유효한 문자열 또는 정수여야 한다. id가 없으면 알림이며 params는 없거나 객체여야 한다. method가 없는 경우에는 1단계 3.3의 응답 모양만 유효하다. 그 밖의 모양, 최상위 배열과 빈 배열, null id, 소수 값 id, 불리언 id는 모두 코드 `-32600`이다. 배치 호출은 배열의 각 원소를 처리하지 않고 배열 전체에 대해 오류 한 줄만 낸다.

[3.3] 유효한 요청의 params가 없거나 객체가 아니면 요청 외형 오류가 아니라 `-32602`다. 유효한 알림과 유효한 응답은 의미 검증에 들어가기 전에 버린다. 그러므로 알림에 `_meta`가 없거나, params 안의 버전이 잘못됐거나, 없는 도구를 불러도 출력하지 않는다. jsonrpc나 method 자체가 틀려 3.2를 만족하지 못한 객체는 알림이 아니며 `-32600`이다. 잘못된 응답의 외형도 같은 규칙으로 `-32600`이다.

[3.4] JSON 파싱이 실패하면 오류 응답에서 id를 생략한다. 파싱에는 성공했지만 외형이 잘못된 객체라면 그 안의 id가 유효한 문자열 또는 정수일 때만 그 id를 돌려준다. 그 밖에는 id를 생략한다. 최상위가 객체가 아닐 때도 생략한다. 메서드·인자·버전·기능 오류는 유효한 요청에 대한 것이므로 항상 요청 id를 포함한다. error 응답에는 result나 resultType을 넣지 않는다.

## 4. 명령과 검증 규칙

[4.1] 유효한 요청의 오류는 다음 순서로 하나만 고른다. 먼저 params와 `_meta` 및 필수 메타데이터의 존재·타입을 검사한다. 다음으로 프로토콜 버전 지원 여부, 메서드 존재 여부, 해당 메서드의 인자 유효성, 필요한 클라이언트 기능 선언 여부, 도메인 실행 순서로 판단한다. 앞에서 실패하면 뒤 조건은 오류 선택에 영향을 주지 않는다. JSON 문법과 메시지 외형 검사는 이 순서보다 먼저다.

[4.2] 필수 메타데이터의 존재·타입 규칙은 1단계 3.4와 같다. clientCapabilities 안에 experimental이 있으면 객체여야 하고 그 각 값도 객체여야 한다. 이 타입 규칙에 어긋나면 `-32602`다. experimental에 알려지지 않은 키가 있더라도 값이 객체이면 무시한다. experimental 이외의 clientCapabilities 키는 값과 무관하게 무시한다. 이 과제는 원문의 모든 선택 기능 스키마를 검증하는 범용 검증기가 아니다.

[4.3] 서버가 지원하는 프로토콜 버전은 문자열 `"2026-07-28"` 하나다. 다른 문자열은 빈 문자열을 포함해 `-32022`이며 앞뒤 공백을 제거하거나 날짜 형태를 추측하지 않는다. 이 오류의 data는 `requested`에 입력 버전 문자열, `supported`에 `["2026-07-28"]`을 가진 객체다. 버전 키가 빠지거나 null·수·객체인 경우에는 `-32022`가 아니라 앞선 존재·타입 검사에서 `-32602`다.

[4.4] 알려진 메서드는 `tools/list`, `tools/call`, `resources/list`, `resources/read`, `prompts/list`, `prompts/get` 여섯 개다. 메서드 이름은 대소문자를 구분하며 다른 문자열은 모두 `-32601`이다. 범위 밖인 initialize와 server/discover도 같은 오류다. params와 메타데이터가 올바르지 않으면 모르는 메서드라도 먼저 `-32602`를 반환한다. 메타데이터 타입은 맞지만 버전이 지원되지 않으면 먼저 `-32022`다.

[4.5] 알려진 메서드는 앞 단계의 인자 규칙을 적용한다. 모르는 도구 이름은 알려진 tools/call의 잘못된 인자이므로 `-32602`다. 마찬가지로 모르는 프롬프트, 리소스, cursor도 `-32602`다. 필수 인자의 누락과 null, 잘못된 타입을 구별해 별도 문구를 만들지 않는다. 도구 인자의 형식이 유효한데 상품이 없거나 재고가 부족하면 계속 도구 실행 오류이며 프로토콜 오류로 바꾸지 않는다.

[4.6] 유효한 cart_quote 인자를 받은 뒤, 현재 요청의 clientCapabilities.experimental에 `com.cresvoid/quote-preview` 키가 없으면 `-32021`이다. 이 오류의 data는 `requiredCapabilities:{"experimental":{"com.cresvoid/quote-preview":{}}}`만 가진 객체다. 키가 있지만 그 값이 객체가 아니면 4.2의 앞선 타입 검사에서 `-32602`다. 키가 있고 값이 객체이면 내부 필드와 무관하게 지원한다고 본다. 다른 도구·리소스·프롬프트는 이 기능을 요구하지 않는다.

[4.7] 오류의 우선순위 때문에 cart_quote의 lines가 비었고 기능도 없으면 `-32602`다. 인자 형식이 유효하고 모르는 상품이 들어 있지만 기능이 없으면 `-32021`이다. 같은 요청에 기능을 선언하면 도구 실행으로 넘어가 `UNKNOWN_PRODUCT <id>`를 isError true로 반환한다. 앞 요청의 기능 선언, 도구 실행 성공과 실패는 다음 요청을 바꾸지 않는다.

## 5. 오류 응답 규칙

[5.1] 코드와 message는 `-32700`과 `Parse error`, `-32600`과 `Invalid Request`, `-32601`과 `Method not found`, `-32602`와 `Invalid params`, `-32022`와 `Unsupported protocol version`, `-32021`과 `Missing required client capability`로 각각 고정한다. 영문 대소문자와 공백도 그대로 쓴다. 앞의 네 오류에는 data를 생략한다. 뒤의 두 오류에는 4.3과 4.6의 data를 반드시 넣는다. 다른 오류 코드나 내부 예외의 메시지를 출력하지 않는다.

[5.2] 최상위 필드는 `jsonrpc`, `error`와 3.4에서 허용하는 경우의 id뿐이다. error에는 `code`, `message`와 정의된 경우의 data만 넣는다. 1단계의 정규화에 따라 최상위 키도 error, id, jsonrpc 순서로 출력한다. id가 없으면 error, jsonrpc 순서다. 오류가 연속해서 오더라도 줄마다 독립적으로 응답하고 EOF까지 계속 처리한다. 잘못된 JSON을 처리하다가 프로세스를 종료하면 실패다.

## 6. 예시

[6.1] 잘린 JSON은 id를 복구하려 하지 않는다. 문법이 맞더라도 null id는 허용하지 않는다. 유효한 외형의 알림은 메타데이터가 잘못돼도 무시한다. id가 없는 오류 응답도 무시한다. 마지막 요청은 유효한 문자열 id를 보존한다.

입력:
```text
{"jsonrpc":"2.0","id":1,
{"jsonrpc":"2.0","id":null,"method":"tools/list","params":{}}
{"jsonrpc":"2.0","method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"old"}}}
{"jsonrpc":"2.0","error":{"code":-32600,"message":"peer error"}}
{"jsonrpc":"2.0","id":"x","method":"unknown","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}
```

출력:
```jsonl
{"error":{"code":-32700,"message":"Parse error"},"jsonrpc":"2.0"}
{"error":{"code":-32600,"message":"Invalid Request"},"jsonrpc":"2.0"}
{"error":{"code":-32601,"message":"Method not found"},"id":"x","jsonrpc":"2.0"}
```

[6.2] 알 수 없는 메서드라도 지원하지 않는 버전을 먼저 보고한다. 다음 두 요청의 인자는 같지만 현재 요청에 기능이 있는지에 따라 도구 실행까지 진행할 수 있는지가 달라진다.

입력:
```jsonl
{"jsonrpc":"2.0","id":1,"method":"unknown","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2025-11-25","io.modelcontextprotocol/clientCapabilities":{}}}}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"name":"cart_quote","arguments":{"lines":[{"productId":"p-999","quantity":1}]}}}
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"experimental":{"com.cresvoid/quote-preview":{}}}},"name":"cart_quote","arguments":{"lines":[{"productId":"p-999","quantity":1}]}}}
```

출력:
```jsonl
{"error":{"code":-32022,"data":{"requested":"2025-11-25","supported":["2026-07-28"]},"message":"Unsupported protocol version"},"id":1,"jsonrpc":"2.0"}
{"error":{"code":-32021,"data":{"requiredCapabilities":{"experimental":{"com.cresvoid/quote-preview":{}}}},"message":"Missing required client capability"},"id":2,"jsonrpc":"2.0"}
{"id":3,"jsonrpc":"2.0","result":{"content":[{"text":"UNKNOWN_PRODUCT p-999","type":"text"}],"isError":true,"resultType":"complete"}}
```

## 7. 검사 방식

[7.1] 1·2단계와 같은 줄 단위 입력과 정규 JSON 문자열 비교를 사용한다. 테스트마다 새 프로세스를 시작해 입력을 보내고 닫으며 빌드를 제외한 제한 시간은 테스트 하나당 15초다. 고정 테스트 10~14개와 생성 테스트 2~3개로 문법·외형·메타데이터·버전·메서드·인자·기능·도메인의 단독 오류와 겹친 오류, 무응답 메시지와 복구 후 정상 요청을 검사한다. 앞의 두 단계 테스트도 누적 실행한다.

[7.2] 테스트 하나의 최대 규모는 2,000줄이며 줄 길이 32KiB, 중첩 깊이 16이라는 기존 상한을 유지한다. 문법 오류인 줄도 길이 상한을 지키며 손상 전 JSON의 중첩 깊이는 16 이하이다. 별도 성능용 대규모 테스트는 없다. 대량 처리를 위한 자료구조보다 줄 단위 복구와 오류 우선순위가 검사 대상이다. 출력 상한은 64MiB이며 종료 코드 0을 요구한다.
