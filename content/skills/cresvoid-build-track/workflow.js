export const meta = {
  name: 'cresvoid-build-track',
  description: 'cresvoid build 트랙(구현) 단계를 만든다. 스펙 작성, 모호성 검토, Kotlin 참조 풀이와 생성기, 독립 테스트와 문항, 교차 검증, 학습자 검토를 거친다',
  whenToUse: 'cresvoid build 트랙을 새로 만들거나 기존 트랙에 단계를 더할 때. args 에 root, track, brief, stages, existing 을 넘긴다.',
  phases: [
    { title: '스펙', detail: '스펙 작성과 두 번의 모호성 검토' },
    { title: '풀이', detail: 'Kotlin 참조 풀이와 생성기' },
    { title: '테스트와 문항', detail: '참조 풀이와 독립적인 테스트, 확인 문항' },
    { title: '검증', detail: '교차 검증, 학습자 검토, 마지막 수정' },
  ],
}

const ROOT = args.root
const TRACK = args.track
const TRACK_DIR = ROOT + '/content/tracks/' + TRACK
const NEW = args.stages.map(st => st.dir)
const EXISTING = args.existing || []
const LANES = args.lanes || 2
if (args.libs && args.libs !== 'kotlinx-coroutines') throw new Error('libs 는 kotlinx-coroutines 만 받는다: ' + args.libs)
const LIBS = args.libs ? `[의존성]
- 이 단계의 Kotlin 프로젝트는 kotlinx.coroutines 1.11.0 을 쓴다(FORMAT.md 7절, ${ROOT}/docs/decisions.md D21).
- Kotlin 프로젝트를 만들 때 빈 lib/ 디렉터리를 만든 뒤 kotlin ${ROOT}/content/templates/sync.main.kts 를 실행한다. sync 가 kotlinx-coroutines-core 와 kotlinx-coroutines-test jar 를 lib/ 에 넣는다. lib/ 의 jar 는 직접 넣거나 고치지 않는다.
- 학습자에게 실험 표시(@ExperimentalCoroutinesApi, @FlowPreview, @DelicateCoroutinesApi)가 붙은 API 를 요구하지 않는다. 가상 시각을 읽는 currentTime 처럼 실행 틀에 꼭 필요한 곳만 @OptIn 으로 한 곳에 둔다.
- runTest 의 기본 실제 시간 한도(1분)는 표준 입력을 읽는 동안에도 흐른다. 실행 틀은 runTest(timeout = Duration.INFINITE) 를 쓰거나 입력을 runTest 밖에서 모두 읽는다.` : ''
const NEW_LIST = NEW.map(dir => TRACK_DIR + '/stages/' + dir).join(', ')

const COMMON = [
  '이 작업은 사용자의 개인 학습 프로젝트 ' + ROOT + ' 에서 진행한다. 이 프로젝트의 규칙은 ' + ROOT + '/AGENTS.md 와 ' + ROOT + '/docs/agents.md 에 있다.',
  '프로젝트 밖 기록 시스템은 쓰지 않는다. git 커밋을 하지 않는다. 결과는 최종 출력으로 돌려준다.',
  '작업 전에 ' + ROOT + '/content/FORMAT.md 를 처음부터 끝까지 읽는다. 엔진과 컨텐츠의 계약이다.',
  '프로젝트에 남기는 코드는 모두 Kotlin 이다. /tmp 에서 잠깐 쓰는 검사 도구는 언어를 가리지 않는다.',
  'Kotlin 코드를 쓰기 전에 세션의 코드 규칙과 설치된 kotlin-core-best-practices 스킬을 읽고 따른다. 주석은 FORMAT.md 11.8 절을 따른다.',
  '이 프롬프트가 지정한 파일만 만들거나 고친다. track.json 은 고치지 않는다.',
  '학습자 공간과 진행 기록(' + ROOT + '/practice)을 바꾸지 않는다. cresvoid validate 는 상태를 바꾸지 않는다. 다른 cresvoid 명령은 프로젝트를 /tmp 에 복사해 그 안에서 실행한다.',
].join('\n')

const WRITING = [
  '한국어 문서 본문은 해라체 평서형으로 쓴다.',
  '한국어 초안을 완성한 뒤 설치된 korean-writing-guide 스킬의 SKILL.md 를 읽고 정확히 한 번 적용한다. 문장의 명확성도 같은 스킬로 확인한다.',
].join('\n')

