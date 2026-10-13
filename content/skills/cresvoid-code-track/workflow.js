export const meta = {
  name: 'cresvoid-code-track',
  description: 'cresvoid code 트랙(코드 읽기) 단계를 만들고 블라인드 풀이, antislop 검토, 수정, 재확인으로 검증한다',
  whenToUse: 'cresvoid code 트랙에 단계를 새로 만들거나 늘릴 때. args 에 root, track, stages 를 넘긴다.',
  phases: [
    { title: '출제', detail: '안내 문서, Kotlin 코드, 문항 작성과 실행 확인' },
    { title: '블라인드', detail: '정답 없이 읽기만으로 풀고 실행으로 대조' },
    { title: '검토', detail: 'antislop 기준과 학습자 관점 검토' },
    { title: '수정', detail: '지적 반영, 재풀이, 마지막 수정' },
  ],
}

const ROOT = args.root
const TRACK = args.track
const TRACK_DIR = ROOT + '/content/tracks/' + TRACK
const LANES = args.lanes || 2
if (args.libs && args.libs !== 'kotlinx-coroutines') throw new Error('libs 는 kotlinx-coroutines 만 받는다: ' + args.libs)
const LIBS = args.libs ? `[의존성]
- 이 단계의 Kotlin 프로젝트는 kotlinx.coroutines 1.11.0 을 쓴다(FORMAT.md 7절, ${ROOT}/docs/decisions.md D21).
- Kotlin 프로젝트를 만들 때 빈 lib/ 디렉터리를 만든 뒤 kotlin ${ROOT}/content/templates/sync.main.kts 를 실행한다. sync 가 kotlinx-coroutines-core 와 kotlinx-coroutines-test jar 를 lib/ 에 넣는다. lib/ 의 jar 는 직접 넣거나 고치지 않는다.
- 학습자에게 실험 표시(@ExperimentalCoroutinesApi, @FlowPreview, @DelicateCoroutinesApi)가 붙은 API 를 요구하지 않는다. 가상 시각을 읽는 currentTime 처럼 실행 틀에 꼭 필요한 곳만 @OptIn 으로 한 곳에 둔다.
- runTest 의 기본 실제 시간 한도(1분)는 표준 입력을 읽는 동안에도 흐른다. 실행 틀은 runTest(timeout = Duration.INFINITE) 를 쓰거나 입력을 runTest 밖에서 모두 읽는다.` : ''
const ANTISLOP_CODE = '설치된 antislop-code 스킬의 SKILL.md'
const ANTISLOP_CORE = '설치된 antislop 스킬의 SKILL.md'

const COMMON = [
  '이 작업은 사용자의 개인 학습 프로젝트 ' + ROOT + ' 에서 진행한다. 이 프로젝트의 규칙은 ' + ROOT + '/AGENTS.md 와 ' + ROOT + '/docs/agents.md 에 있다.',
  '프로젝트 밖 기록 시스템은 쓰지 않는다. git 커밋을 하지 않는다. 결과는 최종 출력으로 돌려준다.',
  '작업 전에 ' + ROOT + '/content/FORMAT.md 를 처음부터 끝까지 읽는다. 엔진과 컨텐츠의 계약이다.',
  '프로젝트에 남기는 코드는 모두 Kotlin 이다. /tmp 에서 잠깐 쓰는 검사 도구는 언어를 가리지 않는다.',
  'Kotlin 코드를 쓰기 전에 세션의 코드 규칙과 설치된 kotlin-core-best-practices 스킬을 읽고 따른다.',
  '이 프롬프트가 지정한 파일만 만들거나 고친다. 다른 agent 가 같은 시간에 다른 단계를 만든다. track.json 은 고치지 않는다.',
  '학습자 공간과 진행 기록(' + ROOT + '/practice)을 바꾸지 않는다. cresvoid 로 흐름을 시험할 때는 프로젝트를 /tmp 에 복사해 그 안에서 실행한다. cresvoid validate 는 상태를 바꾸지 않으므로 프로젝트에서 실행해도 된다.',
].join('\n')

const WRITING = [
  '한국어 문서 본문은 해라체 평서형으로 쓴다.',
  '한국어 초안을 완성한 뒤 설치된 korean-writing-guide 스킬의 SKILL.md 를 읽고 정확히 한 번 적용한다. 문장의 명확성도 같은 스킬로 확인한다.',
].join('\n')

