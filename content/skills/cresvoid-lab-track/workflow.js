export const meta = {
  name: 'cresvoid-lab-track',
  description: 'cresvoid lab 단계를 출제하고 독립 퀴즈, 블라인드 구현, 실험 교차 검증으로 확인한다',
  whenToUse: 'cresvoid lab 트랙에 단계를 만들거나 더할 때. root, track, brief, stages 를 넘긴다.',
  phases: [
    { title: '출제', detail: '스펙, 서비스 시작 코드, 참조 풀이, 실험과 실패 변형' },
    { title: '독립 검증', detail: '스펙만 읽는 퀴즈 작성과 숨은 파일을 보지 않는 블라인드 구현' },
    { title: '교차 검증', detail: '엔진 검증과 블라인드 구현의 시나리오 실행' },
    { title: '검토와 수정', detail: '학습자 관점 검토, 지적 반영, 재확인' },
  ],
}

if (!args || typeof args !== 'object') throw new Error('args 객체가 필요하다')
if (typeof args.root !== 'string' || !/^\/[^\r\n]+$/.test(args.root)) throw new Error('root 는 절대 경로여야 한다')
if (typeof args.track !== 'string' || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(args.track)) throw new Error('track 은 영문 kebab-case 여야 한다')
if (typeof args.brief !== 'string' || !args.brief.trim()) throw new Error('brief 가 필요하다')
if (!Array.isArray(args.stages) || args.stages.length === 0) throw new Error('stages 가 비어 있다')
if (args.lanes !== undefined && args.lanes !== 1 && args.lanes !== 2) throw new Error('lanes 는 1 또는 2여야 한다')
const stageNames = new Set()
const stageNumbers = new Set()
for (const stage of args.stages) {
  if (!stage || typeof stage.dir !== 'string' || !/^(?:0[1-9]|[1-9][0-9])-[a-z0-9]+(?:-[a-z0-9]+)*$/.test(stage.dir)) {
    throw new Error('stages[].dir 은 두 자리 번호-영문 slug 형식이어야 한다')
  }
  if (typeof stage.title !== 'string' || !stage.title.trim()) throw new Error(stage.dir + ': title 이 필요하다')
  if (stageNames.has(stage.dir)) throw new Error('단계 디렉터리가 겹친다: ' + stage.dir)
  const number = stage.dir.slice(0, 2)
  if (stageNumbers.has(number)) throw new Error('단계 번호가 겹친다: ' + number)
  stageNames.add(stage.dir)
  stageNumbers.add(number)
}

const ROOT = args.root.replace(/\/+$/, '')
const TRACK = args.track
const LANES = args.lanes ?? 1
const TRACK_DIR = ROOT + '/content/tracks/' + TRACK
const BRIEF = args.brief.trim()
const STRINGS = { type: 'array', items: { type: 'string' } }
const FINDING = {
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
}
const FINDINGS = { type: 'array', items: FINDING }
const AUTHOR = {
  type: 'object',
  properties: { files: STRINGS, decisions: STRINGS, scenarios: STRINGS, expected_failures: STRINGS, checks: STRINGS },
  required: ['files', 'decisions', 'scenarios', 'expected_failures', 'checks'],
}
const QUIZ = {
  type: 'object',
  properties: { files: STRINGS, independent_checks: STRINGS, concerns: STRINGS },
  required: ['files', 'independent_checks', 'concerns'],
}
const BLIND = {
  type: 'object',
  properties: { workspace: { type: 'string' }, changes: STRINGS, observations: STRINGS, issues: STRINGS, too_easy: STRINGS },
  required: ['workspace', 'changes', 'observations', 'issues', 'too_easy'],
}
const CROSS = {
  type: 'object',
  properties: {
    checks: STRINGS,
    blind_passed: { type: 'boolean' },
    findings: FINDINGS,
    public_changes: STRINGS,
    final_status: { type: 'string', enum: ['clean', 'remaining'] },
    remaining: STRINGS,
  },
  required: ['checks', 'blind_passed', 'findings', 'public_changes', 'final_status', 'remaining'],
}
const REVIEW = {
  type: 'object',
  properties: { findings: FINDINGS, difficulty_verdict: { type: 'string' }, learner_flow: { type: 'string' } },
  required: ['findings', 'difficulty_verdict', 'learner_flow'],
}
const FIX = {
  type: 'object',
  properties: { changes: STRINGS, rejected: STRINGS, public_changes: STRINGS, checks: STRINGS, remaining: STRINGS },
  required: ['changes', 'rejected', 'public_changes', 'checks', 'remaining'],
}

