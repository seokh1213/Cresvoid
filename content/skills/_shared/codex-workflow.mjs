// Runs a cresvoid workflow.js with a fresh `codex exec` process for every agent() call.
// Each call keeps its prompt, live event stream, progress notes, and result under the run
// directory, so a person can follow a background run and a stopped run resumes from the calls
// that already finished (workflow-design.md, D7).
import { execFileSync, spawn } from 'node:child_process'
import { closeSync, existsSync, mkdirSync, openSync, readFileSync, readSync, appendFileSync, statSync, statfsSync, writeFileSync } from 'node:fs'
import { basename, dirname, join, resolve } from 'node:path'

const realNow = Date.now.bind(Date)
// Declared before the top-level run() below starts, so retries can read them.
const MAX_CAPACITY_WAITS = 6
const CAPACITY_WAIT_SEC = Number(process.env.CODEX_CAPACITY_WAIT_SEC ?? 60)
const CAPACITY_PATTERN = /at capacity|rate limit|too many requests|\b429\b|overloaded|usage limit/i

const STATE_NAMES = { waiting: '대기', running: '실행 중', done: '완료', cached: '완료(재사용)', failed: '실패', stopped: '멈춤' }

const [command, ...rest] = process.argv.slice(2)
if (command === 'status' && rest.length === 1) {
  printStatus(resolve(rest[0]))
} else if (command === 'run' && rest.length === 3) {
  await run(...rest)
} else {
  console.error(`usage:
  node codex-workflow.mjs run <workflow.js> <args.json> <run dir>
  node codex-workflow.mjs status <run dir>
env: CODEX_MAX (동시 실행 수, 기본 2), CODEX_MODEL, CODEX_EFFORT, CODEX_SANDBOX,
     CODEX_TIMEOUT_MIN (호출 하나의 한도, 기본 90), CODEX_NO_MCP=1 (설정된 MCP 서버를 끈다),
     CODEX_MIN_FREE_GB (이보다 디스크 여유가 적으면 새 호출을 띄우지 않고 기다린다, 기본 15)`)
  process.exit(2)
}

