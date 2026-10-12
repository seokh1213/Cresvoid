export const meta = {
  name: 'cresvoid-fix-track',
  description: 'cresvoid fix 트랙(버그 수정) 단계를 만들고 블라인드 수정, antislop 검토, 수정, 재확인으로 검증한다',
  whenToUse: 'cresvoid fix 트랙에 단계를 새로 만들거나 늘릴 때. args 에 root, track, stages 를 넘긴다.',
  phases: [
    { title: '출제', detail: '스펙, 참조 풀이, 버그를 심은 시작 코드, 테스트 작성' },
    { title: '블라인드', detail: '스펙과 시작 코드만 보고 버그를 찾아 고친 뒤 숨은 테스트로 확인' },
    { title: '검토', detail: 'antislop 기준과 학습자 관점 검토' },
    { title: '수정', detail: '지적 반영과 재확인' },
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
  'Kotlin 코드를 쓰기 전에 세션의 코드 규칙과 설치된 kotlin-core-best-practices 스킬 를 읽고 따른다.',
  '이 프롬프트가 지정한 파일만 만들거나 고친다. 다른 agent 가 같은 시간에 다른 단계를 만든다. track.json 은 고치지 않는다.',
  '학습자 공간과 진행 기록(' + ROOT + '/practice)을 바꾸지 않는다. cresvoid 로 흐름을 시험할 때는 프로젝트를 /tmp 에 복사해 그 안에서 실행한다. cresvoid validate 는 상태를 바꾸지 않으므로 프로젝트에서 실행해도 된다.',
].join('\n')

const WRITING = [
  '한국어 문서 본문은 해라체 평서형으로 쓴다.',
  '한국어 초안을 완성한 뒤 설치된 korean-writing-guide 스킬의 SKILL.md 를 읽고 정확히 한 번 적용한다. 문장의 명확성도 같은 스킬로 확인한다.',
].join('\n')

const LEARNER = [
  '학습자는 Kotlin 백엔드 경력이 있는 개발자다. 쇼핑 카탈로그 서비스를 만든다.',
  '학습자는 AI 없이 낯선 코드를 읽고 버그를 재현하고 원인을 찾는 힘을 되찾으려 한다. 쉬운 과제를 원하지 않는다.',
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

function stageNumber(st) {
  return Number(st.dir.split('-')[0])
}

function authorPrompt(st) {
  const dir = stageDir(st)
  return `${COMMON}
${WRITING}
${LEARNER}
${ANTISLOP}

너는 fix 트랙(버그 수정) ${TRACK} 의 ${st.dir} 단계 출제자다.
만들 파일:
- ${dir}/spec.md
- ${dir}/hidden/solution/ : 스펙대로 올바르게 동작하는 Kotlin 프로젝트(FORMAT.md 7절). src/main/kotlin 밖의 파일(build.sh, run.sh, build.gradle.kts, settings.gradle.kts, .java-version)은 직접 쓰지 않는다. 소스를 쓴 뒤 kotlin ${ROOT}/content/templates/sync.main.kts 를 실행해 ${ROOT}/content/templates/kotlin 의 파일을 복사한다.
- ${dir}/starter/ : hidden/solution 을 복사한 뒤 버그 하나를 심은 프로젝트
- ${dir}/hidden/tests.json
- 필요하면 ${dir}/hidden/gen.main.kts

[단계 설계]
- 제목: ${st.title}
- 맥락: ${st.context}
- 버그 후보: ${st.bugs}
- 코드 규모: ${st.size}
- 난도: ${st.level}
${cardRules(st)}
${LIBS}

[순서]
1. spec.md 의 앞부분을 쓴다. 제품 맥락, 명령과 입출력 형식, 의도한 동작 규칙을 번호 문단으로 쓴다. 프로그램은 표준 입력에서 명령을 한 줄씩 EOF 까지 읽고 결과를 표준 출력에 쓴다. 시각이 필요하면 입력 명령으로 움직이는 가짜 시계를 쓴다.
2. hidden/solution/ 을 스펙대로 구현한다. 학습자가 cresvoid solution 으로 읽는 모범 코드다. 도메인의 말로 이름을 짓고 책임별로 파일을 나눈다.
3. 버그 후보 가운데 하나를 고른다. 이 맥락에서 실제로 생길 법한 것을 고르고 고른 이유를 rationale 에 적는다. starter/ 는 hidden/solution/ 을 복사한 뒤 그 버그만 심는다. 버그는 실무에서 그렇게 쓰기 쉬운 모양이어야 한다. 주석이나 이름으로 버그 자리를 암시하지 않는다.
4. spec.md 끝에 버그 신고 절을 번호 문단으로 쓴다. 신고자의 말투로 증상, 재현 입력, 기대한 결과, 실제 결과를 적는다. 원인과 버그 위치는 적지 않는다. 재현 입력의 결과는 starter 와 solution 을 실행해 확인한 값이다.
5. hidden/tests.json 을 쓴다. 고정 테스트 6~10개다. expected 는 스펙에서 손으로 유도한 뒤 solution 실행으로 확인한다. 버그를 드러내는 테스트 2개 이상과 버그 근처의 올바른 동작을 지키는 회귀 테스트를 함께 둔다. 테스트는 스펙의 동작만 확인한다. 같은 버그를 다른 방식으로 고쳐도 통과해야 한다. 테스트 이름에는 숨은 케이스의 조건이나 답을 담지 않는다. 엔진은 통과하거나 실패한 테스트 이름을 학습자에게 보여 준다.
6. ${ROOT}/engine/bin/cresvoid validate ${TRACK} 를 실행해 이 단계에 관한 오류가 없게 한다. starter 가 실패하는 테스트 이름과 solution 과의 바뀐 줄 수를 기록한다. 다른 단계의 오류는 다른 agent 가 고친다.
7. 최종 출력 rationale 에 버그를 고른 이유, 학습자가 원인에 닿는 추론 경로(어떤 증상에서 어떤 코드를 의심하게 되는가), 테스트마다 확인하는 규칙을 적는다.
8. 최종 출력 gate 에 Delivery Gate 를 PASS/FAIL 과 증거로 적는다. 주석 위생, 이름, 실무다움, 버그가 하나뿐인지, 버그 신고를 실행으로 확인했는지, 지어낸 API 없음을 포함한다.`
}

function blindFixPrompt(st, round) {
  const dir = stageDir(st)
  const work = '/tmp/cresvoid-blind-fix/' + TRACK + '/' + st.dir + (round > 1 ? '-r' + round : '')
  return `${COMMON}

너는 처음 보는 코드의 버그를 고치는 개발자다.
${dir}/spec.md 와 ${dir}/starter/ 만 읽는다. ${dir}/hidden/ 디렉터리는 열지 않는다. 정답이 들어 있다.
${dir}/starter 를 ${work} 로 복사하고 그 안에서만 고친다. 프로젝트 파일은 고치지 않는다.

1. 스펙의 버그 신고에 적힌 재현 입력으로 증상을 재현한다.
2. 원인을 찾아 고친다. 세운 가설과 확인한 순서를 hypotheses 에 적는다.
3. 고친 뒤 재현 입력과 스스로 만든 경계 입력으로 스펙 규칙을 확인한다.
4. 원인, 고친 파일과 줄, 바뀐 줄 수, 걸린 시간을 적는다.
5. 버그 신고가 모호하거나 다른 원인을 의심하게 만드는 곳, 원인이 둘 이상으로 보이는 곳은 issues 에 적는다.
6. 신고만 보고 버그 위치를 바로 알 수 있었으면 too_easy 에 적는다.
7. 코드가 실무 코드답지 않은 곳(일부러 꼬은 구조, 뜻 없는 이름, 장식 주석, 버그 자리를 암시하는 이름이나 주석)도 issues 에 적는다.
최종 출력 workspace 에 고친 작업 공간 경로(${work})를 적는다.
${round > 1 ? '이 단계는 한 차례 수정을 거쳤다. 남은 문제만 엄격하게 찾는다.' : ''}`
}

function auditPrompt(st, written, fixed) {
  const dir = stageDir(st)
  return `${COMMON}
${LEARNER}
${ANTISLOP}

너는 fix 트랙 ${st.dir} 단계의 검토자다. 프로젝트 파일은 고치지 않는다. 검토 결과만 돌려준다.
읽을 것: ${dir}/spec.md, ${dir}/hidden/solution/, ${dir}/starter/, ${dir}/hidden/tests.json, 아래 출제자 보고와 블라인드 수정 결과.
${cardRules(st) ? "지켰는지 볼 카드 기준:\n" + cardRules(st) : ""}

1. 블라인드 수정본을 숨은 테스트로 확인한다. ${ROOT} 를 /tmp/cresvoid-audit-${TRACK}-${st.dir} 로 복사한다. 그 사본에서 engine/bin/cresvoid start ${TRACK} -stage ${stageNumber(st)} -dir <블라인드 작업 공간 경로> 로 작업 공간을 고른다. 그다음 engine/bin/cresvoid test ${TRACK} -stage ${stageNumber(st)} 를 실행한다. 결과를 blind_test 에 적는다. 실패했으면 원인이 블라인드 수정의 실수인지, 테스트가 다른 올바른 수정을 거절하는 것인지, 버그 신고가 오해를 부른 것인지 판정한다.
2. antislop: antislop-code 점검표, 코드의 실무다움, 이름, 구조, 버그 자리를 암시하는 흔적, 결정마다 이유가 있는지, 주장마다 실행 증거가 있는지.
3. 버그: 원인이 하나뿐인가. starter 와 solution 의 차이가 버그 자리뿐인가. 실무에서 생길 법한가. 난도가 학습자 수준에 맞는가.
4. 스펙과 버그 신고: 의도한 동작이 번호 문단으로 빠짐없이 정해졌는가. 신고가 원인을 누설하지 않으면서 재현에 충분한가. 한국어가 정확한가.
5. 테스트: 버그를 잡는가. 회귀 테스트가 버그 근처의 올바른 동작을 지키는가. 구현 세부에 기대지 않는가. refs 와 quote 가 맞는가. 테스트 이름이 숨은 케이스의 조건이나 답을 드러내지 않는가.
6. Delivery Gate 를 다시 점검하고 PASS/FAIL 과 증거를 적는다.

지적마다 파일, 위치, 심각도(must, should, nit), 문제, 근거, 제안을 적는다.

출제자 보고(JSON):
${JSON.stringify(written, null, 2)}

블라인드 수정 결과(JSON):
${JSON.stringify(fixed, null, 2)}`
}

function revisePrompt(st, payload, round) {
  const dir = stageDir(st)
  return `${COMMON}
${WRITING}
${ANTISLOP}

너는 fix 트랙 ${st.dir} 단계의 수정 담당이다.
${dir}/spec.md, hidden/solution/, starter/, hidden/tests.json 을 읽고 아래 지적과 블라인드 결과를 반영한다.
1. 지적마다 코드와 실행으로 타당성을 검증한다. 타당한 지적만 고치고 나머지는 이유와 함께 rejected 에 적는다.
2. 블라인드 수정본이 숨은 테스트에서 실패했고 원인이 테스트나 신고에 있으면 고친다. 다른 올바른 수정을 거절하는 테스트는 스펙의 동작만 보도록 바꾼다.
3. 버그를 바꾸거나 starter 를 고쳤으면 starter 와 solution 의 차이가 버그 자리뿐인지 다시 확인하고 버그 신고의 결과를 다시 실행해 맞춘다.
4. ${ROOT}/engine/bin/cresvoid validate ${TRACK} 에서 이 단계 오류가 없게 한다.
5. bug_changed 에 버그나 버그 신고를 바꿨는지 적는다. gate 에 Delivery Gate 를 다시 적는다.
${round > 1 ? '이번은 마지막 수정이다. 남은 문제를 모두 해결한다.' : ''}

지적과 블라인드 결과(JSON):
${JSON.stringify(payload, null, 2)}`
}

const STR_ARRAY = { type: 'array', items: { type: 'string' } }

const WRITTEN = {
  type: 'object',
  properties: {
    files: STR_ARRAY,
    bug: { type: 'string' },
    code_lines: { type: 'integer' },
    changed_lines: { type: 'integer' },
    failing_tests: STR_ARRAY,
    rationale: STR_ARRAY,
    gate: STR_ARRAY,
    validate: { type: 'string' },
  },
  required: ['files', 'bug', 'failing_tests', 'rationale', 'gate', 'validate'],
}

const FIXED = {
  type: 'object',
  properties: {
    workspace: { type: 'string' },
    cause: { type: 'string' },
    changes: STR_ARRAY,
    changed_lines: { type: 'integer' },
    hypotheses: STR_ARRAY,
    minutes: { type: 'integer' },
    issues: STR_ARRAY,
    too_easy: STR_ARRAY,
  },
  required: ['workspace', 'cause', 'changes', 'hypotheses', 'issues', 'too_easy'],
}

const FINDINGS = {
  type: 'object',
  properties: {
    blind_test: { type: 'string' },
    blind_passed: { type: 'boolean' },
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
  required: ['blind_test', 'blind_passed', 'findings', 'gate', 'verdict'],
}

const REVISED = {
  type: 'object',
  properties: {
    changes: STR_ARRAY,
    rejected: STR_ARRAY,
    bug_changed: { type: 'boolean' },
    gate: STR_ARRAY,
    validate: { type: 'string' },
    remaining_concerns: STR_ARRAY,
  },
  required: ['changes', 'rejected', 'bug_changed', 'gate', 'validate', 'remaining_concerns'],
}

function needsConfirm(audit, revised) {
  if (!audit || !revised) return true
  if (!audit.blind_passed) return true
  if (revised.bug_changed) return true
  return audit.findings.some(f => f.severity === 'must')
}

async function runStage(st) {
  const written = await agent(authorPrompt(st), { label: st.dir + ':author', phase: '출제', schema: WRITTEN })
  if (!written) return { stage: st.dir, failed: 'author' }
  const fixed = await agent(blindFixPrompt(st, 1), { label: st.dir + ':blind-fix', phase: '블라인드', schema: FIXED })
  const audit = await agent(auditPrompt(st, written, fixed), { label: st.dir + ':audit', phase: '검토', schema: FINDINGS })
  const revised = await agent(revisePrompt(st, { blind: fixed, audit }, 1), { label: st.dir + ':revise', phase: '수정', schema: REVISED })
  let confirm = null
  let confirmAudit = null
  let finalFix = null
  if (needsConfirm(audit, revised)) {
    confirm = await agent(blindFixPrompt(st, 2), { label: st.dir + ':confirm-fix', phase: '수정', schema: FIXED })
    confirmAudit = await agent(auditPrompt(st, { written, revised }, confirm), { label: st.dir + ':confirm-audit', phase: '수정', schema: FINDINGS })
    if (confirmAudit && (!confirmAudit.blind_passed || confirmAudit.findings.some(f => f.severity === 'must'))) {
      finalFix = await agent(revisePrompt(st, { blind: confirm, audit: confirmAudit }, 2), { label: st.dir + ':final', phase: '수정', schema: REVISED })
    }
  }
  log(st.dir + ': 블라인드 수정 ' + (audit && audit.blind_passed ? '통과' : '실패') + (confirmAudit ? ', 재확인 ' + (confirmAudit.blind_passed ? '통과' : '실패') : ''))
  return {
    stage: st.dir,
    bug: written.bug,
    rationale: written.rationale,
    blind_cause: fixed ? fixed.cause : null,
    blind_passed: audit ? audit.blind_passed : null,
    audit_findings: audit ? audit.findings : null,
    revise_changes: revised ? revised.changes : null,
    revise_rejected: revised ? revised.rejected : null,
    confirm_passed: confirmAudit ? confirmAudit.blind_passed : null,
    confirm_findings: confirmAudit ? confirmAudit.findings : null,
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