const LEARNER = [
  '학습자는 Kotlin 백엔드 경력이 있는 개발자다.',
  '학습자는 AI 없이 스펙을 읽고 설계해 끝까지 구현하는 감각과 긴 한국어 문서를 정확히 읽는 힘을 되찾으려 한다. 쉬운 과제를 원하지 않는다.',
].join('\n')

const CONTEXT = `[트랙]
트랙: ${TRACK} (${TRACK_DIR})
이미 있는 단계: ${EXISTING.length > 0 ? EXISTING.join(', ') : '없음'}
이번에 만들 단계: ${NEW.join(', ')}
${EXISTING.length > 0 ? '이미 있는 단계의 spec.md 를 먼저 읽는다. 새 단계는 그 위에 쌓인다. 새 단계 규칙이 이미 있는 단계 명령의 출력을 바꾸면 안 된다.' : '새 트랙이다.'}

[설계 요약]
${args.brief}
${LIBS}`

const EXISTING_SOLUTIONS = EXISTING.length > 0 ? '이미 있는 단계의 Kotlin 참조 풀이(hidden/solution/)도 먼저 읽는다.' : ''

const ARCHITECT = `${COMMON}
${WRITING}
${LEARNER}
${CONTEXT}
${EXISTING_SOLUTIONS}

너는 이 트랙의 스펙 작성자다. 단계마다 숙련 개발자가 1~3시간 걸리는 난도로 쓴다.
만들 파일: 새 단계마다 ${TRACK_DIR}/stages/<단계>/spec.md.${EXISTING.length === 0 ? ' 그리고 ' + TRACK_DIR + '/starters/kotlin/ (FORMAT.md 7절 모양이고 8절 기준을 따른다. main 은 TODO() 로 비운다. src/main/kotlin 밖의 파일은 kotlin ' + ROOT + '/content/templates/sync.main.kts 로 복사한다).' : ''}
설계 요약에 빠진 세부 동작은 네가 정한다. 가장 단순하고 검사 가능한 규칙을 고른다. 정한 내용은 모두 스펙의 번호 문단에 적고 최종 출력 decisions 에도 적는다.
스펙 구성은 FORMAT.md 11.1 절을 따른다. 배경 개념 절은 그 개념을 처음 구현하는 사람이 이해할 만큼 설명하되 구현 방법은 지시하지 않는다.
검사 방식 절에는 입력 방식, 출력 비교 방식, 제한 시간, 대규모 테스트 규모를 적는다.
번호 문단은 "2.3" 처럼 절 번호와 문단 번호를 점으로 잇는다.
예시는 입력과 출력을 함께 보여 주는 코드 블록으로 쓰고 출력은 직접 계산해서 확인한다.`

function hunterPrompt(round) {
  return `${COMMON}
${CONTEXT}

너는 스펙만 보고 구현하는 개발자다. 파일은 고치지 않는다.
새 단계의 spec.md(${NEW_LIST})와 이미 있는 단계의 spec.md 를 읽는다. FORMAT.md 와 spec.md 말고는 열지 않는다. 단계의 hidden/ 디렉터리는 열지 않는다.
두 사람이 스펙대로 각자 구현했을 때 출력이 달라질 수 있는 지점을 모두 찾는다. 지점마다 차이를 드러내는 입력 예와 갈리는 해석을 적는다.
단계 사이의 모순도 찾는다. 뒤 단계 규칙이 앞 단계 명령의 출력을 바꾸면 모순이다.
예시 출력이 규칙과 어긋나는지 직접 계산해 확인한다.
출력에 영향이 있으면 severity 는 must, 출력에는 영향이 없지만 읽는 사람을 헷갈리게 하면 should 다.
${round > 1 ? '이번은 두 번째 검토다. 첫 번째 검토의 지적은 이미 반영되었다. 남은 문제만 적는다.' : ''}`
}

function fixerPrompt(issues) {
  return `${COMMON}
${WRITING}
${CONTEXT}

너는 스펙 수정자다. 대상은 새 단계의 spec.md 다(${NEW_LIST}).
아래 지적을 하나씩 검토한다. 타당한 지적은 번호 문단에 규칙을 보태거나 문장을 고쳐 해석을 하나로 고정한다. 가장 단순하고 검사 가능한 쪽을 고른다.
뒤 단계 규칙이 앞 단계 명령의 출력을 바꾸지 않게 한다. 이미 있는 단계의 spec.md 를 고쳐야 하면 고치지 말고 rejected 에 이유와 함께 적는다.
타당하지 않은 지적은 rejected 에 이유와 함께 적는다. 예시를 고쳤으면 출력을 다시 계산해 확인한다.

지적 목록(JSON):
${JSON.stringify(issues, null, 2)}`
}

