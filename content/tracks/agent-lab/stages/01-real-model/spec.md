# 실제 모델로 도구 루프 평가하기

[1] 각본 모델로 만든 도구 루프에 실제 모델을 연결해라. 실제 모델 평가의 입문 단계이며 대화 보존과 오류 환류의 뼈대는 제공한다. 먼저 시작 코드를 빌드하고 실험해라. 하네스 불변식을 만족하도록 구현한 뒤 같은 작업을 반복 실행해 성공률, 차례 수, 토큰과 비용을 관찰해라. 실제 모델은 같은 입력에도 다른 도구 호출과 답을 낼 수 있다. 특정 도구 선택 경로나 성공률을 정답으로 삼지 마라.

## 실행과 공개 HTTP 계약

[2] JDK 21과 제공된 Kotlin 도구만 사용해라. 프로젝트에서 `./build.sh`로 `build/app.jar`를 만들고 `PORT=18081 ./run.sh`로 서버를 실행해라. 서버는 `127.0.0.1`의 PORT에서 수신하며 `GET /health`에 HTTP 200과 빈 JSON 객체를 반환한다. 실행 전에 OPENCODE_API_KEY 환경 변수를 설정해라. 키가 없거나 공백이면 서버와 실험기 모두 변수 이름을 표준 오류로 알리고 종료 코드 2로 끝낸다. 키·인증 헤더·세션 식별자를 출력하거나 파일에 저장하지 마라.

[3] `POST /run`은 JSON 객체 하나를 받는다. 필수 필드는 `prompt` 문자열, `description` 문자열, `cache` 문자열, `turn_limit` 정수다. prompt 길이는 UTF-16 코드 단위로 1~2000이다. 보조 평면 문자 하나는 코드 단위 두 개로 센다. description은 brief 또는 detailed, cache는 cold 또는 stable, turn_limit은 1~8이다. 요청 본문은 UTF-8 기준 8192바이트 이하여야 한다. 형식이나 범위가 틀리면 모델을 호출하지 않고 HTTP 400을 반환한다. 지원하지 않는 메서드는 HTTP 405다. 클라이언트가 임의의 모델 주소나 키를 본문에 넣게 하지 마라.

[4] 정상 요청은 HTTP 200으로 `status`, `answer`, `turns`, `tool_calls`, `tool_errors`, `tool_executions`, `usage`를 반환한다. status는 도구 호출 없이 stop으로 끝나면 completed, 도구 호출 없이 다른 종료 사유로 끝나면 model_limit, 마지막 허용 차례에도 도구 호출이 있으면 turn_limit이다. answer는 도구 호출 없는 마지막 메시지의 content를 앞뒤 공백만 지운 문자열이며, 그런 메시지가 없으면 빈 문자열이다. turns는 받은 모델 응답 수, tool_calls는 받은 모든 도구 호출 수, tool_errors는 거절한 호출 수, tool_executions는 검증을 통과해 실행한 호출 수다. 도구를 쓰지 않는 최종 응답과 제한에 걸린 마지막 응답도 turns와 usage에 포함해라.

[5] usage는 `input_tokens`, `output_tokens`, `cache_read_tokens`, `allowance_usd`를 가진다. 대화의 모든 응답에서 각각 usage.prompt_tokens, usage.completion_tokens, usage.prompt_tokens_details.cached_tokens, usage.opencode.allowance를 더해라. cached_tokens가 생략되면 0으로 센다. 캐시 토큰은 입력 토큰의 부분집합이므로 input_tokens에서 빼거나 다시 더하지 마라. 토큰은 음이 아닌 정수이고 응답마다 cached_tokens는 prompt_tokens 이하여야 한다. allowance_usd는 음이 아닌 십진수로 합산해라. usage.cost가 0이어도 allowance는 사용될 수 있으므로 allowance를 비용 지표로 써라. 이는 Go 허용량의 달러 환산 소비량이며 별도의 현금 청구액을 뜻하지 않는다.

[6] 서버를 직접 실행했을 때 요청 예시는 다음과 같다. 성공 여부와 사용량 수치는 모델 응답에 따라 달라지며, 실패한 과제도 정상적으로 끝난 대화라면 HTTP 200이다.

```sh
curl -s http://127.0.0.1:18081/run -H 'Content-Type: application/json' \
  -d '{"prompt":"What is the stock quantity of tea?","description":"detailed","cache":"stable","turn_limit":3}'
```

## 모델 연결과 도구 루프

