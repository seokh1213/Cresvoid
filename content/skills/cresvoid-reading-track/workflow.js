export const meta = {
  name: 'cresvoid-reading-track',
  description: 'cresvoid reading 트랙(비문학 독해) 단계를 만들고 블라인드 풀이, 수정, 재풀이로 검증한다',
  whenToUse: 'cresvoid reading 트랙에 지문 단계를 새로 만들 때. args 에 root, track, stages 를 넘긴다.',
  phases: [
    { title: '출제', detail: '지문과 문항 작성, 사실 확인' },
    { title: '블라인드', detail: '정답 없이 지문만 보고 풀이' },
    { title: '수정', detail: '풀이 결과 반영, 재풀이, 마지막 수정' },
  ],
}

const ROOT = args.root
const TRACK = args.track || 'reading'
const TRACK_DIR = ROOT + '/content/tracks/' + TRACK
const LANES = args.lanes || 2

const COMMON = [
  '이 작업은 사용자의 개인 학습 프로젝트 ' + ROOT + ' 에서 진행한다. 이 프로젝트의 규칙은 ' + ROOT + '/AGENTS.md 와 ' + ROOT + '/docs/agents.md 에 있다.',
  '프로젝트 밖 기록 시스템은 쓰지 않는다. git 커밋을 하지 않는다. 결과는 최종 출력으로 돌려준다.',
  '작업 전에 ' + ROOT + '/content/FORMAT.md 를 처음부터 끝까지 읽는다. 엔진과 컨텐츠의 계약이다.',
  '이 프롬프트가 지정한 파일만 만들거나 고친다. 다른 agent 가 같은 시간에 다른 단계를 만든다. track.json 은 고치지 않는다.',
  '계산이나 글자 수 확인이 필요하면 /tmp 에서 Kotlin 스크립트나 다른 도구를 쓴다. 프로젝트에 검사 코드를 남기지 않는다.',
].join('\n')

const WRITING = [
  '한국어 문서 본문은 해라체 평서형으로 쓴다.',
  '초안을 완성한 뒤 설치된 korean-writing-guide 스킬의 SKILL.md 를 읽고 정확히 한 번 적용한다. 문장의 명확성도 같은 스킬로 확인한다.',
].join('\n')

function stageDir(st) {
  return TRACK_DIR + '/stages/' + st.dir
}

function writerPrompt(st) {
  const dir = stageDir(st)
  return `${COMMON}
${WRITING}

너는 비문학 독해 지문과 문항의 출제자다.
학습자는 개발자다. 짧은 영상에 익숙해져 긴 글을 정확히 읽는 힘이 약해졌고 그 힘을 되찾으려 한다. 쉬운 문제를 원하지 않는다.

만들 파일:
- ${dir}/passage.md
- ${dir}/hidden/quiz.json

지문 조건:
1. 분야와 주제: ${st.topic}
2. 다룰 내용: ${st.scope}
3. 길이: 공백 포함 ${st.length}자. 문단 번호 표지를 포함해 센다. 길이는 도구로 세어 확인한다.
4. 문단 수: ${st.paragraphs}개. 문단마다 [1] 부터 차례로 번호를 붙인다.
5. 난도: ${st.level}
6. 기존 시험 지문, 교과서 문단, 번역문을 옮기지 않고 새로 쓴다.
7. 사실관계가 정확해야 한다. 확신하지 못하는 세부 사실은 넣지 않는다.
8. 사실 확인은 1차 자료로 한다. WebSearch 와 WebFetch 는 ToolSearch 로 불러 쓴다. 참고할 출처: ${st.sources || '주제의 1차 자료'}
9. 개념 사이의 조건, 인과, 대비, 예외 관계를 촘촘하게 담는다.
10. 문항이 가리키는 구절 앞에는 ㉠, ㉡ 같은 표지를 붙인다.
11. 지문에 제목을 쓰지 않는다.
${st.avoid ? '12. 피할 것: ' + st.avoid : ''}

문항 조건:
1. ${st.questions}문항이고 모두 choice 다. 선지는 5개, 정답은 1개다.
2. 구성: ${st.mix}
3. <보기> 는 context 필드에 쓴다. 수치 계산이 들어간 <보기> 는 도구로 계산해 확인한다.
4. 오답 선지는 지문 내용을 부분적으로 비틀어 만든다. 상식만으로 고를 수 있는 선지를 만들지 않는다.
5. 정답 선지 번호를 고르게 섞는다.
6. refs, quote, explanation 을 모두 채운다. quote 는 refs 문단 가운데 하나의 문장을 글자 그대로 옮긴다. explanation 에는 선지 다섯 개가 각각 왜 맞거나 틀리는지 쓴다.
7. 발문은 수능 문항 형식을 따른다.
${st.traps ? '8. 문항이 노릴 지점: ' + st.traps : ''}

다 만든 뒤 스스로 한 번 풀어 보고 정답이 하나뿐인지 확인한다.
${ROOT}/engine/bin/cresvoid validate ${TRACK} 에서 이 단계에 관한 오류가 없게 한다. 다른 단계의 오류는 다른 agent 가 고친다.
최종 출력의 questions 에는 quiz.json 의 문항을 id, prompt, context, choices, answer 만 담아 그대로 옮긴다. facts_to_check 에는 확인한 사실과 출처를 적는다.`
}