const COMMON = [
  '이 작업은 개인 학습 프로젝트 ' + ROOT + ' 에서 한다. ' + ROOT + '/AGENTS.md, ' + ROOT + '/docs/agents.md, ' + ROOT + '/content/FORMAT.md 를 읽는다.',
  '지정한 단계 외의 파일과 track.json 은 고치지 않는다. git 커밋과 외부 기록은 하지 않는다. practice/ 도 고치지 않는다.',
  '제품 코드, 참조 풀이, 검사 스크립트는 Kotlin 이다. JavaScript 는 제작 워크플로우 도구에만 허용된다.',
  'Kotlin 코드를 쓰기 전에 설치된 kotlin-core-best-practices 스킬과 세션 코드 규칙을 읽는다.',
  '한국어 문서는 해라체로 쓰고 초안 완성 후 korean-writing-guide 를 정확히 한 번 적용한다.',
  '새 의존성은 요구하거나 설치하지 않는다. JDK 21과 기존 Kotlin 템플릿, 이미 승인된 kotlinx.coroutines만 사용한다.',
].join('\n')

function stageDir(stage) { return TRACK_DIR + '/stages/' + stage.dir }

function authorPrompt(stage) {
  const dir = stageDir(stage)
  return `${COMMON}

너는 lab ${TRACK}/${stage.dir} 출제자다. 이 단계의 아래 파일만 만든다.
- ${dir}/spec.md: 번호 문단으로 관찰 절차, 공개 서비스 계약, 부하, 지표, 합격 불변식, 수정 범위를 정한다.
- ${dir}/starter/: 빌드되지만 불변식에 실패하는 Kotlin 프로젝트.
- ${dir}/hidden/solution/: 스펙을 만족하는 Kotlin 프로젝트.
- ${dir}/hidden/lab.json: scenarios 와 must_fail 목록.
- ${dir}/hidden/experiment.main.kts: HTTP 서비스 시작·준비 확인·부하·통계·종료를 소유하는 실험기.
- ${dir}/hidden/variants/<slug>/: 그럴듯하지만 다른 불변식을 놓치는 실패 변형 하나 이상.
hidden/quiz.json 은 독립 작성자가 맡는다. hidden/tests.json 은 만들지 않는다.

[설계 요약]
${BRIEF}
[단계]
${stage.title}

lab.json 의 시나리오에는 고유한 영문 kebab-case name, 스펙 번호 문단 refs, refs에 실제 들어 있는 quote, 양수 timeout_sec 와 repeat 를 적는다. must_fail 에는 variants/<slug> 를 적는다. 시나리오 이름은 카드의 정답을 누설하지 않는다.
실험기는 hidden/ 을 작업 디렉터리로 실행하며 인자 두 개(프로그램 절대 경로, 시나리오 이름)를 받는다. 표준 출력에는 METRIC <이름> <유한한 숫자> 형식만 최대 10줄 출력한다. 종료 코드 0은 모든 불변식 통과, 1은 불변식 실패, 그 밖은 실험 오류다. 메트릭 이름은 서로 달라야 한다.
run.sh 는 PORT 환경 변수의 루프백 포트에서 서비스를 띄우고 build.sh 는 실행 파일을 만든다. 둘 다 실행 가능해야 한다. 실험기는 빈 포트를 고르고 서버 준비를 기다리며 예정된 시각에 요청을 보낸다. finally 에서 서버를 종료한다. 지연 시간 기준은 환경 차이를 고려해 정하되 서비스가 잘못되어도 통과할 만큼 느슨하게 만들지 않는다.
시작 코드와 실패 변형은 정상 빌드되고 실험기가 지표를 낸 뒤 종료 코드 1로 실패해야 한다. 참조 풀이는 각 시나리오를 세 번 실행해 통과해야 한다. 실패 변형도 매 차수 적어도 하나의 불변식에 실패해야 한다. 실패한 빌드, 시간 초과, 누락된 지표를 의도된 실패로 세지 않는다.
현재 템플릿이 필요한 Kotlin 공통 파일을 제공하면 소스만 작성하고 kotlin ${ROOT}/content/templates/sync.main.kts 로 동기화한다. 공식 API 근거를 스펙에 적고, 예시와 지표를 실행으로 확인한다.
결과의 checks 에 실제 실행한 명령과 통과/실패 결과를 적는다.`
}

function quizPrompt(stage) {
  const dir = stageDir(stage)
  return `${COMMON}

너는 독립 퀴즈 작성자다. ${dir}/spec.md 만 읽고 ${dir}/hidden/quiz.json 만 만든다. hidden/ 의 기존 파일, starter/, 참조 풀이, 실험 스크립트는 열지 않는다. 스펙만으로 정답을 유도한다.
학습자가 실험을 보기 전에 예측할 수 있는 choice·short 문항을 만든다. choice 는 선지 2~5개와 정답 하나가 있어야 한다. short 는 스펙에서 답을 결정할 수 있게 쓴다. 모든 문항에 refs, 정확한 quote, explanation 을 붙인다. 답을 그대로 되풀이하는 선지를 피하고 부하·지표·불변식의 함정을 확인한다.
결과의 independent_checks 에 스펙 문장만으로 정답을 확인한 근거를 적는다.`
}

