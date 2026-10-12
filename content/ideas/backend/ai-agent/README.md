# AI 에이전트

LLM 에 도구와 검색과 기억을 붙여 일을 시키는 시스템을 백엔드 개발자의 눈으로 익힌다. 에이전트 루프의 규약, 도구 설계, 컨텍스트 관리, 검색 증강(RAG), MCP, 평가와 보안을 다룬다. 모델을 쓰는 법보다 모델을 둘러싼 실행기를 바르게 만드는 힘을 기른다.

## 1. 개념 지도

### 1.1 에이전트와 워크플로

- 기본 단위는 증강 LLM 이다. 검색, 도구, 기억을 붙인 LLM 이 스스로 검색어를 만들고 도구를 고르고 남길 정보를 정한다.
- 워크플로는 LLM 과 도구를 미리 정한 코드 경로로 엮은 것이다. 에이전트는 LLM 이 과정과 도구 사용을 스스로 정한다.
- 가장 단순한 해법부터 찾고 필요할 때만 복잡도를 늘린다. 에이전트는 단계 수를 미리 알 수 없고 경로를 고정할 수 없는 열린 문제에 맞다.
- 워크플로 패턴 다섯: 프롬프트 연쇄(앞 호출의 출력을 다음 호출이 처리), 라우팅(입력을 분류해 전문 경로로 보냄), 병렬화(독립 하위 작업으로 나누기, 여러 번 시켜 투표하기), 오케스트레이터-워커(중앙 LLM 이 작업을 나눠 맡김), 평가자-최적화자(한 LLM 이 만들고 다른 LLM 이 평가를 되돌려 줌).

### 1.2 도구 사용 규약

Claude API 기준이다.

- 도구 정의는 이름(`^[a-zA-Z0-9_-]{1,128}$`), 설명, 입력 JSON Schema(`input_schema`)로 이뤄진다.
- 모델이 도구를 쓰려 하면 응답의 `stop_reason` 이 `tool_use` 이고 내용에 `tool_use` 블록(id, name, input)이 있다.
- 실행기는 다음 user 메시지에 `tool_result` 블록(tool_use_id, content, is_error)을 담아 보낸다. 도구 결과 메시지는 도구 사용 메시지 바로 다음에 와야 하고 content 배열에서 모든 tool_result 가 텍스트보다 앞에 와야 한다. 어기면 400 오류다.
- 도구 실행이 실패하거나 필수 인자가 빠졌으면 is_error 결과로 알려 준다. 모델은 고친 호출로 다시 시도한다. 오류 메시지에는 무엇이 잘못됐고 다음에 무엇을 하면 되는지 적는다.
- `tool_choice` 는 auto(도구가 있을 때 기본), any, tool, none 이다. 강제 사용을 지원하지 않는 모델과 설정이 있다.
- 도구 결과에는 웹 페이지나 메일처럼 통제할 수 없는 내용이 들어올 수 있다. 그 안의 지시로 모델을 조종하는 것이 간접 프롬프트 주입이다. 신뢰할 수 없는 내용은 tool_result 안에만 둔다.

### 1.3 도구 설계

- 도구 성능에 가장 큰 요인은 자세한 설명이다. 무엇을 하는지, 언제 쓰고 언제 쓰지 않는지, 인자마다의 뜻, 한계를 적는다.
- 관련 동작은 적은 수의 도구로 묶고 이름에 서비스 접두를 붙인다. 결과에는 다음 판단에 필요한 정보만 담는다.
- 에이전트-컴퓨터 인터페이스(ACI)에도 사람용 인터페이스만큼 공을 들인다. 실수하기 어렵게 인자를 바꾼다(예: 상대 경로 대신 절대 경로).

### 1.4 컨텍스트 관리

- 컨텍스트의 토큰이 늘수록 모델이 정보를 정확히 떠올리는 능력이 떨어진다(context rot). 주의는 한정된 예산이다.
- 긴 작업 기법: 컴팩션(한도에 가까워지면 대화를 요약해 다시 시작), 구조화된 노트(컨텍스트 밖에 노트를 남겼다가 다시 읽음), 하위 에이전트(맡긴 일을 처리하고 요약만 돌려줌).
- 가장 가벼운 컴팩션은 오래된 도구 결과 지우기다. 데이터를 미리 다 넣지 않고 식별자만 들고 있다가 필요할 때 도구로 불러온다.
- 프롬프트 캐싱은 접두사 단위로 동작한다. tools, system, messages 순서로 앞부분이 바뀌면 그 뒤가 모두 무효가 된다. 기본 수명은 5분이고 1시간을 고를 수 있다. 캐시 읽기는 기본 입력보다 훨씬 싸고 쓰기는 더 비싸다. 모델별 수치와 최소 길이는 문서로 확인한다.

### 1.5 다중 에이전트

- Anthropic 의 연구 시스템(2025-06-13 글)에서 에이전트는 채팅보다 약 4배, 다중 에이전트는 약 15배 토큰을 썼다. 내부 평가에서 단일 에이전트보다 90.2% 나았다. 토큰 사용량 하나가 성능 차이의 80% 를 설명했다.
- 모든 에이전트가 같은 컨텍스트를 공유해야 하거나 에이전트 사이 의존이 많은 일(대부분의 코딩)에는 맞지 않는다.
- 하위 에이전트에게 주는 지시가 모호하면 일을 겹쳐 하거나 빠뜨린다.
- 평가는 처음부터 작은 표본으로 시작한다. LLM 판정관이 루브릭으로 0.0~1.0 점수를 매기고 사람이 함께 검토한다.