[7] 모델 주소는 `https://opencode.ai/inference/go/openai/v1/chat/completions`, model은 deepseek-v4.1-flash다. JDK HttpClient로 OpenAI 호환 JSON을 보내라. 모든 요청에 `Authorization: Bearer <환경 변수의 키>`, `User-Agent: cresvoid-agent-lab/1.0`, `x-opencode-session`을 보낸다. 세션 값은 대화마다 새로 만들고 한 대화의 모든 차례에서 유지해라. stream은 false, max_tokens는 512로 고정해라. 자동 재시도는 하지 마라. API나 통신 오류, 파싱할 수 없는 모델 응답은 본문을 노출하지 않는 HTTP 502로 처리한다. 실험기는 이를 실험 오류로 구분한다.

[8] 검증과 관찰은 같은 HTTP 모델 클라이언트와 도구 루프를 사용한다. LAB_MODEL_URL 환경 변수가 없으면 [7]의 실제 주소를 사용해라. 대역 주소는 http와 호스트 127.0.0.1 조합만 허용하며, 자격 정보가 들어간 URL은 거절해라. 실험기가 루프백 모델 게이트웨이를 띄우고 이 변수로 주소를 지정한다. 게이트웨이는 검증용 요청에 각본 응답을 돌려주고 관찰용 요청은 [7]의 실제 API로 전달한다. 검증용 prompt를 서비스의 특별 분기로 처리하지 마라.

[9] 도구는 재고 조회 함수 stock 하나다. tools의 function 정의에 이름과 설명, JSON Schema를 넣어라. 인자는 추가 필드 없는 객체 `{"sku":"tea"}` 또는 `{"sku":"coffee"}`뿐이다. sku는 대소문자를 구분하는 문자열이다. tea 수량은 7, coffee 수량은 11이다. 정상 결과는 `{"ok":true,"quantity":7}`처럼 반환한다. brief 설명은 짧은 조회 안내이고 detailed 설명은 허용하는 sku, 반환값, 읽기 전용 성질과 실패 후 인자 수정 방법을 설명해야 한다. 두 조건의 스키마와 도구 동작은 같아야 한다.

[10] 모든 도구 호출을 실행 전에 검증해라. 이름이 stock이 아니면 unknown_tool, JSON 문법 오류·객체가 아닌 인자·누락 필드·타입 오류·추가 필드·허용하지 않는 sku는 invalid_arguments로 거절해라. 거절한 호출은 도구를 실행하지 않는다. 결과는 `{"ok":false,"error":"invalid_arguments","hint":"Send only sku."}`처럼 오류 코드와 비어 있지 않은 수정 안내를 포함한다. 오류를 최종 답으로 처리하거나 대화를 중단하지 말고 도구 결과로 모델에 전달해 다음 차례에서 고칠 기회를 줘라.

[11] 모델 응답의 choices[0].message를 대화에 보존해라. tool_calls가 있으면 배열 순서대로 각각 한 번 처리하고, 각 호출 id를 tool_call_id로 갖는 role=tool 메시지에 결과 JSON을 문자열로 넣어라. 한 응답에 여러 도구 호출이 있어도 성공과 오류 결과를 모두 같은 순서로 추가한 다음 모델을 다시 호출해라. 앞선 메시지와 모델이 반환한 부가 필드를 유지해라. 한 응답의 도구 호출은 최대 16개다. 이 실험의 각본과 관찰 입력은 이 범위를 넘지 않는다.

[12] turn_limit은 최종 답의 유무와 관계없이 모델 응답 수의 상한이다. 허용한 마지막 응답의 도구 호출도 검증·실행·집계하되 다음 모델 요청은 보내지 마라. 대화 사이에 메시지·집계·세션을 공유하지 마라. 서버 전체 모델 요청은 검증용을 포함해 60회 이하이고 입력·출력 토큰 합계는 60000 이하, allowance 합계는 0.10달러 이하여야 한다. 다음 요청 전에 UTF-8 요청 본문의 바이트 수와 512의 합을 토큰 예약량으로 사용해 잔여 예산을 검사해라. 성공 응답의 실제 사용량으로 예약량을 정산하고, 통신 실패 시 예약량은 보수적으로 유지해라. 예산 부족은 새 호출 없이 HTTP 429로 반환해라. 외부 API가 예상을 넘는 사용량을 돌려주면 이후 호출을 중단하고 실험 오류로 끝내라.

[12.1] allowance는 요청마다 0.01달러를 예약해라. 이는 이 단계가 정한 호출당 허용 상한이며 제공자의 가격표나 사전 비용 보증이 아니다. 정산한 소비량과 미정산 예약의 합에 다음 예약량을 더했을 때 0.10달러를 넘으면 호출하지 않고 HTTP 429로 반환해라. 정확히 0.10달러인 예약은 허용한다. 요청 수·토큰·allowance의 검사와 예약은 동시에 들어온 요청에도 원자적으로 처리해라. 사용량을 읽은 성공 응답은 두 예약량을 실제 값으로 대체해라. 통신 실패·비정상 HTTP 응답·사용량 해석 실패에는 두 예약을 유지해라. 제공자가 호출당 allowance 0.01달러 또는 토큰 예약량을 넘겨 보고하거나 정수 합산이 넘치면 현재 요청은 HTTP 502로 반환하고 이후 모든 호출도 새 모델 요청 없이 HTTP 502로 차단해라. 제공자가 상한을 넘겨 소비한 비용을 되돌릴 수는 없다. 이 경우 사전 보장을 달성했다고 해석하지 말고 사후 초과를 감지한 실험 오류로 기록해라.