function blindPrompt(stage, round) {
  const dir = stageDir(stage)
  const workspace = '/tmp/cresvoid-lab-blind/' + TRACK + '/' + stage.dir + '-r' + round
  return `${COMMON}

너는 이 실험을 처음 보는 학습자다. ${dir}/spec.md 와 ${dir}/starter/ 만 읽는다. ${dir}/hidden/ 디렉터리나 기존 워크플로우 결과는 열지 않는다. ${dir}/starter/ 를 ${workspace} 에 복사하고 복사본만 고친다. 프로젝트 파일은 고치지 않는다.
스펙을 읽고 실행 전 예상 지표를 적는다. 시작 코드를 로컬에서 빌드·실행해 관찰한 다음, 스펙의 수정 범위 안에서 불변식을 만족하게 고친다. 자체 HTTP 요청과 경계 사례로 확인한다. 숨은 실험기를 보거나 실행하지 않는다.
결과의 workspace 에 절대 경로를 적고, observations 에 예상과 실제의 차이를 적는다. 스펙의 빈틈은 issues, 즉시 답이 드러나는 대목은 too_easy 에 적는다.`
}

function crossPrompt(stage, blind, round) {
  const dir = stageDir(stage)
  return `${COMMON}

너는 교차 검증자다. ${dir} 의 스펙, 퀴즈, 참조 풀이, 시작 코드, lab.json, 실험기와 실패 변형을 읽는다. 이 단계 파일은 스펙 기준으로 고칠 수 있다. 블라인드 구현은 ${blind.workspace} 에 있다. 그 복사본은 고치지 않는다.
1. engine/bin/cresvoid validate ${TRACK} 를 실행한다. 다른 단계가 동시에 제작 중이면 이 단계와 관련된 오류를 따로 판단한다.
2. 검증기가 참조 풀이를 각 시나리오 3회 통과시키고, 시작 코드와 모든 must_fail 변형이 3회 모두 불변식에서 실패하는지 확인한다. 종료 코드 2 이상, 빌드 실패, 시간 초과, 지표 출력 오류는 실패 변형의 성공적인 적발이 아니다.
3. 블라인드 구현의 build.sh 를 실행하고 hidden/ 을 작업 디렉터리로 삼아 experiment.main.kts 를 블라인드 절대 경로와 각 시나리오 이름으로 호출한다. 블라인드 구현자가 hidden/ 을 보지 않은 상태를 유지한다. 퀴즈 완료 기록이 필요한 cresvoid test 를 블라인드 agent 에게 실행시키지 않는다.
4. 스펙, 퀴즈, 실험기의 결론이 같은지 검토한다. 부하 일정, 메트릭, 불변식, refs·quote, 서버 정리, 허용된 수정 범위를 확인한다. 문서나 실험이 틀렸으면 스펙을 기준으로 고친 뒤 validate 와 블라인드 실험을 다시 실행한다. 검사를 지워 불일치를 숨기지 않는다.
5. 바뀐 spec.md 또는 starter/ 파일은 public_changes 에 적는다. 바뀌면 별도 블라인드 구현으로 다시 확인해야 한다. 남은 문제를 숨기지 말고 final_status 와 remaining 에 적는다.

검증 차수: ${round}. 블라인드 결과(JSON):
${JSON.stringify(blind, null, 2)}`
}

function reviewPrompt(stage, blind, cross) {
  const dir = stageDir(stage)
  return `${COMMON}

너는 학습자 관점 검토자다. 파일은 고치지 않는다. ${dir}/spec.md, ${dir}/starter/, ${dir}/hidden/quiz.json 만 읽는다. 참조 풀이, lab.json, 실험 스크립트, 실패 변형은 열지 않는다.
스펙과 시작 코드만으로 해야 할 일이 분명한가? 관찰 전 예측 문항이 실제로 추론을 요구하는가? 테스트 지표와 실패 메시지에서 원인을 추적할 수 있는가? 숙련 Kotlin 개발자에게 적절한 난도인가? 시작 코드의 이름·주석이 답을 누설하지 않는가? 지적은 파일, 위치, 심각도, 근거, 제안과 함께 적는다.

블라인드 구현자의 문제 제기(JSON):
${JSON.stringify({ issues: blind.issues, too_easy: blind.too_easy }, null, 2)}
교차 검증 상태(JSON):
${JSON.stringify({ blind_passed: cross.blind_passed, final_status: cross.final_status, remaining: cross.remaining }, null, 2)}`
}