const REFERENCE = `${COMMON}
${CONTEXT}
${EXISTING_SOLUTIONS}

너는 참조 풀이 작성자다. 새 단계의 spec.md 를 읽는다.
만들 파일: 새 단계마다 ${TRACK_DIR}/stages/<단계>/hidden/solution/ (FORMAT.md 7절 Kotlin 프로젝트. src/main/kotlin 밖의 파일은 kotlin ${ROOT}/content/templates/sync.main.kts 로 복사한다)와 ${TRACK_DIR}/stages/<단계>/hidden/gen.main.kts.

참조 풀이 기준:
1. 단계마다 그 단계까지의 규칙을 모두 구현한 완성본이다. ${EXISTING.length > 0 ? '마지막 기존 단계(' + EXISTING[EXISTING.length - 1] + ')의 Kotlin 풀이 구조와 이름을 이어받는다.' : ''}
2. 학습자가 cresvoid solution 으로 읽는 모범 코드다. 책임별로 파일을 나누고 처리 단계가 위에서 아래로 읽히게 쓴다. 갈래가 정해진 구조는 sealed 타입으로 표현한다.
3. 스펙 문장을 글자 그대로 구현한다. 스펙이 두 가지로 읽히는 곳은 그 문단에 규칙을 보태 한 가지로 고정하고 spec_changes 에 적는다.
4. 대규모 테스트를 제한 시간의 1/5 안에 통과한다.

생성기 기준:
1. FORMAT.md 6절을 따른다. 규모를 인자로 받는다.
2. 그 단계의 모든 명령과 경계 조건과 오류 입력을 섞는다. 단어나 값은 흔한 것과 드문 것이 함께 나오게 뽑는다.
3. 앞 단계 검사 입력에는 뒤 단계에서 뜻이 바뀌는 입력을 넣지 않는다.
4. 대규모 입력의 출력은 수 MB 이하로 유지한다.

확인 절차:
1. 스펙의 예시를 모두 풀이에 넣어 출력이 예시와 같은지 확인한다.
2. 각 생성기를 seed 1, 2, 3 으로 실행해 풀이가 정상 종료하는지, 같은 입력에 같은 출력을 내는지 확인한다.
3. 대규모 입력에서 풀이 실행 시간을 잰다.
4. 뒤 단계 풀이에 앞 단계 생성기 입력을 넣고 앞 단계 풀이와 출력이 같은지 확인한다.
tests.json 과 quiz.json 은 만들지 않는다.`

function testsPrompt(reference) {
  return `${COMMON}
${CONTEXT}

너는 테스트 작성자다. 새 단계의 spec.md 를 읽는다.
hidden/solution/ 디렉터리는 열지 않는다. 기대값을 참조 풀이와 독립적으로 만드는 것이 이 작업의 목적이다. hidden/gen.main.kts 는 인자를 알기 위해 읽어도 된다.
새 단계마다 ${TRACK_DIR}/stages/<단계>/hidden/tests.json 을 만든다.
1. FORMAT.md 5절과 11.2 절을 따른다.
2. 스펙의 번호 문단 가운데 출력으로 확인할 수 있는 규칙은 모두 한 번 이상 다룬다.
3. 고정 테스트의 expected 는 스펙만 보고 직접 유도한다. 계산이 필요하면 /tmp 에서 도구를 계산기로 쓴다.
4. refs 는 테스트가 확인하는 규칙의 문단 번호이고 quote 는 refs 문단의 핵심 문장을 글자 그대로 옮긴다.
5. 마지막 테스트는 대규모 테스트다. gen 인자와 timeout_sec 은 스펙 검사 방식 절의 값을 쓴다. 생성기 테스트의 repeat 는 2 또는 3 이다.
6. 테스트 이름에는 숨은 케이스의 조건이나 답을 담지 않는다. 엔진은 통과하거나 실패한 테스트 이름을 학습자에게 보여 준다.

참조 풀이 작성자가 남긴 생성기 정보(JSON):
${JSON.stringify(reference ? reference.generators : [], null, 2)}`
}