## 실험 부하와 합격 불변식

[13] 실험기는 빈 루프백 포트를 고르고 run.sh를 띄워 최대 30초 동안 준비를 확인한다. 공용 load.main.kts의 개방형 부하로 도착 시각을 정한다. 도구 루프 자체를 비교하는 실험이므로 일반 대화는 한 번에 하나씩 도착 시각 0에 보내고 완료 후 다음 대화를 시작한다. 경계값을 확인하는 두 요청만 같은 부하 묶음의 0ms와 20ms에 보낸다. 처리량이나 지연 시간을 합격 조건으로 삼지 않는다. 실험기는 종료와 오류 때 서버 및 게이트웨이를 정리한다.

[14] 먼저 각본 모델로 모르는 도구 뒤의 회복, JSON 문법 오류·누락 필드·타입 오류·추가 필드·허용하지 않는 sku에서의 회복, 한 응답 안의 성공·오류 혼합 호출, 최종 답 없는 8차례, 두 응답의 서로 다른 토큰·캐시·allowance, 즉시 최종 답, 0과 9의 차례 상한 거절을 확인한다. 합격하려면 도구 결과의 코드·안내·호출 연결·순서가 맞고, 거절한 호출의 실행 수가 0이며, 모든 응답의 사용량과 호출 수가 누락이나 중복 없이 합산되고, 대화별 세션과 차례 상한이 지켜져야 한다. 불변식 실패 수는 반드시 0이어야 한다. 이 사전 검증에 실패하면 비용을 쓰는 실제 관찰은 생략하며 관찰 지표는 0으로 출력한다. 이 경우 0인 성공률을 실제 모델의 성능으로 해석하지 마라.

[14.1] 예산 경계와 진단 형식은 별도로 띄운 서버와 각본 대역으로 확인한다. 예약이 상한과 같거나 잔액을 넘는 경우, 실제 사용량 정산, 실패 응답의 예약 유지, 제공자 초과 뒤 후속 호출 차단, 60회 요청 상한을 검사한다. 이 검사는 실제 API를 호출하지 않으며 각 서버의 예산은 독립적이다. 각본 응답의 가상 사용량은 관찰 지표에 포함하지 않는다.

[15] 사전 검증을 통과하면 tea, coffee, tea, coffee 순서로 같은 두 작업을 두 번씩 실제 모델에 요청한다. 각 대화의 turn_limit은 3이며, 실제 요청은 실험 한 번에 최대 12회다. 모든 대화의 시스템 지시는 stock을 사용해 재고를 구하고 최종 답으로 십진 정수만 반환하라는 것이다. 과제 성공은 status가 completed이고 answer가 tea는 7, coffee는 11과 정확히 같은 경우다. 설명과 답의 성공률이 낮아도 하네스 불변식이 맞으면 통과한다. 반대로 모델이 모든 답을 맞혀도 하네스 불변식이 틀리면 실패한다.

[16] morning-work는 brief와 cold, midday-work는 detailed와 cold, evening-work는 detailed와 stable을 비교한다. cold는 매 대화 시스템 메시지 맨 앞에 서로 다른 임의 nonce를 붙여 접두사 재사용을 방해하고, stable은 그 nonce 없이 같은 시스템·도구 접두사를 유지한다. 프롬프트 캐싱의 활성화·적중 여부는 제공자가 결정한다. 이 조건은 접두사 재사용 기회를 비교하며 캐시를 강제로 끄거나 적중을 보장하는 스위치가 아니다. Claude 전용 cache_control 필드를 이 API에 보내지 마라. 실제 cache_read_tokens가 늘지 않거나 성공률이 역전되는 결과도 관측값으로 남겨라.

[17] 출력 지표는 10개다. tea_success_rate와 coffee_success_rate는 각 작업의 2회 중 성공 비율이며 0~1이다. success_variance는 두 작업의 성공 비율을 각각 p라고 할 때 p(1-p)의 평균인 모집단 분산이다. mean_turns는 네 대화의 turns 평균이다. tool_calls는 네 대화의 전체 도구 호출 수다. input_tokens, output_tokens, cache_read_tokens, allowance_usd는 실제 네 대화의 usage 합계다. invariant_failures는 검증과 관찰에서 발견한 하네스 위반 수다. 각본 대역의 가상 사용량은 관찰 합계와 비용에 넣지 마라. 두 번의 표본만으로 설명 개선이나 캐싱의 인과 효과를 단정하지 마라.