const LEARNER = [
  '학습자는 Kotlin 백엔드 경력이 있는 개발자다. 쇼핑 카탈로그 서비스를 만든다.',
  '학습자는 AI 없이 코드를 끝까지 읽고 동작을 예측하는 힘을 되찾으려 한다. 쉬운 과제를 원하지 않는다.',
].join('\n')

const ANTISLOP = [
  'antislop 기준: ' + ANTISLOP_CODE + ' 를 읽고 따른다. ' + ANTISLOP_CORE + ' 에서는 C-1~C-5, R-31, R-35, R-36, R-38, Delivery Gate 만 코드 컨텐츠에 적용한다. UI 규칙은 적용하지 않는다.',
  'FORMAT.md 11.8 절이 이 프로젝트에 맞춘 요약이다.',
].join('\n')

function cardRules(st) {
  return [
    st.checks ? '- 검증 기준: ' + st.checks : '',
    st.avoid ? '- 피할 것: ' + st.avoid : '',
  ].filter(Boolean).join('\n')
}

function stageDir(st) {
  return TRACK_DIR + '/stages/' + st.dir
}

function authorPrompt(st) {
  const dir = stageDir(st)
  return `${COMMON}
${WRITING}
${LEARNER}
${ANTISLOP}

너는 code 트랙(코드 읽기) ${TRACK} 의 ${st.dir} 단계 출제자다.
만들 파일:
- ${dir}/guide.md
- ${dir}/code/ : FORMAT.md 7절 모양의 Kotlin 프로젝트. src/main/kotlin 밖의 파일(build.sh, run.sh, build.gradle.kts, settings.gradle.kts, .java-version)은 직접 쓰지 않는다. 소스를 쓴 뒤 kotlin ${ROOT}/content/templates/sync.main.kts 를 실행해 ${ROOT}/content/templates/kotlin 의 파일을 복사한다.
- ${dir}/hidden/quiz.json

[단계 설계]
- 제목: ${st.title}
- 도메인과 상황: ${st.domain}
- 문항이 겨냥할 Kotlin·표준 라이브러리 지점: ${st.focus}
- 코드 규모: ${st.size}
- 문항 구성: ${st.questions}
- 난도: ${st.level}
- 목표 풀이 시간: ${st.target_minutes}분
${cardRules(st)}
${LIBS}

[코드]
1. 표준 입력에서 명령을 한 줄씩 EOF 까지 읽어 처리하고 결과를 표준 출력에 쓰는 프로그램이다. 시각이 필요하면 입력 명령으로 움직이는 가짜 시계를 쓴다. 출력은 입력만으로 정해진다.
2. 실무 코드처럼 쓴다. 도메인의 말로 이름을 짓고 책임별로 파일을 나눈다. 문항의 함정은 실무 코드에서 자연스럽게 생기는 선택(언어 규칙, 표준 라이브러리 동작, 순서와 경계)에서 나와야 한다. 퍼즐을 위해 일부러 꼬은 코드를 쓰지 않는다.
3. 코드에 버그를 심지 않는다. 코드는 의도한 대로 동작한다. 문항은 대충 읽으면 동작을 잘못 예측하게 되는 지점을 묻는다.
4. ${LIBS ? 'Kotlin 표준 라이브러리, JDK, kotlinx.coroutines 만 쓴다.' : 'Kotlin 표준 라이브러리와 JDK 만 쓴다.'}

[guide.md]
1. 번호 문단으로 쓴다. 코드가 쓰이는 맥락, 명령 형식, 출력 형식, 문항이 가정하는 상황을 설명한다.
2. 코드의 동작, 특히 문항이 묻는 부분을 미리 풀어 주지 않는다.
3. 제목 다음에 읽을 파일 목록과 읽는 순서를 적는다.

[quiz.json]
1. FORMAT.md 4절의 code 규칙을 따른다. short 문항은 input 이 있어야 하고 그 input 으로 실행하면 정확히 한 줄이 나와야 한다.
2. 문항마다 코드를 따라가며 상태를 추적해야 풀리게 만든다. refs 는 코드 위치(파일:시작줄-끝줄)를 중심으로 쓰고 quote 는 그 범위의 코드 한 줄을 글자 그대로 옮긴다.
3. choice 오답은 코드를 대충 읽었을 때 고르게 되는 그럴듯한 예측으로 만든다. 정답은 하나뿐이다. 정답 번호를 고르게 섞는다.
4. explanation 은 코드 위치를 들어 정답의 이유와 오답 하나하나의 이유를 쓴다.
5. 발문은 "~은?" 이나 "~는?" 으로 끝낸다.

[확인]
1. code/ 를 build.sh 로 빌드한다. short 문항 input 으로 실행해 출력 한 줄이 answers[0] 과 같은지 확인한다.
2. choice 정답과 해설에 쓴 동작 주장은 /tmp 에 만든 작은 입력이나 시험 코드로 실행해 확인한다.
3. ${ROOT}/engine/bin/cresvoid validate ${TRACK} 를 실행해 이 단계에 관한 오류가 없게 한다. 다른 단계의 오류는 다른 agent 가 고친다.
4. 최종 출력 rationale 에 문항마다 목적(어떤 읽기 실수를 잡는지)과 함정이 생기는 코드 위치를 적는다. 코드 구조와 도메인을 고른 이유도 적는다.
5. 최종 출력 gate 에 Delivery Gate 를 PASS/FAIL 과 증거로 적는다. 주석 위생, 이름, 실무다움, 실행으로 확인한 주장, 지어낸 API 없음을 포함한다.
6. 최종 출력 questions 에는 quiz.json 의 문항을 그대로 옮긴다.`
}