const QUIZ = `${COMMON}
${WRITING}
${CONTEXT}

너는 스펙 확인 문항 작성자다. 새 단계의 spec.md 를 읽는다. hidden/solution/ 과 hidden/tests.json 은 열지 않는다.
새 단계마다 ${TRACK_DIR}/stages/<단계>/hidden/quiz.json 을 만든다. 문항의 목적은 학습자가 구현 전에 스펙을 꼼꼼히 읽었는지 확인하는 것이다.
1. FORMAT.md 4절과 11.3 절을 따른다. 단계마다 choice 3개, short 2개다.
2. choice 는 선지 5개, 정답 1개다. 오답 선지 가운데 하나 이상은 규칙 두 개를 엮어야 지울 수 있게 만든다.
3. short 는 input 을 둔다. 그 단계 프로그램에 넣으면 정확히 한 줄이 출력되게 만들고 그 줄을 answers[0] 에 적는다. 정답은 규칙 두 개 이상을 함께 적용해야 나오게 만든다.
4. refs, quote, explanation 을 모두 채운다. explanation 에는 정답의 이유와 오답의 이유를 함께 쓴다.
5. 발문은 "~은?" 이나 "~는?" 으로 끝낸다. 정답 번호를 고르게 섞는다.
6. choice 정답이 하나뿐인지 스펙 문장으로 다시 확인하고 short 정답은 스펙만 보고 손으로 다시 유도한다.`

const CROSS = `${COMMON}
${CONTEXT}

너는 교차 검증자다. 새 단계의 참조 풀이, 테스트, 문항은 서로 독립적으로 만들어졌다.
이 셋과 스펙이 서로 맞는지 실행으로 확인하고 틀린 쪽을 고친다.
1. ${ROOT}/engine/bin/cresvoid validate ${TRACK} 를 실행한다. 검사 항목은 FORMAT.md 10절이다.
2. 불일치마다 스펙을 기준으로 어느 쪽이 맞는지 판정한다. 스펙이 두 가지로 읽혀서 생긴 불일치면 스펙 번호 문단에 규칙을 보태 한 가지로 고정한 뒤 틀린 산출물을 고친다.
3. 불일치를 없애려고 테스트를 지우지 않는다. 스펙상 틀린 테스트는 같은 규칙을 확인하는 올바른 테스트로 바꾼다.
4. 참조 풀이가 틀렸으면 그 단계와 뒤 단계 참조 풀이를 함께 고친다.
5. 고친 뒤 validate 를 다시 실행한다. 통과할 때까지 반복한다.
6. 생성기 테스트마다 참조 풀이 실행 시간을 timings 에 적는다.`

const REVIEW = `${COMMON}
${LEARNER}
${CONTEXT}

너는 이 트랙을 풀 학습자 관점의 검토자다. 파일을 고치지 않는다. 검토 결과만 돌려준다.
새 단계의 spec.md, hidden/quiz.json, hidden/tests.json 을 읽는다. hidden/solution/ 은 열지 않는다.
검토 항목:
1. 난도: 숙련 개발자에게 너무 쉬운 단계나 규칙이 있는가. 대규모 테스트가 색인이나 알맞은 자료구조 없이는 통과하지 못하는가.
2. 명확성: 스펙만으로 구현할 때 두 가지로 읽히는 문장이 남아 있는가.
3. 테스트 정합성: 스펙에 없는 동작에 기대는 테스트가 있는가. 테스트가 확인하지 않는 스펙 규칙이 있는가. 테스트 이름이 숨은 케이스의 조건이나 답을 드러내는가.
4. 읽기 훈련 가치: 스펙 문장이 정확하고 읽을 만한 한국어인가.
5. 문항 품질: 스펙을 대충 읽으면 틀리는가. 정답이 하나뿐인가. 오답 선지가 스펙 문장 하나를 뒤집는 데 그치지 않는가.
지적마다 파일, 위치, 근거 문장, 심각도(must, should, nit)를 적는다. 직접 구현해 보며 확인해도 된다(/tmp).`

function finalFixPrompt(review) {
  return `${COMMON}
${WRITING}
${CONTEXT}

너는 마지막 수정 담당이다. 아래 학습자 검토 지적을 하나씩 검증한다.
must 와 should 가운데 타당한 지적을 고친다. nit 은 고치는 비용이 작을 때만 고친다. 타당하지 않은 지적은 이유와 함께 rejected 에 적는다.
스펙을 고쳤으면 참조 풀이, 테스트, 문항을 함께 맞추고 ${ROOT}/engine/bin/cresvoid validate ${TRACK} 가 통과할 때까지 고친다.

검토 결과(JSON):
${JSON.stringify(review, null, 2)}`
}