[18] 모델 게이트웨이는 실제로 받은 요청 헤더·대화 기록과 실제로 반환한 usage를 독립적으로 모아 서비스 집계와 비교한다. 서비스가 출력한 성공률이나 자체 점검 결과만 믿지 않는다. 네 관찰 대화에서도 상한·사용량·도구 호출 수와 설명·접두사 조건을 확인한다. stdout은 유한한 숫자를 갖는 고유한 METRIC 행 10개뿐이며, 종료 코드 0은 불변식 통과, 1은 불변식 실패, 나머지는 실험 오류다. 실제 모델 거절이나 토큰 소진은 관측 가능한 정상 결과지만 인증·제공자·연결 장애는 실험 오류다.

[19] 세 조건의 지표를 나란히 적고 모델이 도구를 잘못 고른 경우와 하네스가 잘못 처리한 경우를 구분해라. 수정 범위는 프로젝트의 src/main/kotlin 안이다. 작은 JSON 읽기·쓰기 도구와 실행 템플릿을 재사용하고 새 의존성, 외부 부하 도구, 별도 서비스를 추가하지 마라. LAB_COST_LOG를 설정하면 실험기가 해당 파일에 외부 요청 수와 토큰·allowance만 JSON Lines로 덧붙인다. 제작과 검증에서는 이 기록으로 누적 비용을 관리하고 전체 3달러에 도달하기 전에 중단해라. 같은 비교를 필요 이상 반복하지 마라.

[19.1] 실패 원인을 살펴볼 때는 `LAB_DIAGNOSTICS=1 PORT=18081 ./run.sh`로 서버를 직접 실행하고 [6]의 요청을 한 번씩 보내라. HTTP 200 응답의 선택 필드 diagnostics는 차례별 turn, finish_reason, answer_format과 calls 배열을 제공한다. calls는 순서대로 name(stock 또는 unknown), sku(tea·coffee 또는 invalid), validation(accepted·unknown_tool·invalid_arguments)을 담는다. validation은 실제 실행 결과의 분류다. finish_reason은 stop·tool_calls·length 또는 other이며, answer_format은 도구 호출이 있으면 not_final, 없으면 canonical_integer·other_integer·non_integer·empty 중 하나다. canonical_integer는 0 또는 0으로 시작하지 않는 양의 십진 정수 문자열이고, other_integer는 나머지 부호·선행 0을 포함한 정수 문자열이다. 원문 도구 이름·인자·답·프롬프트·키·헤더·세션 식별자는 진단에 넣지 마라. 환경 변수가 없으면 diagnostics를 생략해라. 진단은 METRIC을 추가하지 않는다.

[19.2] tea 요청에 coffee가 accepted로 기록되면 모델의 sku 선택을 살펴봐라. invalid_arguments 뒤에 차례·예산 여유가 있는데도 다음 차례가 이어지지 않거나 거절한 호출의 실행 수가 늘었다면 [10]과 대조해 하네스를 확인해라. completed인데 answer_format이 non_integer이거나 answer가 정답과 다르면 모델의 최종 답을 확인해라. 진단은 원인 추적을 돕는 서비스 출력이며 독립 검증을 대신하지 않는다. 원래 조건과 같은 description·cache·turn_limit으로 재현하되 확률적인 모델이 이전 실행과 같은 경로를 택한다고 가정하지 마라. 직접 실행한 서버도 끝나면 종료해라.

## 근거

[20] [OpenCode Go 공식 문서](https://opencode.ai/docs/go/)의 OpenAI 호환 엔드포인트·모델 식별자와 opencode.allowance 설명을 따랐다. 이 단계의 사용량 경로는 실제 응답으로 확인했다. 도구 호출 메시지 형식은 [DeepSeek Function Calling](https://api-docs.deepseek.com/guides/tool_calls), 접두사 캐싱의 배경은 [DeepSeek Context Caching](https://api-docs.deepseek.com/guides/kv_cache)을 참고해라. DeepSeek 직접 API의 캐시 통계 이름을 OpenCode 응답에 그대로 적용하지 마라.

[21] [Claude 도구 호출 처리](https://platform.claude.com/docs/en/agents-and-tools/tool-use/handle-tool-calls)와 [프롬프트 캐싱](https://platform.claude.com/docs/en/build-with-claude/prompt-caching)은 오류를 모델에 되돌려 주는 원칙과 접두사 재사용의 배경이다. Claude의 tool_result나 cache_control 형식을 이 단계의 OpenAI 호환 요청에 섞지 마라. [How we built our multi-agent research system](https://www.anthropic.com/engineering/multi-agent-research-system)의 평가 절처럼 작은 작업 집합으로 평가를 시작하고, 특정 실행 경로의 재현보다 결과와 과정의 제약을 나눠 관찰해라.