function fixPrompt(stage, cross, review) {
  const dir = stageDir(stage)
  return `${COMMON}

너는 lab ${TRACK}/${stage.dir} 수정 담당이다. 이 단계의 모든 파일을 읽고 교차 검증과 학습자 검토의 지적을 스펙 및 실행으로 판단한다. must·should 지적을 고치고, 틀린 지적은 근거를 rejected 에 적는다. 테스트나 문항을 지워서 불일치를 감추지 않는다.
고친 뒤 engine/bin/cresvoid validate ${TRACK} 를 실행한다. spec.md 또는 starter/ 를 바꿨으면 public_changes 에 적는다. 새 블라인드 구현이 필요한 변경을 빠뜨리지 않는다. 실험의 서버 종료와 실패 변형 검증까지 확인한다.

교차 검증(JSON):
${JSON.stringify(cross, null, 2)}

학습자 검토(JSON):
${JSON.stringify(review, null, 2)}`
}

function needsFix(cross, review) {
  return cross.final_status !== 'clean' || !cross.blind_passed ||
    review.findings.some(finding => finding.severity === 'must' || finding.severity === 'should')
}

async function runStage(stage) {
  const label = stage.dir
  phase(label + ' 출제')
  const author = await agent(authorPrompt(stage), { label: label + ':author', phase: '출제', schema: AUTHOR })
  if (!author) return { stage: label, failed: 'author' }

  phase(label + ' 독립 검증')
  const quiz = await agent(quizPrompt(stage), { label: label + ':quiz', phase: '독립 검증', schema: QUIZ })
  if (!quiz) return { stage: label, author, failed: 'quiz' }
  let blind = await agent(blindPrompt(stage, 1), { label: label + ':blind-1', phase: '독립 검증', schema: BLIND })
  if (!blind) return { stage: label, author, quiz, failed: 'blind' }

  phase(label + ' 교차 검증')
  let cross = await agent(crossPrompt(stage, blind, 1), { label: label + ':cross-1', phase: '교차 검증', schema: CROSS })
  if (!cross) return { stage: label, author, quiz, blind, failed: 'cross' }
  if (cross.public_changes.length > 0) {
    blind = await agent(blindPrompt(stage, 2), { label: label + ':blind-2', phase: '교차 검증', schema: BLIND })
    if (!blind) return { stage: label, author, quiz, cross, failed: 'reblind' }
    cross = await agent(crossPrompt(stage, blind, 2), { label: label + ':cross-2', phase: '교차 검증', schema: CROSS })
    if (!cross) return { stage: label, author, quiz, blind, failed: 'recross' }
  }

  phase(label + ' 검토와 수정')
  const review = await agent(reviewPrompt(stage, blind, cross), { label: label + ':review', phase: '검토와 수정', schema: REVIEW })
  if (!review) return { stage: label, author, quiz, blind, cross, failed: 'review' }
  let fix = null
  let confirm = null
  if (needsFix(cross, review)) {
    fix = await agent(fixPrompt(stage, cross, review), { label: label + ':fix', phase: '검토와 수정', schema: FIX })
    if (!fix) return { stage: label, author, quiz, blind, cross, review, failed: 'fix' }
    if (fix.public_changes.length > 0) {
      blind = await agent(blindPrompt(stage, 3), { label: label + ':blind-3', phase: '검토와 수정', schema: BLIND })
      if (!blind) return { stage: label, author, quiz, cross, review, fix, failed: 'final-blind' }
    }
    confirm = await agent(crossPrompt(stage, blind, 3), { label: label + ':confirm', phase: '검토와 수정', schema: CROSS })
    if (!confirm) return { stage: label, author, quiz, blind, cross, review, fix, failed: 'confirm' }
  }
  const latest = confirm ?? cross
  const remaining = [...latest.remaining, ...(fix ? fix.remaining : [])]
  if (latest.public_changes.length > 0) remaining.push('마지막 공개 변경 뒤 새 블라인드 구현 검증이 필요하다')
  if (!latest.blind_passed) remaining.push('블라인드 구현이 실험을 통과하지 못했다')
  if (latest.final_status !== 'clean') remaining.push('교차 검증이 완료되지 않았다')
  log(label + ': ' + (remaining.length === 0 ? '검증 완료' : '남은 문제 ' + remaining.length + '건'))
  return { stage: label, author, quiz, blind, cross, review, fix, confirm, remaining }
}

const lanes = Array.from({ length: LANES }, () => [])
args.stages.forEach((stage, index) => lanes[index % LANES].push(stage))
async function runLane(stages) {
  const results = []
  for (const stage of stages) results.push(await runStage(stage))
  return results
}
const results = LANES === 1
  ? await runLane(lanes[0])
  : (await parallel(lanes.filter(lane => lane.length > 0).map(lane => () => runLane(lane)))).filter(Boolean).flat()
return { track: TRACK, stages: results }