async function run(scriptPath, argsPath, runDirArg) {
  const runDir = resolve(runDirArg)
  const callsDir = join(runDir, 'calls')
  mkdirSync(callsDir, { recursive: true })
  const workflowArgs = JSON.parse(readFileSync(argsPath, 'utf8'))
  writeFileSync(join(runDir, 'args.json'), JSON.stringify(workflowArgs, null, 2) + '\n')
  const root = workflowArgs.root ?? process.cwd()
  const extraPath = join(runDir, 'extra.md')
  const extra = existsSync(extraPath) ? readFileSync(extraPath, 'utf8').trim() : ''
  const max = Number(process.env.CODEX_MAX ?? 2)
  const timeoutMs = Number(process.env.CODEX_TIMEOUT_MIN ?? 90) * 60_000
  const minFreeGb = Number(process.env.CODEX_MIN_FREE_GB ?? 15)
  const baseArgs = codexBaseArgs()

  const state = { workflow: resolve(scriptPath), pid: process.pid, max, started: realNow(), finished: null, calls: [] }
  const children = new Set()
  const runnerLog = message => appendFileSync(join(runDir, 'runner.log'), `${clock(realNow())} ${message}\n`)
  const saveStatus = () => {
    writeFileSync(join(runDir, 'status.json'), JSON.stringify(state, null, 2) + '\n')
    writeFileSync(join(runDir, 'status.md'), renderStatus(state))
  }
  const refresh = setInterval(saveStatus, 30_000)

  const stop = signal => {
    runnerLog(`${signal} 받음, 실행 중인 호출을 멈춘다`)
    for (const child of children) child.kill('SIGTERM')
    for (const call of state.calls) if (call.state === 'running' || call.state === 'waiting') call.state = 'stopped'
    saveStatus()
    process.exit(130)
  }
  process.on('SIGINT', () => stop('SIGINT'))
  process.on('SIGTERM', () => stop('SIGTERM'))

  let active = 0
  const waiters = []
  const acquire = async () => {
    if (active < max) { active++; return }
    await new Promise(resolveSlot => waiters.push(resolveSlot))
  }
  const release = () => {
    const next = waiters.shift()
    if (next) next()
    else active--
  }

  const seen = new Map()
  globalThis.agent = async (prompt, opts = {}) => {
    const base = opts.label ?? 'agent'
    const count = (seen.get(base) ?? 0) + 1
    seen.set(base, count)
    const label = count > 1 ? `${base}#${count}` : base
    const dir = join(callsDir, label.replace(/[^\w.#-]+/g, '.'))
    mkdirSync(join(dir, 'work'), { recursive: true })
    const call = { label, phase: opts.phase ?? null, dir, state: 'waiting', attempt: 0, started: null, ended: null, exit: null }
    state.calls.push(call)
    const cached = readResult(dir, opts.schema)
    if (cached !== undefined) {
      call.state = 'cached'
      runnerLog(`재사용 ${label}`)
      saveStatus()
      return cached
    }
    saveStatus()
    await acquire()
    try {
      let ordinaryFailures = 0
      let capacityWaits = 0
      for (let attempt = 1; ; attempt++) {
        const result = await runCall(call, prompt, opts.schema, attempt)
        if (result !== undefined) {
          writeFileSync(join(dir, 'result.json'), JSON.stringify(result, null, 2) + '\n')
          call.state = 'done'
          return result
        }
        // Model capacity and rate limits clear up on their own; an immediate retry just fails again.
        if (isCapacityFailure(dir, attempt) && capacityWaits < MAX_CAPACITY_WAITS) {
          const seconds = Math.min(600, CAPACITY_WAIT_SEC * 2 ** capacityWaits)
          capacityWaits++
          runnerLog(`모델 용량 부족, ${seconds}초 뒤 다시 시도 ${label}`)
          await new Promise(resume => setTimeout(resume, seconds * 1000))
          continue
        }
        ordinaryFailures++
        if (ordinaryFailures >= 2) break
      }
      call.state = 'failed'
      return null
    } finally {
      release()
      saveStatus()
    }
  }

  async function runCall(call, prompt, schema, attempt) {
    const { dir, label } = call
    const fullPrompt = [prompt, recordRules(dir, runDir), extra].filter(Boolean).join('\n\n')
    writeFileSync(join(dir, 'prompt.md'), fullPrompt + '\n')
    const lastMessage = join(dir, `last-message-${attempt}.txt`)
    const args = [...baseArgs, '-C', root, '--json', '-o', lastMessage]
    if (schema) {
      writeFileSync(join(dir, 'schema.json'), JSON.stringify(strictSchema(schema), null, 2) + '\n')
      args.push('--output-schema', join(dir, 'schema.json'))
    }
    args.push('-')
    await waitForDisk(label)
    const out = openSync(join(dir, `events-${attempt}.jsonl`), 'a')
    const err = openSync(join(dir, `stderr-${attempt}.log`), 'a')
    Object.assign(call, { state: 'running', attempt, started: realNow(), ended: null, exit: null })
    saveStatus()
    runnerLog(`시작 ${label} (시도 ${attempt})`)

    const child = spawn('codex', args, { cwd: root, stdio: ['pipe', out, err] })
    children.add(child)
    // A piped stdin that stays open makes `codex exec` wait for more input.
    child.stdin.end(fullPrompt)
    const timer = setTimeout(() => {
      runnerLog(`시간 초과 ${label}`)
      child.kill('SIGTERM')
      setTimeout(() => child.kill('SIGKILL'), 10_000).unref()
    }, timeoutMs)
    const exit = await new Promise(done => {
      child.on('error', error => { runnerLog(`실행 오류 ${label}: ${error.message}`); done(-1) })
      child.on('close', code => done(code ?? -1))
    })
    clearTimeout(timer)
    children.delete(child)
    closeSync(out)
    closeSync(err)
    Object.assign(call, { ended: realNow(), exit })

    const text = existsSync(lastMessage) ? readFileSync(lastMessage, 'utf8').trim() : ''
    const result = exit === 0 && text ? parseResult(text, schema) : undefined
    runnerLog(`${result === undefined ? '실패' : '끝'} ${label} (시도 ${attempt}, 종료 코드 ${exit}, ${duration(call.started, call.ended)})`)
    return result
  }

  // A full disk breaks every running call at once, so new calls wait until space comes back.
  async function waitForDisk(label) {
    let warned = false
    while (true) {
      const stats = statfsSync(root)
      const freeGb = (stats.bavail * stats.bsize) / 1024 ** 3
      if (freeGb >= minFreeGb) return
      if (!warned) runnerLog(`디스크 여유 ${freeGb.toFixed(1)}GB 라 ${label} 시작을 멈춘다. ${minFreeGb}GB 가 되면 이어 간다`)
      warned = true
      await new Promise(resume => setTimeout(resume, 60_000))
    }
  }

  Date.now = () => { throw new Error('Date.now() is not allowed in workflow scripts') }
  Math.random = () => { throw new Error('Math.random() is not allowed in workflow scripts') }
  const swallow = error => {
    runnerLog(`[error] ${error.message}`)
    return null
  }
  globalThis.args = workflowArgs
  globalThis.log = message => runnerLog(`[log] ${message}`)
  globalThis.phase = title => runnerLog(`[phase] ${title}`)
  globalThis.budget = { total: null, spent: () => 0, remaining: () => Infinity }
  globalThis.parallel = async thunks => Promise.all(thunks.map(thunk => thunk().catch(swallow)))
  globalThis.pipeline = async (items, ...stages) => Promise.all(items.map(async (item, index) => {
    let value = item
    for (const stage of stages) {
      try {
        value = await stage(value, item, index)
      } catch (error) {
        return swallow(error)
      }
    }
    return value
  }))

  runnerLog(`실행 시작 ${basename(dirname(state.workflow))}, 동시 실행 최대 ${max}`)
  saveStatus()
  const source = readFileSync(scriptPath, 'utf8').replace(/^export const meta/m, 'const meta')
  const result = await new Function(`return (async () => {\n${source}\n})()`)()
  writeFileSync(join(runDir, 'result.json'), JSON.stringify(result, null, 2) + '\n')
  state.finished = realNow()
  clearInterval(refresh)
  saveStatus()
  runnerLog('실행 끝, 결과는 result.json')
}

function isCapacityFailure(dir, attempt) {
  const events = join(dir, `events-${attempt}.jsonl`)
  const errors = join(dir, `stderr-${attempt}.log`)
  return [events, errors].some(file => existsSync(file) && CAPACITY_PATTERN.test(tail(file, 8192)))
}

function codexBaseArgs() {
  const args = ['exec']
  if (process.env.CODEX_MODEL) args.push('-m', process.env.CODEX_MODEL)
  if (process.env.CODEX_EFFORT) args.push('-c', `model_reasoning_effort="${process.env.CODEX_EFFORT}"`)
  if (process.env.CODEX_SANDBOX) args.push('-s', process.env.CODEX_SANDBOX)
  if (process.env.CODEX_NO_MCP === '1') {
    const servers = JSON.parse(execFileSync('codex', ['mcp', 'list', '--json'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }))
    // Overriding a server that is already off (some come from plugins) breaks config loading.
    for (const server of servers.filter(s => s.enabled)) args.push('-c', `mcp_servers.${server.name}.enabled=false`)
  }
  return args
}

function recordRules(dir, runDir) {
  return `[실행 기록]
이 호출을 실행하는 실행기가 기록 디렉터리 ${dir} 를 만들었다.
- 일하는 동안 ${dir}/progress.md 에 진행 상황을 한 줄씩 덧붙인다. 형식은 "- HH:MM 내용" 이고 시각은 date +%H:%M 으로 얻는다.
- 자료를 다 읽었을 때, 파일을 만들거나 고쳤을 때, 검증 명령을 실행했을 때(결과 포함), 막히거나 방향을 바꿀 때 적는다. 오래 걸리는 일 중에도 10분 안에 한 줄은 적는다.
- 명령 출력이나 계산처럼 나중에 확인할 중간 산출물은 ${dir}/work/ 에 파일로 남긴다.
- 끝내기 직전에 progress.md 에 "- HH:MM 완료: 한 줄 요약" 을 적는다.
- ${runDir} 안의 다른 호출 디렉터리는 열지 않는다. 다른 agent 의 결과와 정답이 들어 있다.
- 자원 회수: 이 호출이 /tmp 에 만든 프로젝트 사본과 임시 파일, 직접 띄운 Docker 컨테이너·볼륨·네트워크, 백그라운드로 띄운 프로세스는 최종 출력을 내기 전에 모두 지운다. 프로젝트 전체 사본은 1GB 가까이 되어 쌓이면 디스크가 찬다. 나중에 볼 파일은 ${dir}/work/ 에 남긴다. 다른 호출이 만든 것은 지우지 않는다.
- docs/handoff.md 와 docs/log 의 작업 기록 문서는 실행을 조율하는 세션이 맡는다. 이 호출의 기록 디렉터리 밖에는 기록을 남기지 않는다.`
}

// Structured output requires every property to be listed and no extra keys, so optional
// properties become nullable instead of being left out.
function strictSchema(schema) {
  if (!schema || typeof schema !== 'object') return schema
  if (schema.type === 'array') return { ...schema, items: strictSchema(schema.items) }
  if (schema.type !== 'object') return schema
  const required = new Set(schema.required ?? [])
  const properties = {}
  for (const [key, sub] of Object.entries(schema.properties ?? {})) {
    const strict = strictSchema(sub)
    properties[key] = required.has(key) ? strict : nullable(strict)
  }
  return { ...schema, properties, required: Object.keys(properties), additionalProperties: false }
}

function nullable(schema) {
  const out = { ...schema, type: [schema.type, 'null'] }
  if (schema.enum) out.enum = [...schema.enum, null]
  return out
}

function parseResult(text, schema) {
  if (!schema) return text
  try {
    const value = JSON.parse(text)
    const missing = (schema.required ?? []).filter(key => value?.[key] === undefined)
    return missing.length === 0 ? value : undefined
  } catch {
    return undefined
  }
}

function readResult(dir, schema) {
  const file = join(dir, 'result.json')
  if (!existsSync(file)) return undefined
  const value = JSON.parse(readFileSync(file, 'utf8'))
  return schema ? parseResult(JSON.stringify(value), schema) : value
}

function printStatus(runDir) {
  const state = JSON.parse(readFileSync(join(runDir, 'status.json'), 'utf8'))
  if (!state.finished && !alive(state.pid)) {
    for (const call of state.calls) if (call.state === 'running' || call.state === 'waiting') call.state = 'stopped'
  }
  console.log(renderStatus(state))
  for (const call of state.calls.filter(c => c.state === 'running')) {
    console.log(`## ${call.label}`)
    for (const line of progressLines(call.dir).slice(-5)) console.log(line)
    console.log(`마지막 동작: ${lastEvent(join(call.dir, `events-${call.attempt}.jsonl`))}\n`)
  }
  const logFile = join(runDir, 'runner.log')
  if (existsSync(logFile)) {
    console.log('## runner.log (최근 8줄)')
    console.log(readFileSync(logFile, 'utf8').trimEnd().split('\n').slice(-8).join('\n'))
  }
}

function renderStatus(state) {
  const finished = state.calls.filter(c => c.state === 'done' || c.state === 'cached').length
  const lines = [
    '# 실행 상태',
    '',
    `- 워크플로우: ${basename(dirname(state.workflow))}`,
    `- 시작: ${dateTime(state.started)}${state.finished ? `, 끝: ${dateTime(state.finished)}` : ''}`,
    `- ${state.finished ? '끝남' : '진행 중'}: 지금까지 나온 호출 ${state.calls.length}개 가운데 ${finished}개 완료, 동시 실행 최대 ${state.max}개`,
    '',
    '| 호출 | 상태 | 시도 | 시작 | 걸린 시간 | 최근 진행 |',
    '|---|---|---|---|---|---|',
  ]
  for (const call of state.calls) {
    const last = progressLines(call.dir).at(-1) ?? ''
    const took = call.started ? duration(call.started, call.ended ?? realNow()) : ''
    lines.push(`| ${call.label} | ${STATE_NAMES[call.state] ?? call.state} | ${call.attempt || ''} | ${call.started ? clock(call.started).slice(0, 5) : ''} | ${took} | ${cell(last.replace(/^- /, ''))} |`)
  }
  return lines.join('\n') + '\n'
}

function progressLines(dir) {
  const file = join(dir, 'progress.md')
  return existsSync(file) ? readFileSync(file, 'utf8').split('\n').filter(line => line.trim()) : []
}

function lastEvent(file) {
  if (!existsSync(file)) return '없음'
  const lines = tail(file, 65_536).trimEnd().split('\n').reverse()
  for (const line of lines) {
    let event
    try { event = JSON.parse(line) } catch { continue }
    const item = event.item
    if (!item) continue
    if (item.type === 'command_execution') return `명령 ${cell(item.command ?? '')}`
    if (item.type === 'file_change') return `파일 수정 ${(item.changes ?? []).map(change => basename(change.path ?? '')).join(', ')}`
    if (item.type === 'agent_message') return `메시지 ${cell(item.text ?? '')}`
    return item.type
  }
  return '없음'
}

function tail(file, bytes) {
  const size = statSync(file).size
  const length = Math.min(size, bytes)
  const buffer = Buffer.alloc(length)
  const fd = openSync(file, 'r')
  readSync(fd, buffer, 0, length, size - length)
  closeSync(fd)
  return buffer.toString('utf8')
}

function alive(pid) {
  try {
    process.kill(pid, 0)
    return true
  } catch {
    return false
  }
}

function cell(text) {
  const flat = text.replace(/\s+/g, ' ').replace(/\|/g, '/').trim()
  return flat.length > 90 ? flat.slice(0, 89) + '…' : flat
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function clock(time) {
  const d = new Date(time)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function dateTime(time) {
  const d = new Date(time)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${clock(time).slice(0, 5)}`
}

function duration(from, to) {
  const minutes = Math.round((to - from) / 60_000)
  return minutes < 60 ? `${minutes}분` : `${Math.floor(minutes / 60)}시간 ${minutes % 60}분`
}