function solverPrompt(st, questions, round) {
  const dir = stageDir(st)
  return `${COMMON}

너는 코드 읽기 문항을 푸는 학습자이자 문항 검토자다. 파일은 고치지 않는다.
${dir}/guide.md 와 ${dir}/code/src/main/kotlin/ 아래 파일만 읽는다. ${dir}/hidden/ 디렉터리는 열지 않는다. 그 안에 정답이 있다.

1. 먼저 코드를 실행하지 않고 읽기만으로 아래 문항을 모두 푼다. 문항마다 답, 확신(high, medium, low), 근거 코드 위치를 적는다. 이 답이 first_answers 다.
2. 그다음 ${dir}/code 를 /tmp/cresvoid-blind-${TRACK}-${st.dir} 로 복사해 빌드하고 short 문항 input 으로 실행해 자기 답을 확인한다. choice 문항은 확인할 수 있으면 작은 입력으로 실행해 확인한다. 실행 뒤 답을 final_answers 에 적는다.
3. 처음 답이 틀린 문항마다 이유를 적는다. 자기 읽기 실수인지, 문항이나 안내 문서가 모호해서인지 구분한다.
4. choice 문항은 선지를 하나씩 따진다. 정답이 아니라고 본 선지 가운데 정답이라고 우길 근거가 있는 선지를 defensible 에 적는다.
5. 안내 문서와 코드만으로 판단할 수 없는 문항, 두 가지로 읽히는 문항, 해설이 필요할 사실이 코드에 없는 문항은 issues 에 적는다.
6. 코드를 읽지 않고도 풀리는 문항은 too_easy 에 적는다.
7. 코드가 실무 코드답지 않은 곳(일부러 꼬은 구조, 뜻 없는 이름, 장식 주석, 코드를 다시 말하는 주석)도 issues 에 적는다.
8. 걸린 시간과 체감 난도를 difficulty 에 적는다.
${round > 1 ? '이 문항들은 한 차례 수정을 거쳤다. 남은 문제만 엄격하게 찾는다.' : ''}

문항(JSON, 정답 없음):
${JSON.stringify(questions, null, 2)}`
}

function auditPrompt(st, written, solved) {
  const dir = stageDir(st)
  return `${COMMON}
${LEARNER}
${ANTISLOP}

너는 code 트랙 ${st.dir} 단계의 검토자다. 파일은 고치지 않는다. 검토 결과만 돌려준다.
읽을 것: ${dir}/guide.md, ${dir}/code/, ${dir}/hidden/quiz.json, 아래 출제자 보고와 블라인드 풀이 결과.
${cardRules(st) ? "지켰는지 볼 카드 기준:\n" + cardRules(st) : ""}

관점:
1. antislop: antislop-code 점검표, 코드의 실무다움, 이름, 구조, 퍼즐 냄새, 결정마다 이유가 있는지(출제자 rationale), 주장마다 실행 증거가 있는지.
2. 문항: 정답이 하나뿐인가. refs 와 quote 가 정확한가. 함정이 공정한가(언어 규칙에 기댄 함정은 실행으로 확인되는가). 난도가 학습자 수준에 맞는가. 해설의 동작 주장을 직접 실행해 확인한다.
3. guide.md: 맥락이 충분한가. 동작을 미리 풀어 주지 않는가. 한국어가 정확한가.
4. 블라인드 풀이: 틀린 문항과 defensible 선지의 원인이 문항 결함인지 판정한다.
5. Delivery Gate 를 다시 점검하고 PASS/FAIL 과 증거를 적는다.

지적마다 파일, 위치, 심각도(must, should, nit), 문제, 근거, 제안을 적는다.

출제자 보고(JSON):
${JSON.stringify(written, null, 2)}

블라인드 풀이 결과(JSON):
${JSON.stringify(solved, null, 2)}`
}

