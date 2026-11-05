# 02. 리소스·프롬프트와 페이지 나누기

## 1. 개요

[1.1] 1단계 서버에 `resources/list`, `resources/read`, `prompts/list`, `prompts/get`을 더한다. 상품을 리소스로 읽고 그 내용을 프롬프트에 담아 전달한다. 리소스와 프롬프트의 목록은 접두어로 걸러 요청한 크기의 페이지로 나눈다. 커서에서 목록 조건을 복원하고 조건 불일치를 검증한다. 목표 시간은 2~3시간이다. 1단계의 도구, 메시지 분류, 메타데이터, 출력 정규화, 입출력 상한과 오류 형식을 그대로 적용한다. `tools/list`는 계속 도구 두 개를 한 페이지에 반환한다.

[1.2] 근거는 MCP `2026-07-28` 판의 [리소스](https://modelcontextprotocol.io/specification/2026-07-28/server/resources), [프롬프트](https://modelcontextprotocol.io/specification/2026-07-28/server/prompts), [페이지 나누기](https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/pagination)다. [고정한 schema.ts](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/271ecc9accafdd9b83a3c869fa67c22953b2af80/schema/2026-07-28/schema.ts)의 `PaginatedRequestParams`, `PaginatedResult`, `CacheableResult`, `Resource`, `TextResourceContents`, `Prompt`, `PromptArgument`, `PromptMessage`, `EmbeddedResource`를 따른다. 리소스 URI와 커서의 문자열 형식, 목록 인자 `pageSize`와 `namePrefix`는 과제 전용 계약이며 MCP 표준 목록 인자가 아니다.

## 2. 배경 개념

[2.1] 리소스는 서버가 제공하는 주소 있는 데이터다. 목록에서 받은 URI를 읽기 요청에 넣어 내용을 얻는다. 목록의 리소스 설명과 읽기 결과의 내용은 다른 객체다. 설명에는 식별자와 형식 정보가 있고 읽기 결과에는 실제 텍스트가 있다. 같은 URI라도 문자열을 정규화하거나 다른 주소로 추측해 연결할 수 있다는 뜻은 아니다.

[2.2] 프롬프트는 사용자가 선택해 쓸 수 있는 메시지 틀이다. `prompts/list`의 인자 설명은 JSON Schema가 아니라 `name`과 `required` 같은 필드를 가진 `PromptArgument`다. `prompts/get`은 인자를 적용한 메시지 배열을 반환한다. 메시지 하나의 `content`는 도구 결과의 content 배열과 달리 콘텐츠 객체 하나다. 텍스트 메시지와 리소스를 내장한 메시지를 같은 배열 안에 둘 수 있다.

[2.3] 내장 리소스는 URI만 가리키는 링크와 다르다. `type:"resource"`인 콘텐츠의 `resource` 안에 `uri`, `mimeType`, `text`가 함께 들어가므로 클라이언트가 같은 데이터를 다시 읽지 않아도 된다. 이 과제의 내장 리소스와 리소스 읽기는 동일한 상품을 동일한 텍스트로 보여 준다.

[2.4] 페이지 커서는 목록의 다음 위치를 나타내는 불투명한 문자열이다. 클라이언트는 응답의 `nextCursor`를 그대로 다음 요청의 `cursor`에 넣는다. 마지막 페이지에는 nextCursor 자체가 없다. cursor가 없다는 것과 빈 문자열이라는 것은 다르다. 서버는 이전 목록 요청을 기억하지 않고 커서와 메서드만으로 페이지를 정한다. 다른 종류의 목록에서 얻은 커서는 사용할 수 없다.

## 3. 입출력 규칙

[3.1] 새 메서드의 요청도 1단계와 같은 `params._meta`를 필수로 가진다. 리소스와 프롬프트는 추가 클라이언트 기능을 요구하지 않으므로 `io.modelcontextprotocol/clientCapabilities:{}`로 호출할 수 있다. 문자열 id, 알림과 응답 무시, 한 줄 출력, 재귀적인 객체 키 정렬 규칙도 같다. 상품과 목록은 실행 중 바뀌지 않는다.

[3.2] 새 목록 결과는 `resultType:"complete"`, `ttlMs:0`, `cacheScope:"public"`, 해당 목록 배열을 가진다. 뒤에 페이지가 있을 때만 문자열 `nextCursor`를 더한다. 리소스 읽기 결과도 ttlMs와 cacheScope를 가진다. `prompts/get` 결과에는 두 캐시 필드가 없으며 4절의 필드만 넣는다. 선택적인 `_meta`, `title`, `description` 등을 명시된 곳 외에 추가하지 않는다.

## 4. 명령 규칙

[4.1] 리소스는 상품마다 하나씩 총 다섯 개다. URI는 `shop://products/` 뒤에 상품 id를 붙인 값이며 예를 들어 `shop://products/p-100`이다. 리소스 설명 객체에는 `uri`, `name`, `mimeType`만 넣는다. name은 상품 id, mimeType은 `text/plain`이다. 목록 순서는 `p-100`, `p-200`, `p-300`, `p-400`, `p-500`이다. 목록에 없는 URI와 이 URI의 다른 표기는 같은 리소스로 취급하지 않는다. 대소문자 변경, 끝의 `/`, 쿼리, 퍼센트 인코딩을 통한 대체 표기도 모두 모르는 URI다.

[4.2] `resources/list`의 기본 페이지 크기는 2다. 목록 조건을 생략하고 cursor가 없으면 p-100과 p-200을 반환하고 nextCursor는 `resources:2`다. cursor가 `resources:2`면 p-300과 p-400을 반환하고 nextCursor는 `resources:4`다. cursor가 `resources:4`면 p-500만 반환하고 nextCursor는 생략한다. 다른 목록 조건과 커서는 4.11~4.13을 따른다. 첫 페이지를 먼저 읽지 않고 커서를 보내거나 같은 커서를 여러 번 보내도 같은 페이지를 반환한다.

[4.3] `resources/read`의 `params.uri`는 필수 문자열이다. 존재하는 URI이면 `resultType:"complete"`, `ttlMs:0`, `cacheScope:"public"`, `contents`를 반환한다. contents는 객체 하나의 배열이며 그 객체의 필드는 `uri`, `mimeType:"text/plain"`, `text`뿐이다. text는 해당 상품에 대한 1단계 product_get의 성공 텍스트와 같다. 재고 0인 상품도 읽을 수 있다. URI가 없거나 타입이 틀리거나 존재하지 않으면 `-32602`다. 과거 MCP 판의 리소스 부재 코드 `-32002`는 쓰지 않는다.

[4.4] 프롬프트는 `compare_products`, `inspect_product`, `summarize_catalog` 순서로 세 개다. description은 각각 `두 상품의 차이를 비교한다.`, `상품에 대한 질문을 작성한다.`, `카탈로그 요약을 요청한다.`다. 목록의 프롬프트 객체에는 `name`, `description`, `arguments`만 넣는다. arguments는 인자 설명 객체의 배열이며 인자 설명의 필드는 `name`과 불리언 `required`뿐이다.

[4.5] compare_products의 인자 설명은 `leftId` required true, `rightId` required true 순서다. inspect_product는 `productId` required true, `question` required false 순서다. summarize_catalog의 인자 설명은 빈 배열이다. `prompts/list`의 기본 페이지 크기는 2다. 목록 조건을 생략하고 cursor가 없으면 앞의 두 프롬프트와 nextCursor `prompts:2`를 반환한다. cursor가 `prompts:2`면 summarize_catalog만 반환하고 nextCursor는 생략한다. 다른 목록 조건과 커서는 4.11~4.13을 따른다. 리소스 목록의 커서는 프롬프트 목록에서, 프롬프트 목록의 커서는 리소스 목록에서 사용할 수 없다.

[4.6] `prompts/get`의 `params.name`은 필수 문자열이며 세 이름 중 하나여야 한다. `params.arguments`는 생략할 수 있으며 생략하면 빈 객체와 같다. 제공하면 객체여야 하고 모든 값이 문자열이어야 한다. 해당 프롬프트의 필수 인자는 모두 있어야 하며 선언하지 않은 인자는 허용하지 않는다. null은 생략으로 취급하지 않는다. 상품 id 인자는 카탈로그에 실제로 존재해야 한다. 빈 상품 id나 모르는 상품도 `-32602`다. leftId와 rightId가 같아도 유효하며 question은 빈 문자열, 줄바꿈과 큰따옴표를 포함한 문자열도 허용한다.

[4.7] 모든 prompts/get 성공 결과에는 `resultType:"complete"`, 해당 프롬프트의 `description`, `messages`만 넣는다. 모든 메시지의 role은 `user`다. 텍스트 메시지의 content는 `type:"text"`와 `text`만 가진 객체다. 상품을 내장하는 메시지의 content는 `type:"resource"`와 `resource`만 가진 객체이며 resource는 4.3의 contents 안에 들어갈 객체와 같다. 내장 리소스 안에 ttlMs, cacheScope, resultType은 넣지 않는다.

[4.8] compare_products의 messages는 세 개다. 첫 메시지는 텍스트 `두 상품을 비교하라.`이고 두 번째는 leftId의 내장 리소스, 세 번째는 rightId의 내장 리소스다. 두 id가 같으면 같은 리소스를 두 번 넣는다. 메시지 순서를 상품 id 순서로 바꾸지 않는다.

[4.9] inspect_product의 messages는 두 개다. 첫 메시지의 텍스트는 question이 제공됐으면 그 값 그대로이고 생략됐으면 `구매 전에 확인할 점을 설명하라.`다. 두 번째 메시지는 productId의 내장 리소스다. question이 빈 문자열이면 기본값으로 바꾸지 않는다. 상품 이름이나 question의 줄바꿈은 출력 JSON 문자열 안에서 이스케이프한다.

[4.10] summarize_catalog는 인자를 받지 않는다. messages는 텍스트 메시지 하나이며 텍스트는 `카탈로그를 요약하라.`, `products=5`, `available=4`, `stock=36`을 LF로 이은 문자열이다. 마지막 LF는 없다. products는 상품 수, available은 재고가 양수인 상품 수, stock은 전체 재고 합계다. 앞 요청에서 견적을 냈어도 이 값은 바뀌지 않는다.

[4.11] `resources/list`와 `prompts/list`의 params에서 선택 인자 `pageSize`와 `namePrefix`를 받는다. pageSize는 표기에 관계없이 수학적으로 정수인 JSON 수이며 1~5여야 한다. namePrefix는 길이 0~32인 문자열이며 각 문자는 영문 소문자, 숫자, `_`, `-` 중 하나여야 한다. null을 비롯한 타입·범위 위반은 `-32602`다. cursor가 없으면 생략한 pageSize는 2, 생략한 namePrefix는 빈 문자열이다. 목록의 name이 namePrefix로 시작하는 항목만 기존 순서를 유지해 고른 다음 pageSize개씩 나눈다. 일치하는 항목이 없으면 빈 배열을 반환하고 nextCursor는 생략한다. 도구 목록과 다른 메서드는 두 인자를 무시한다.

[4.12] cursor는 목록 종류, 페이지 크기, 접두어, 걸러진 목록에서 시작할 위치를 결정한다. 기본 조건인 pageSize 2와 빈 접두어에서는 `<종류>:<위치>` 형식을 쓴다. 그 밖의 조건에서는 `<종류>:<위치>:<크기>:<접두어>` 형식을 쓰며 빈 접두어일 때도 마지막 콜론을 남긴다. 종류는 resources 또는 prompts다. 위치와 크기는 선행 0이나 부호가 없는 십진수다. 위치는 0보다 크고 pageSize의 배수이며 걸러진 목록의 항목 수보다 작아야 한다. 기본 조건을 네 부분 형식으로 쓴 커서는 허용하지 않는다. 문자열이 아닌 cursor, 빈 문자열, 형식·조건·위치가 유효하지 않은 cursor는 모두 `-32602`다.

[4.13] cursor가 있으면 생략한 pageSize와 namePrefix는 커서에서 복원한다. 함께 제공한 조건은 커서의 조건과 정확히 같아야 하며 다르면 `-32602`다. 커서를 이전 요청의 조건과 연결하지 않는다. 다음 페이지가 있을 때만 현재 위치에 pageSize를 더한 위치로 nextCursor를 만들며 마지막 페이지에는 생략한다. 예를 들어 pageSize 3, namePrefix `p-`인 첫 리소스 목록은 p-100~p-300과 `resources:3:3:p-`를 반환한다. 이 커서만으로 다음 요청을 보내면 p-400과 p-500을 반환하며 nextCursor는 없다. 정확히 한 페이지 분량인 필터 결과에도 nextCursor를 넣지 않는다.

## 5. 오류 규칙

[5.1] 새 명령의 필수 인자 누락, 타입 오류, 모르는 이름·상품·URI, 모르는 cursor, 다른 목록의 cursor, 선언하지 않은 프롬프트 인자는 모두 코드 `-32602`, message `Invalid params`인 프로토콜 오류다. data는 생략한다. 여기에는 도구의 isError에 해당하는 실행 오류 결과가 없다. 알림은 이 오류 조건에 해당하는 인자를 담고 있어도 응답하지 않는다.

[5.2] 1단계의 도구 오류 규칙은 그대로다. product_get의 모르는 상품은 isError true인 도구 결과이지만 resources/read와 prompts/get의 모르는 상품은 프로토콜 오류다. 같은 문자열을 입력했더라도 요청한 메서드의 계약에 따라 판단한다. 이 단계에서도 요청 외형, 버전과 클라이언트 기능의 추가 오류 범위는 3단계에 맡긴다.

## 6. 예시

[6.1] 리소스 두 번째 페이지를 바로 요청할 수 있다. p-300을 읽으면 상품 이름의 LF까지 text 안에 들어가며 출력은 여전히 한 줄이다.

입력:
```jsonl
{"jsonrpc":"2.0","id":1,"method":"resources/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"cursor":"resources:2"}}
{"jsonrpc":"2.0","id":"read","method":"resources/read","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"uri":"shop://products/p-300"}}
```

출력:
```jsonl
{"id":1,"jsonrpc":"2.0","result":{"cacheScope":"public","nextCursor":"resources:4","resources":[{"mimeType":"text/plain","name":"p-300","uri":"shop://products/p-300"},{"mimeType":"text/plain","name":"p-400","uri":"shop://products/p-400"}],"resultType":"complete","ttlMs":0}}
{"id":"read","jsonrpc":"2.0","result":{"cacheScope":"public","contents":[{"mimeType":"text/plain","text":"id=p-300\nname=원두\n중배전\nprice=18000\nstock=5","uri":"shop://products/p-300"}],"resultType":"complete","ttlMs":0}}
```

[6.2] question을 빈 문자열로 제공하면 빈 텍스트 메시지를 유지한다. 다음 요청은 리소스 목록에 프롬프트의 커서를 보내므로 오류다.

입력:
```jsonl
{"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"name":"inspect_product","arguments":{"productId":"p-200","question":""}}}
{"jsonrpc":"2.0","id":3,"method":"resources/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"cursor":"prompts:2"}}
```

출력:
```jsonl
{"id":2,"jsonrpc":"2.0","result":{"description":"상품에 대한 질문을 작성한다.","messages":[{"content":{"text":"","type":"text"},"role":"user"},{"content":{"resource":{"mimeType":"text/plain","text":"id=p-200\nname=드립 필터\nprice=4500\nstock=0","uri":"shop://products/p-200"},"type":"resource"},"role":"user"}],"resultType":"complete"}}
{"error":{"code":-32602,"message":"Invalid params"},"id":3,"jsonrpc":"2.0"}
```

## 7. 검사 방식

[7.1] 1단계와 같은 줄 단위 표준 입출력과 정규 JSON 문자열 비교를 사용한다. 테스트 하나당 제한 시간은 빌드를 제외하고 15초다. 테스트마다 새 프로세스를 쓰고 EOF 뒤 종료 코드 0을 요구한다. 고정 테스트 10~14개와 생성 테스트 2~3개로 목록의 처음·중간·끝, 독립적인 커서 재사용, 리소스 내용, 프롬프트의 인자와 메시지 순서, 메서드별 오류 차이를 검사한다. 1단계 테스트도 누적 실행한다.

[7.2] 최대 규모는 테스트 하나당 2,000줄, 상품 리소스 5개와 프롬프트 3개다. 별도 성능용 대규모 테스트는 없다. 목록은 작지만 페이지 크기·접두어 조합, 커서만으로 조건 복원, 조건 불일치, 빈 결과와 마지막 페이지, 프롬프트에 포함되는 리소스와 문자열 이스케이프를 섞어 검사한다. 출력 상한은 엔진의 64MiB다.
