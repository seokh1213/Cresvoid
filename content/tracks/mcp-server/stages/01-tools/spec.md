# 01. 메시지 구분과 도구 호출

## 1. 개요

[1.1] 상품 카탈로그를 제공하는 MCP 서버를 만든다. 클라이언트는 표준 입력으로 JSON-RPC 메시지를 보내고 서버는 표준 출력으로 응답한다. 이 단계에서는 메시지의 종류를 구분하고 도구 목록과 도구 호출을 구현한다. 목표 시간은 2~3시간이다. 시작 코드에는 `main`의 `TODO()`만 있으며 JSON 파서와 직렬화기도 직접 마련한다. Kotlin 표준 라이브러리와 JDK 21만 사용한다.

[1.2] 이 트랙은 MCP `2026-07-28` 판의 일부를 구현한다. HTTP, 인증, 초기화, `server/discover`, 구독, 취소, 진행 알림, 다중 왕복 요청은 범위 밖이다. 서버가 먼저 요청이나 알림을 보내는 일도 없다. `initialize`로 상태를 만들지 않으며 각 요청은 자기 안의 정보만으로 처리한다. 2단계는 리소스와 프롬프트를 더하고 3단계는 잘못된 입력에 대한 처리를 완성한다. 뒤 단계에서도 이 단계에 정의된 입력의 출력은 그대로 유지한다.