function revisePrompt(st, payload, round) {
  const dir = stageDir(st)
  return `${COMMON}
${WRITING}
${ANTISLOP}

너는 code 트랙 ${st.dir} 단계의 수정 담당이다.
${dir}/guide.md, ${dir}/code/, ${dir}/hidden/quiz.json 을 읽고 아래 지적과 풀이 결과를 반영한다.
1. 지적마다 코드와 실행으로 타당성을 검증한다. 타당한 지적만 고치고 나머지는 이유와 함께 rejected 에 적는다.
2. 블라인드 풀이자가 틀린 문항 가운데 문항이나 안내 문서 결함 때문인 것은 고친다. 풀이자의 읽기 실수 때문인 것은 함정이 공정한지만 확인한다.
3. defensible 선지는 근거가 타당하면 선지나 코드를 고쳐 정답을 하나로 만든다.
4. too_easy 문항은 코드를 따라가야 풀리게 고친다.
5. 고친 뒤 short 문항을 실행으로 다시 확인하고 ${ROOT}/engine/bin/cresvoid validate ${TRACK} 에서 이 단계 오류가 없게 한다.
6. 최종 출력 questions 에는 수정한 quiz.json 의 문항을 그대로 옮긴다. gate 에 Delivery Gate 를 다시 적는다.
${round > 1 ? '이번은 마지막 수정이다. 남은 문제를 모두 해결한다.' : ''}

지적과 풀이 결과(JSON):
${JSON.stringify(payload, null, 2)}`
}

const STR_ARRAY = { type: 'array', items: { type: 'string' } }

const QUESTION = {
  type: 'object',
  properties: {
    id: { type: 'string' },
    type: { type: 'string' },
    prompt: { type: 'string' },
    context: { type: 'string' },
    input: STR_ARRAY,
    choices: STR_ARRAY,
    answer: { type: 'integer' },
    answers: STR_ARRAY,
  },
  required: ['id', 'type', 'prompt'],
}

const WRITTEN = {
  type: 'object',
  properties: {
    files: STR_ARRAY,
    code_lines: { type: 'integer' },
    questions: { type: 'array', items: QUESTION },
    rationale: STR_ARRAY,
    gate: STR_ARRAY,
    validate: { type: 'string' },
  },
  required: ['files', 'questions', 'rationale', 'gate', 'validate'],
}

const ANSWER = {
  type: 'object',
  properties: {
    id: { type: 'string' },
    choice: { type: 'integer' },
    text: { type: 'string' },
    confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
    refs: STR_ARRAY,
    defensible: { type: 'array', items: { type: 'integer' } },
    note: { type: 'string' },
  },
  required: ['id', 'confidence'],
}

const SOLVED = {
  type: 'object',
  properties: {
    first_answers: { type: 'array', items: ANSWER },
    final_answers: { type: 'array', items: ANSWER },
    misses: STR_ARRAY,
    issues: STR_ARRAY,
    too_easy: STR_ARRAY,
    difficulty: { type: 'string' },
  },
  required: ['first_answers', 'final_answers', 'issues', 'too_easy', 'difficulty'],
}

const FINDINGS = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string' },
          location: { type: 'string' },
          severity: { type: 'string', enum: ['must', 'should', 'nit'] },
          problem: { type: 'string' },
          evidence: { type: 'string' },
          suggestion: { type: 'string' },
        },
        required: ['file', 'severity', 'problem', 'evidence'],
      },
    },
    gate: STR_ARRAY,
    verdict: { type: 'string' },
  },
  required: ['findings', 'gate', 'verdict'],
}