const STR_ARRAY = { type: 'array', items: { type: 'string' } }

const SPEC_RESULT = {
  type: 'object',
  properties: { files: STR_ARRAY, decisions: STR_ARRAY },
  required: ['files', 'decisions'],
}

const ISSUES = {
  type: 'object',
  properties: {
    issues: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          stage: { type: 'string' },
          paragraph: { type: 'string' },
          problem: { type: 'string' },
          example_input: { type: 'string' },
          readings: STR_ARRAY,
          severity: { type: 'string', enum: ['must', 'should'] },
        },
        required: ['stage', 'problem', 'severity'],
      },
    },
  },
  required: ['issues'],
}

const FIX_RESULT = {
  type: 'object',
  properties: { resolved: STR_ARRAY, rejected: STR_ARRAY },
  required: ['resolved', 'rejected'],
}

const REFERENCE_RESULT = {
  type: 'object',
  properties: {
    files: STR_ARRAY,
    generators: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          stage: { type: 'string' },
          usage: { type: 'string' },
          normal_args: { type: 'string' },
          scale_args: { type: 'string' },
          scale_runtime_sec: { type: 'number' },
        },
        required: ['stage', 'usage', 'normal_args', 'scale_args'],
      },
    },
    spec_changes: STR_ARRAY,
  },
  required: ['files', 'generators', 'spec_changes'],
}

const SIMPLE_RESULT = {
  type: 'object',
  properties: { files: STR_ARRAY, notes: STR_ARRAY },
  required: ['files', 'notes'],
}

const CROSS_RESULT = {
  type: 'object',
  properties: {
    rounds: { type: 'integer' },
    mismatches: STR_ARRAY,
    spec_changes: STR_ARRAY,
    final_status: { type: 'string', enum: ['clean', 'remaining'] },
    remaining: STR_ARRAY,
    timings: STR_ARRAY,
  },
  required: ['rounds', 'mismatches', 'final_status', 'remaining'],
}

const REVIEW_RESULT = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string' },
          location: { type: 'string' },
          evidence: { type: 'string' },
          problem: { type: 'string' },
          severity: { type: 'string', enum: ['must', 'should', 'nit'] },
        },
        required: ['file', 'problem', 'severity'],
      },
    },
    difficulty_verdict: { type: 'string' },
  },
  required: ['findings', 'difficulty_verdict'],
}

phase('스펙')
const spec = await agent(ARCHITECT, { label: 'spec:author', phase: '스펙', schema: SPEC_RESULT })
if (!spec) return { failed: 'spec' }
const hunted = []
for (const round of [1, 2]) {
  const found = await agent(hunterPrompt(round), { label: 'spec:hunt-' + round, phase: '스펙', schema: ISSUES })
  const issues = found ? found.issues : []
  hunted.push({ round, count: issues.length })
  if (issues.length === 0) break
  await agent(fixerPrompt(issues), { label: 'spec:fix-' + round, phase: '스펙', schema: FIX_RESULT })
}
log('스펙: 모호성 검토 ' + hunted.map(h => h.round + '차 ' + h.count + '건').join(', '))

phase('풀이')
const reference = await agent(REFERENCE, { label: 'reference', phase: '풀이', schema: REFERENCE_RESULT })

phase('테스트와 문항')
const writeTests = () => agent(testsPrompt(reference), { label: 'tests', phase: '테스트와 문항', schema: SIMPLE_RESULT })
const writeQuiz = () => agent(QUIZ, { label: 'quiz', phase: '테스트와 문항', schema: SIMPLE_RESULT })
const [tests, quiz] = LANES > 1 ? await parallel([writeTests, writeQuiz]) : [await writeTests(), await writeQuiz()]

phase('검증')
const cross = await agent(CROSS, { label: 'cross-validate', phase: '검증', schema: CROSS_RESULT })
log('교차 검증: ' + (cross ? cross.final_status : '실패'))
const review = await agent(REVIEW, { label: 'learner-review', phase: '검증', schema: REVIEW_RESULT })
const actionable = review ? review.findings.filter(f => f.severity !== 'nit') : []
const finalFix = actionable.length > 0 ? await agent(finalFixPrompt(review), { label: 'final-fix', phase: '검증', schema: FIX_RESULT }) : null
return { spec, hunted, reference, tests, quiz, cross, review, finalFix }