function solverPrompt(st, questions, round) {
  const dir = stageDir(st)
  return `${COMMON}

너는 수능과 LEET 국어 문항을 푸는 수험생이자 문항 검토자다.
${dir}/passage.md 만 읽는다. ${dir}/hidden/ 디렉터리는 열지 않는다. 그 안에 정답이 들어 있다. 파일은 고치지 않는다.

아래 문항을 지문만 근거로 푼다.
문항마다 고른 선지, 확신 정도, 근거 문단 번호를 적는다.
그다음 선지 다섯 개를 하나씩 따진다. 정답이 아니라고 본 선지 가운데 정답이라고 우길 근거가 지문에 있는 선지가 있으면 defensible 에 적는다.
지문만으로 판단할 수 없는 선지, 사실과 다른 지문 서술, 두 가지로 읽히는 문장은 issues 에 적는다.
지문을 읽지 않고도 풀리는 문항은 too_easy 에 적는다.
${round > 1 ? '이 문항들은 한 차례 수정을 거쳤다. 남은 문제만 엄격하게 찾는다.' : ''}

문항(JSON, 정답 없음):
${JSON.stringify(questions, null, 2)}`
}

function reviserPrompt(st, payload, round) {
  const dir = stageDir(st)
  return `${COMMON}
${WRITING}

너는 비문학 문항 검수자다.
${dir}/passage.md 와 ${dir}/hidden/quiz.json 을 읽는다.
아래 풀이 결과는 정답을 모르는 풀이자가 지문만 보고 낸 것이다.

할 일:
1. 풀이자의 답이 quiz.json 정답과 다른 문항을 분석한다. 정답 표기가 틀렸으면 정답을 고친다. 두 선지가 모두 맞을 수 있으면 선지나 지문을 고쳐 정답을 하나로 만든다.
2. defensible 로 지목된 선지를 검토한다. 근거가 타당하면 그 선지나 지문을 고친다.
3. issues 에 적힌 지문 서술 문제를 검토하고 고친다.
4. 지문의 사실 서술을 모두 점검한다. 틀린 서술은 고치거나 뺀다. 고친 내용에 맞춰 문항과 해설도 고친다. 사실 확인은 1차 자료로 한다. WebSearch 와 WebFetch 는 ToolSearch 로 불러 쓴다.
5. too_easy 로 지목된 문항은 지문을 정확히 읽어야 풀리도록 고친다.
6. refs, quote, explanation 이 고친 내용과 맞는지 확인한다.
7. 지문 길이(공백 포함 ${st.length}자)와 문단 수(${st.paragraphs}개) 조건을 유지한다.
8. 고친 문장은 기존 문체인 해라체 평서형에 맞춘다.
9. ${ROOT}/engine/bin/cresvoid validate ${TRACK} 에서 이 단계 오류가 없게 한다.
최종 출력의 questions 에는 수정한 quiz.json 의 문항을 id, prompt, context, choices, answer 만 담아 옮긴다.
${round > 1 ? '이번은 마지막 수정이다. 재풀이에서 남은 문제를 모두 해결한다.' : ''}

풀이 결과(JSON):
${JSON.stringify(payload, null, 2)}`
}

const STR_ARRAY = { type: 'array', items: { type: 'string' } }

const QUESTION_ITEM = {
  type: 'object',
  properties: {
    id: { type: 'string' },
    prompt: { type: 'string' },
    context: { type: 'string' },
    choices: STR_ARRAY,
    answer: { type: 'integer' },
  },
  required: ['id', 'prompt', 'choices', 'answer'],
}