### 1.6 검색 증강(RAG)

- 문서를 청크로 나누고 임베딩과 BM25 로 찾아 재순위화한 뒤 답에 쓴다.
- 여러 검색 결과는 RRF 로 합친다. 점수 = Σ 1/(k + 순위), k = 60(Cormack 외 2009). k 가 20~100 이면 성능이 거의 같다.
- 문맥 검색(2024-09-19 글): 청크마다 50~100토큰의 문맥 설명을 붙여 상위 20개 검색 실패율을 임베딩만으로 35%, BM25 를 더해 49%, 재순위화까지 67% 줄였다.
- 지식이 20만 토큰(약 500쪽)보다 작으면 RAG 없이 프롬프트에 통째로 넣는 쪽을 먼저 검토한다.
- 검색 품질은 recall@k, MRR, nDCG 로 잰다.

### 1.7 MCP

- MCP 는 LLM 앱(호스트)이 외부 데이터와 도구를 연결하는 개방 규약이다. JSON-RPC 2.0 메시지를 쓴다. 서버는 리소스, 프롬프트, 도구를 내놓는다.
- 2026-07-28 판은 상태 없는 규약이다. 요청마다 `_meta` 에 프로토콜 버전과 클라이언트 기능을 담고(필수, 빠지면 -32602) 서버는 이전 요청으로 맥락을 잡지 않는다. 결과에는 `resultType`(complete, input_required)이 있다.
- 요청 id 는 null 이면 안 된다. 알림에는 id 가 없고 받는 쪽은 응답하지 않는다. -32020~-32099 는 MCP 가 정의하는 오류 코드다(-32020 HeaderMismatch, -32021 MissingRequiredClientCapability, -32022 UnsupportedProtocolVersion).
- 보안 원칙: 사용자 동의와 통제, 데이터 보호, 도구는 임의 코드 실행으로 다룬다. 믿을 수 있는 서버가 아니면 도구 설명과 주석도 믿지 않는다.
- 규약은 판마다 크게 바뀐다. 카드를 만들 때 판을 하나 고정하고 그 판의 schema.ts 를 근거로 쓴다.

## 2. 학습 경로

1. R-ai-agent-10 기술 지문으로 에이전트 루프, 도구 규약, 컨텍스트 관리를 익힌다.
2. B-ai-agent-20 `agent-runtime` 트랙에서 각본 모델로 에이전트 실행기를 만든다. 실제 모델 없이 결정적으로 채점한다.
3. B-ai-agent-30 `rag` 트랙에서 청크, 검색, 결합, 평가를 만든다.
4. B-ai-agent-40 `mcp-server` 트랙에서 MCP 서버를 표준 입출력으로 만든다.
5. L-ai-agent-50 실험은 실제 모델 API 를 쓴다. API 키와 비용을 정한 뒤에 한다.

## 3. 환경

- R-ai-agent-10, B-ai-agent-20, B-ai-agent-30, B-ai-agent-40 은 추가 도구가 필요 없다. 모델 응답은 테스트 입력에 들어 있는 각본으로 대신한다.
- L-ai-agent-50(`env: llm-api`)는 실제 모델 API 키와 호출 비용이 든다. 사용자가 정한다.

## 4. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| 벡터 색인(HNSW) | build | 계층 그래프 탐색, 후보 수와 재현율. 결정적이도록 층 배정을 입력으로 준다 |
| 평가 하네스 | build | 골든 집합, 판정 규칙, 비결정성을 다루는 반복 실행과 통계 |
| 프롬프트 주입 방어 | code | 권한 정책과 신뢰 경계 코드를 읽고 우회 경로를 찾는다 |
| 비용과 지연 계산 | reading | 캐시 적중률, 토큰 수, 병렬 하위 에이전트의 비용 |

## 5. 출처

- Schluntz, Zhang, "Building effective agents", Anthropic, 2024-12-19: https://www.anthropic.com/engineering/building-effective-agents
- "Effective context engineering for AI agents", Anthropic, 2025-09-29: https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- "How we built our multi-agent research system", Anthropic, 2025-06-13: https://www.anthropic.com/engineering/multi-agent-research-system
- "Introducing Contextual Retrieval", Anthropic, 2024-09-19: https://www.anthropic.com/news/contextual-retrieval
- Claude 문서, Define tools: https://platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools
- Claude 문서, Handle tool calls: https://platform.claude.com/docs/en/agents-and-tools/tool-use/handle-tool-calls
- Claude 문서, Prompt caching: https://platform.claude.com/docs/en/build-with-claude/prompt-caching
- MCP Specification(2026-07-28): https://modelcontextprotocol.io/specification/2026-07-28
- Cormack, Clarke, Büttcher, "Reciprocal rank fusion outperforms Condorcet and individual rank learning methods", SIGIR 2009.
- Liu 외, "Lost in the Middle: How Language Models Use Long Contexts", TACL 2024.
- OWASP Top 10 for LLM Applications(LLM01 Prompt Injection).