const REVISED = {
  type: 'object',
  properties: {
    changes: STR_ARRAY,
    rejected: STR_ARRAY,
    questions: { type: 'array', items: QUESTION },
    gate: STR_ARRAY,
    validate: { type: 'string' },
    remaining_concerns: STR_ARRAY,
  },
  required: ['changes', 'rejected', 'questions', 'gate', 'validate', 'remaining_concerns'],
}

function strip(questions) {
  return questions.map(q => ({
    id: q.id,
    type: q.type,
    prompt: q.prompt,
    context: q.context || '',
    input: q.input || [],
    choices: q.choices || [],
  }))
}

function normalize(text) {
  return String(text || '').trim().split(/\s+/).join(' ')
}

function keyOf(q) {
  return q.type === 'choice' ? String(q.answer) : normalize((q.answers || [])[0])
}

function answerOf(found, q) {
  if (!found) return null
  return q.type === 'choice' ? String(found.choice) : normalize(found.text)
}

function comparison(questions, solved) {
  return questions.map(q => {
    const first = solved ? (solved.first_answers || []).find(a => a.id === q.id) : null
    const final = solved ? (solved.final_answers || []).find(a => a.id === q.id) : null
    return {
      id: q.id,
      key: keyOf(q),
      first: answerOf(first, q),
      final: answerOf(final, q),
      confidence: first ? first.confidence : null,
      defensible: final ? final.defensible || [] : [],
      note: final ? final.note || '' : '',
    }
  })
}

function leftovers(questions, solved) {
  if (!solved) return [{ problem: 'confirm solver failed' }]
  const rows = comparison(questions, solved).filter(row => row.final !== row.key || row.defensible.length > 0)
  const problems = rows.map(row => ({ question: row }))
  if (solved.issues.length > 0) problems.push({ issues: solved.issues })
  if (solved.too_easy.length > 0) problems.push({ too_easy: solved.too_easy })
  return problems
}

async function runStage(st) {
  const written = await agent(authorPrompt(st), { label: st.dir + ':author', phase: '출제', schema: WRITTEN })
  if (!written) return { stage: st.dir, failed: 'author' }
  const solved = await agent(solverPrompt(st, strip(written.questions), 1), { label: st.dir + ':blind', phase: '블라인드', schema: SOLVED })
  const firstRows = comparison(written.questions, solved)
  const audit = await agent(auditPrompt(st, written, { comparison: firstRows, solver: solved }), { label: st.dir + ':audit', phase: '검토', schema: FINDINGS })
  const revised = await agent(
    revisePrompt(st, { comparison: firstRows, solver: solved, audit }, 1),
    { label: st.dir + ':revise', phase: '수정', schema: REVISED },
  )
  const current = revised && revised.questions.length > 0 ? revised.questions : written.questions
  const confirm = await agent(solverPrompt(st, strip(current), 2), { label: st.dir + ':confirm', phase: '수정', schema: SOLVED })
  const left = leftovers(current, confirm)
  let finalFix = null
  if (left.length > 0) {
    finalFix = await agent(revisePrompt(st, { problems: left, solver: confirm }, 2), { label: st.dir + ':final', phase: '수정', schema: REVISED })
  }
  const firstCorrect = firstRows.filter(row => row.first === row.key).length
  log(st.dir + ': 블라인드 첫 풀이 ' + firstCorrect + '/' + written.questions.length + ', 재풀이 남은 문제 ' + left.length)
  return {
    stage: st.dir,
    rationale: written.rationale,
    first_blind: firstRows,
    audit_findings: audit ? audit.findings : null,
    audit_gate: audit ? audit.gate : null,
    revise_changes: revised ? revised.changes : null,
    revise_rejected: revised ? revised.rejected : null,
    confirm: confirm ? comparison(current, confirm) : null,
    leftovers: left,
    final_changes: finalFix ? finalFix.changes : null,
    gate: finalFix ? finalFix.gate : (revised ? revised.gate : written.gate),
    remaining: finalFix ? finalFix.remaining_concerns : (revised ? revised.remaining_concerns : []),
  }
}

async function runLane(stages) {
  const results = []
  for (const st of stages) {
    results.push(await runStage(st))
  }
  return results
}

const lanes = Array.from({ length: LANES }, () => [])
args.stages.forEach((st, index) => lanes[index % LANES].push(st))
const laneResults = await parallel(lanes.filter(lane => lane.length > 0).map(lane => () => runLane(lane)))
return { track: TRACK, stages: laneResults.filter(Boolean).flat() }