const WRITTEN = {
  type: 'object',
  properties: {
    passage_chars: { type: 'integer' },
    paragraphs: { type: 'integer' },
    questions: { type: 'array', items: QUESTION_ITEM },
    facts_to_check: STR_ARRAY,
    validate: { type: 'string' },
  },
  required: ['passage_chars', 'questions', 'facts_to_check', 'validate'],
}

const SOLVED = {
  type: 'object',
  properties: {
    answers: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          choice: { type: 'integer' },
          confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
          refs: STR_ARRAY,
          defensible: { type: 'array', items: { type: 'integer' } },
          note: { type: 'string' },
        },
        required: ['id', 'choice', 'confidence', 'defensible'],
      },
    },
    issues: STR_ARRAY,
    too_easy: STR_ARRAY,
    difficulty: { type: 'string' },
  },
  required: ['answers', 'issues', 'too_easy', 'difficulty'],
}

const REVISED = {
  type: 'object',
  properties: {
    changes: STR_ARRAY,
    facts_checked: {
      type: 'array',
      items: {
        type: 'object',
        properties: { claim: { type: 'string' }, verdict: { type: 'string' }, fix: { type: 'string' } },
        required: ['claim', 'verdict'],
      },
    },
    questions: { type: 'array', items: QUESTION_ITEM },
    remaining_concerns: STR_ARRAY,
    validate: { type: 'string' },
  },
  required: ['changes', 'questions', 'remaining_concerns', 'validate'],
}

function strip(questions) {
  return questions.map(q => ({ id: q.id, prompt: q.prompt, context: q.context || '', choices: q.choices }))
}

function comparison(questions, solved) {
  const answers = solved ? solved.answers : []
  return questions.map(q => {
    const found = answers.find(a => a.id === q.id)
    return {
      id: q.id,
      key: q.answer,
      solver_choice: found ? found.choice : null,
      confidence: found ? found.confidence : null,
      defensible: found ? found.defensible : [],
      note: found ? found.note || '' : 'not answered',
    }
  })
}

function leftovers(questions, solved) {
  if (!solved) return [{ problem: 'confirm solver failed' }]
  const problems = comparison(questions, solved).filter(row => row.solver_choice !== row.key || row.defensible.length > 0)
  if (solved.issues.length > 0) problems.push({ passage_issues: solved.issues })
  if (solved.too_easy.length > 0) problems.push({ too_easy: solved.too_easy })
  return problems
}

async function runStage(st) {
  const written = await agent(writerPrompt(st), { label: st.dir + ':author', phase: '출제', schema: WRITTEN })
  if (!written) return { stage: st.dir, failed: 'write' }
  const solved = await agent(solverPrompt(st, strip(written.questions), 1), { label: st.dir + ':blind', phase: '블라인드', schema: SOLVED })
  const first = comparison(written.questions, solved)
  const revised = await agent(reviserPrompt(st, { comparison: first, solver: solved }, 1), { label: st.dir + ':revise', phase: '수정', schema: REVISED })
  const current = revised && revised.questions.length > 0 ? revised.questions : written.questions
  const confirm = await agent(solverPrompt(st, strip(current), 2), { label: st.dir + ':confirm', phase: '수정', schema: SOLVED })
  const left = leftovers(current, confirm)
  let finalFix = null
  if (left.length > 0) {
    finalFix = await agent(reviserPrompt(st, { problems: left, solver: confirm }, 2), { label: st.dir + ':final', phase: '수정', schema: REVISED })
  }
  const agreeFirst = first.filter(row => row.solver_choice === row.key).length
  log(st.dir + ': 첫 블라인드 일치 ' + agreeFirst + '/' + written.questions.length + ', 재풀이 남은 문제 ' + left.length)
  return {
    stage: st.dir,
    passage_chars: written.passage_chars,
    facts: written.facts_to_check,
    first_agreement: agreeFirst + '/' + written.questions.length,
    revise_changes: revised ? revised.changes : null,
    facts_checked: revised ? revised.facts_checked : null,
    confirm: confirm ? comparison(current, confirm) : null,
    leftovers: left,
    final_changes: finalFix ? finalFix.changes : null,
    remaining: finalFix ? finalFix.remaining_concerns : (revised ? revised.remaining_concerns : []),
    difficulty: confirm ? confirm.difficulty : (solved ? solved.difficulty : null),
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