[1.3] 규약의 근거는 [MCP 기본 규약](https://modelcontextprotocol.io/specification/2026-07-28/basic/index), [도구 규약](https://modelcontextprotocol.io/specification/2026-07-28/server/tools), [JSON-RPC 2.0](https://www.jsonrpc.org/specification)이다. 필드와 오류 코드는 [고정한 schema.ts](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/271ecc9accafdd9b83a3c869fa67c22953b2af80/schema/2026-07-28/schema.ts)의 `RequestMetaObject`, `JSONRPCRequest`, `JSONRPCErrorResponse`, `Result`, `CacheableResult`, `ListToolsResult`, `CallToolResult`를 따른다. 이 문서의 상품, 도구 이름, 응답 문구와 출력 정규화는 과제 전용 계약이다. 원문의 선택 기능을 모두 구현할 필요는 없으며 검사할 동작은 이 문서의 번호 문단에 모두 정의한다.

## 2. 배경 개념

[2.1] JSON-RPC의 요청은 메서드 실행을 요구하는 메시지다. 요청의 `id`는 그 응답이 어느 요청에 대한 것인지 연결한다. `id`가 숫자 `7`인 경우와 문자열 `"7"`인 경우는 다르다. 알림은 `id` 없이 보내며 성공 응답도 오류 응답도 받지 않는다. 응답에는 `method` 대신 `result` 또는 `error`가 있다. 응답을 새 요청으로 취급하면 서로 응답을 주고받는 일이 생길 수 있다.

[2.2] MCP의 도구는 이름과 입력 JSON Schema로 공개되는 기능이다. `tools/list`는 호출 가능한 도구의 설명이고 `tools/call`은 그중 하나의 실행이다. 고정한 판의 원문은 모르는 도구와 요청 구조 오류를 프로토콜 오류로 구분하고, 범위 밖 값 같은 도구 입력값 검증 실패는 `isError:true`인 도구 실행 오류로 돌려주도록 권고한다. 이 과제는 검증 경계를 단순화하기 위해 4.4의 도구 입력 스키마 위반도 프로토콜 오류로 처리한다. 이는 MCP의 일반 규칙이 아니라 과제 전용 규칙이다. 상품 부재나 재고 부족은 도구 실행 오류로 처리한다.

[2.3] 이 MCP 판에서 요청의 `params._meta`에는 버전과 클라이언트 기능이 매번 들어간다. 앞 요청에 있던 기능은 다음 요청의 권한이나 기능 선언이 되지 않는다. 클라이언트 기능은 서버의 도구 목록과도 다르다. 상품 조회를 호출하려고 클라이언트가 `tools` 기능을 선언할 필요는 없다. 이 과제의 견적 도구에만 별도 기능을 요구하며 그 기능은 4절에서 정의한다.

[2.4] JSON-RPC 수준에서 성공적으로 처리한 메시지는 `result`를 가지며, 이 판의 모든 `result`에는 `resultType`이 필요하다. 도구 실행 오류도 전달 자체는 완료됐으므로 `resultType`이 `"complete"`인 `result`에 `isError:true`를 싣는다. `error`는 프로토콜 오류를 전달한다. 목록 결과의 `ttlMs`는 캐시 유효 기간의 힌트이고 `cacheScope`는 공유 가능한 범위를 뜻한다. 이 과제에서는 캐시를 구현하지 않고 두 필드의 값을 고정한다.

## 3. 입출력 규칙

[3.1] 입력은 UTF-8이며 물리적인 한 줄이 메시지 하나다. 메시지 구분자는 LF이며 CR은 JSON 공백으로 취급한다. 따라서 CRLF 입력의 CR도 JSON 공백이다. 줄 길이 상한에는 구분자인 LF를 포함하지 않는다. EOF까지 순서대로 처리하고 종료 코드 0으로 끝낸다. 입력 문자열 속 줄바꿈은 `\n`처럼 JSON 이스케이프로 들어온다. 빈 줄, JSON 배열을 최상위에 둔 배치 호출, 문법 오류는 이 단계 입력에 없다. 한 테스트는 메시지 1~2,000개이며 한 줄은 UTF-8 기준 32KiB 이하, 중첩 깊이는 16 이하이다. 이 상한은 트랙 전체에 적용한다.

[3.2] 요청은 `jsonrpc:"2.0"`, `id`, 문자열 `method`를 가진 객체이며 `result`와 `error`는 없다. `id`는 문자열 또는 절댓값이 9,007,199,254,740,991 이하인 정수 값이다. 숫자는 소수점이나 지수 표기가 있어도 수학적으로 정수이면 유효하다. 문자열은 빈 문자열도 허용하고 디코딩한 값 그대로 응답에 돌려준다. 숫자 id는 정수의 십진수 표기로 돌려주며 `-0`은 `0`으로 쓴다. 처리한 id를 기억하거나 중복 사용을 거절하지 않는다. 요청은 순차 처리하므로 동시에 대기 중인 요청은 없다.

[3.3] `jsonrpc:"2.0"`과 문자열 `method`가 있고 `id`, `result`, `error`가 없는 객체는 알림이다. 알림의 `params`는 없거나 객체다. 모든 알림을 아무 동작 없이 무시한다. 알림에는 요청용 `_meta`가 없어도 되며, 모르는 메서드나 잘못된 메서드 인자가 있어도 응답하지 않는다. 유효한 JSON-RPC 응답도 입력에 섞일 수 있으며 무시한다. 응답은 `jsonrpc:"2.0"`과 객체인 `result` 또는 `error` 중 하나를 가지며 `method`와 `params`는 없다. result 응답에는 유효한 id가 필수다. error 응답의 id는 생략할 수 있고 있으면 유효해야 하며 error 안에는 정수 code와 문자열 message가 있다. 이 단계에는 이 세 종류 이외의 메시지 모양이 들어오지 않는다.

[3.4] 요청의 `params`와 그 안의 `_meta`는 객체여야 한다. `_meta`의 `io.modelcontextprotocol/protocolVersion`은 문자열, `io.modelcontextprotocol/clientCapabilities`는 객체여야 하며 둘 다 필수다. 이 둘 또는 상위 객체가 빠지거나 타입이 틀리면 `-32602`를 반환한다. 이 단계에서 버전 문자열은 항상 `"2026-07-28"`이다. `clientInfo`, 다른 메타데이터, 문서가 정의하지 않은 최상위 필드와 `params` 필드는 무시한다. 단, `arguments` 안의 필드는 도구의 입력 스키마를 따른다.

[3.5] 응답은 입력 순서대로 쓰며 요청마다 정확히 한 줄을 쓴다. 성공 응답의 최상위 필드는 `jsonrpc`, `id`, `result`뿐이고 프로토콜 오류 응답은 `jsonrpc`, `id`, `error`뿐이다. 3단계에서 id를 복구할 수 없는 오류에 한해 `id`를 생략한다. 결과와 오류에는 정의된 필드만 쓰며 선택 필드를 임의로 추가하지 않는다. 표준 출력에 로그나 빈 줄을 쓰지 않는다. EOF 전에 각 응답 줄을 출력하고 flush한다.

[3.6] 엔진은 JSON 의미가 아니라 출력 문자열을 비교하므로 출력 JSON을 정규화한다. 모든 객체의 키를 재귀적으로 ASCII 사전순으로 정렬하고 키·값 사이와 원소 사이에 공백을 넣지 않는다. 출력의 모든 키는 ASCII다. 배열 순서는 각 명령의 규칙을 따른다. 문자열의 큰따옴표와 역슬래시는 각각 `\"`, `\\`로, BS·FF·LF·CR·TAB은 각각 `\b`, `\f`, `\n`, `\r`, `\t`로 쓴다. 나머지 U+0000~U+001F는 소문자 16진수 네 자리의 `\u00xx`로 쓰고 `/`와 그 밖의 문자는 그대로 쓴다. 출력 숫자는 지수·소수점·불필요한 선행 0 없는 십진수 정수다. 객체의 입력 키 순서와 문자열 이스케이프 표기는 결과에 영향을 주지 않는다.

[3.7] JSON 객체에 중복 키는 들어오지 않으며 문자열에는 단독 서로게이트가 들어오지 않는다. 입력의 숫자는 유한한 JSON 수이며 유효한 id 이외의 수는 절댓값이 1,000,000,000 이하이다. 입력 문자열은 디코딩 후 2,048 UTF-16 코드 단위 이하이다. 문자열 사전순은 UTF-16 코드 단위를 앞에서부터 비교하는 Kotlin의 String.compareTo 순서다. 1·2단계에서는 clientCapabilities에 experimental이 있으면 그 값과 그 안의 각 값은 항상 객체다. 잘못된 JSON, 잘못된 메시지 모양과 기능 객체의 타입 오류는 3단계에서 추가한다.

## 4. 명령 규칙

[4.1] 카탈로그는 다섯 상품으로 고정한다. `p-100`의 이름은 `머그 "밤"`, 가격은 12000, 재고는 8이다. `p-200`은 `드립 필터`, 4500, 0이다. `p-300`은 이름이 `원두` 뒤에 LF 하나와 `중배전`을 이은 문자열이며 가격은 18000, 재고는 5이다. `p-400`은 이름이 `서버` 뒤에 역슬래시 하나와 `유리`를 이은 문자열이며 가격은 24000, 재고는 3이다. `p-500`은 `계량 스푼`, 7000, 20이다. 가격 단위는 원이고 상품 id는 대소문자를 구분한다. 모든 도구는 읽기 전용이며 요청이 성공하거나 실패해도 카탈로그와 재고는 바뀌지 않는다.

[4.2] `tools/list`는 `resultType:"complete"`, `ttlMs:0`, `cacheScope:"public"`, `tools` 배열을 반환한다. 배열은 `cart_quote`, `product_get` 순서이며 두 도구를 모두 싣는다. 클라이언트 기능에 따라 목록을 거르지 않는다. `cursor`가 없을 때만 첫 목록을 요청한 것으로 보며 이 목록은 한 페이지뿐이므로 `nextCursor`를 생략한다. `cursor`가 존재하면 빈 문자열을 포함한 모든 값에 `-32602`를 반환한다. 2단계에서도 이 목록의 페이지 규칙은 바꾸지 않는다.

[4.3] 각 도구 객체에는 `name`, `description`, `inputSchema`만 넣는다. `cart_quote`의 description은 `장바구니의 재고와 합계를 확인한다.`이고 `product_get`의 description은 `상품 한 개를 조회한다.`이다. 두 스키마의 루트는 `type:"object"`, `additionalProperties:false`이며 `properties`와 `required`를 가진다. `product_get`의 properties는 `productId:{"type":"string"}` 하나이고 required는 `["productId"]`다. `cart_quote`의 properties는 `lines` 하나이며 그 값은 `type:"array"`, `minItems:1`, `maxItems:20`, `items`를 가진다. items는 `type:"object"`, `additionalProperties:false`, properties가 `productId:{"type":"string"}`, `quantity:{"type":"integer","minimum":1,"maximum":1000}`인 스키마이고 required는 `["productId","quantity"]`다. cart_quote 루트의 required는 `["lines"]`다. `$schema`, `title`, `outputSchema` 등은 싣지 않는다.

[4.4] `tools/call`의 `params.name`은 문자열이며 두 도구 이름 중 하나여야 한다. `params.arguments`는 필수 객체이며 4.3의 해당 스키마를 만족해야 한다. JSON 수인 quantity는 표기에 관계없이 수학적으로 정수이고 1~1000일 때 유효하다. 이 과제 전용 규칙으로 이름·arguments의 누락, 타입 오류, 모르는 이름, 추가 인자와 스키마 위반은 모두 `-32602`다. 여러 인자가 잘못돼도 상세 원인을 구분해 응답하지 않는다.

[4.5] `product_get`은 해당 상품을 찾으면 텍스트 `id=<상품 id>`, `name=<이름>`, `price=<가격>`, `stock=<재고>`를 LF로 이어 반환한다. 마지막 LF는 붙이지 않는다. `<...>`는 실제 값으로 치환하며 상품 이름 자체의 LF와 역슬래시는 보존한다. 찾지 못하면 텍스트가 `UNKNOWN_PRODUCT <요청한 productId>`인 도구 실행 오류를 반환한다. 빈 문자열이나 공백이 있는 productId도 타입이 문자열이면 인자 검증을 통과하고 카탈로그와 정확히 일치하는지 판단한다.

[4.6] `cart_quote`는 과제 전용 클라이언트 기능 `experimental` 안의 `com.cresvoid/quote-preview`를 요구한다. 이 키의 값은 객체이면 되며 내부 필드는 무시한다. 선언 예는 `{"experimental":{"com.cresvoid/quote-preview":{}}}`다. 이는 견적 표시 기능을 클라이언트가 지원한다는 이 과제의 약속이며 일반적인 MCP 도구 호출에 필요한 표준 기능은 아니다. 이 단계의 cart_quote 요청에는 이 선언이 항상 있다. 선언이 없는 요청의 오류와 기능 객체의 타입 검사는 3단계에서 추가한다. 선언을 다른 요청으로부터 물려받아서는 안 된다.

[4.7] `cart_quote`는 먼저 같은 productId의 quantity를 합친다. 알 수 없는 상품이 하나라도 있으면 그 id들을 문자열 사전순으로 정렬했을 때 첫 id에 대해 `UNKNOWN_PRODUCT <id>`를 반환한다. 모든 상품이 있으면 합산 수량이 재고보다 큰 상품을 같은 순서로 골라 `OUT_OF_STOCK <id>`를 반환한다. 재고와 수량이 같으면 성공한다. 성공 텍스트는 `total=<가격×합산 수량의 총합>`과 `items=<서로 다른 상품 수>`를 LF 하나로 이은 문자열이다. 마지막 LF는 없다. 수량을 합친 값에는 개별 줄의 상한 1000을 다시 적용하지 않는다.

[4.8] 도구 호출의 result에는 `resultType:"complete"`, `content`, `isError`만 넣는다. content는 `type:"text"`와 `text`만 가진 객체 하나의 배열이다. 4.5 또는 4.7에서 성공하면 isError는 false이며 상품 부재나 재고 부족이면 true다. `structuredContent`는 넣지 않는다. 도구 실행 오류에서도 JSON-RPC 최상위 `error`를 쓰지 않는다.

## 5. 오류 규칙

[5.1] 이 단계의 프로토콜 오류는 코드 `-32602`, message `Invalid params`로 통일하며 `data`는 생략한다. error 객체에는 `code`와 `message`만 넣고 id는 요청에서 돌려준다. `result`, `resultType`, `isError`는 프로토콜 오류 응답에 넣지 않는다. 한 요청이 실패해도 다음 입력 줄을 계속 처리한다.

[5.2] 이 단계 입력은 알려진 메서드의 요청, 3.3의 알림과 응답뿐이다. 잘못된 `_meta`와 도구 인자, cursor, 도메인 실행 실패는 검사한다. 지원하지 않는 버전, 필요한 기능의 누락, 잘못된 JSON, 잘못된 요청 외형, 모르는 메서드는 3단계 입력에서 추가한다. 이 단계에 없는 오류에 임시 응답 형식을 정할 필요는 없다.

## 6. 예시

[6.1] 다음 입력에는 알림, 문자열 id의 요청, 서버가 무시할 응답, 도구 실행 오류가 섞여 있다. 출력은 두 줄이다. 상품 이름의 큰따옴표와 줄 구분자는 JSON 문자열 안에서 이스케이프한다.

입력:
```jsonl
{"jsonrpc":"2.0","method":"notifications/example","params":{"anything":1}}
{"jsonrpc":"2.0","id":"7","method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"name":"product_get","arguments":{"productId":"p-100"}}}
{"jsonrpc":"2.0","id":99,"result":{"resultType":"complete"}}
{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"name":"product_get","arguments":{"productId":"p-999"}}}
```

출력:
```jsonl
{"id":"7","jsonrpc":"2.0","result":{"content":[{"text":"id=p-100\nname=머그 \"밤\"\nprice=12000\nstock=8","type":"text"}],"isError":false,"resultType":"complete"}}
{"id":7,"jsonrpc":"2.0","result":{"content":[{"text":"UNKNOWN_PRODUCT p-999","type":"text"}],"isError":true,"resultType":"complete"}}
```

[6.2] 중복 줄의 수량을 합치면 p-100은 3개, p-300은 1개다. 합계는 12000×3+18000=54000이며 상품 종류는 두 개다. 두 번째 요청은 첫 요청의 메타데이터를 이어받지 않으므로 프로토콜 오류다.

입력:
```jsonl
{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"experimental":{"com.cresvoid/quote-preview":{}}}},"name":"cart_quote","arguments":{"lines":[{"productId":"p-100","quantity":1},{"productId":"p-300","quantity":1},{"productId":"p-100","quantity":2}]}}}
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
```

출력:
```jsonl
{"id":1,"jsonrpc":"2.0","result":{"content":[{"text":"total=54000\nitems=2","type":"text"}],"isError":false,"resultType":"complete"}}
{"error":{"code":-32602,"message":"Invalid params"},"id":2,"jsonrpc":"2.0"}
```

## 7. 검사 방식

[7.1] 테스트마다 새 프로세스를 실행하고 입력 줄을 모두 보낸 뒤 표준 입력을 닫는다. 제한 시간은 빌드 시간을 제외하고 테스트 하나당 15초다. 응답은 3.6의 정규 JSON 문자열로 비교한다. 엔진은 줄 끝의 공백·탭·CR과 출력 끝의 빈 줄만 제거하며 객체 키 순서를 바꿔 비교하지 않는다. 종료 코드가 0이 아니거나 출력이 64MiB를 넘으면 실패다. 네트워크와 외부 서비스는 쓰지 않는다.

[7.2] 고정 테스트 10~14개와 생성 테스트 2~3개로 메시지 구분, id 보존, 메타데이터 독립성, 입력 스키마, 도구 실패와 프로토콜 실패, 중복 상품과 문자열 이스케이프를 조합한다. 마지막 생성 테스트도 최대 2,000줄·카탈로그 5개·장바구니 20줄이라는 일반 상한 안에 둔다. 별도의 성능용 대규모 테스트는 없다. 이 단계의 목표는 대량 조회 최적화가 아니라 규약의 구별을 지키는 것이다.

[7.3] 별도 상호작용 검사에서는 표준 입력을 열어 둔 채 요청 한 줄을 보내고 10초 안에 응답 한 줄을 받아야 한다. 그 응답을 받은 다음에만 다음 요청을 보낸다. 알림을 섞어도 요청의 응답만 출력해야 한다. 요청마다 응답과 flush를 마쳐야 하며 EOF까지 입력을 모으는 구현은 실패한다. 이 검사는 뒤 단계의 참조 풀이와 학습자 구현에도 누적 적용한다.
