import { appendFileSync, readFileSync } from 'node:fs'

const [scriptPath, argsJson, scenario = 'normal'] = process.argv.slice(2)
if (!scriptPath || !argsJson) {
  console.error('usage: node mock-workflow.mjs <workflow.js> <args JSON> [normal|blind-fail|author-null]')
  process.exit(2)
}
const source = readFileSync(scriptPath, 'utf8').replace(/^export const meta/m, 'const meta')
const calls = []
let active = 0
let maxActive = 0

function fromSchema(schema, label) {
  if (!schema) return 'text'
  switch (schema.type) {
    case 'object': {
      const out = {}
      for (const [key, sub] of Object.entries(schema.properties || {})) {
        out[key] = fromSchema(sub, label)
      }
      return out
    }
    case 'array': return []
    case 'string': return schema.enum ? schema.enum[0] : label
    case 'integer': return 1
    case 'number': return 1
    case 'boolean': return true
    default: return null
  }
}

function override(result, label) {
  if (scenario === 'blind-fail' && label.endsWith(':audit') && 'blind_passed' in result) result.blind_passed = false
  if (scenario === 'author-null' && label.endsWith(':author')) return null
  if (label.endsWith(':author') && 'questions' in result) {
    result.questions = [
      { id: 'q1', type: 'choice', prompt: 'p', choices: ['a', 'b'], answer: 2 },
      { id: 'q2', type: 'short', prompt: 'p', input: ['X'], answers: ['1 2'] },
    ]
  }
  if ((label.endsWith(':blind') || label.endsWith(':confirm')) && 'first_answers' in result) {
    result.first_answers = [{ id: 'q1', choice: 1, confidence: 'high', defensible: [] }, { id: 'q2', text: '1  2', confidence: 'low' }]
    result.final_answers = [{ id: 'q1', choice: 2, confidence: 'high', defensible: [] }, { id: 'q2', text: '1 2', confidence: 'high' }]
  }
  return result
}

Date.now = () => { throw new Error('Date.now() is not allowed in workflow scripts') }
Math.random = () => { throw new Error('Math.random() is not allowed in workflow scripts') }

globalThis.args = JSON.parse(argsJson)
globalThis.log = message => console.log('[log]', message)
globalThis.phase = title => console.log('[phase]', title)
globalThis.budget = { total: null, spent: () => 0, remaining: () => Infinity }
const errors = []
function swallow(error) {
  errors.push(error.message)
  console.log('[error]', error.message)
  return null
}

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
globalThis.agent = async (prompt, opts = {}) => {
  if (process.env.MOCK_PROMPTS) appendFileSync(process.env.MOCK_PROMPTS, '### ' + opts.label + '\n' + prompt + '\n')
  active++
  maxActive = Math.max(maxActive, active)
  calls.push(opts.label)
  await new Promise(resolve => setTimeout(resolve, 5))
  active--
  if (typeof prompt !== 'string' || prompt.includes('undefined')) {
    throw new Error('prompt contains undefined: ' + opts.label + ' :: ' + String(prompt).split('\n').filter(line => line.includes('undefined')).join(' | '))
  }
  return override(fromSchema(opts.schema, opts.label), opts.label)
}

const body = `return (async () => {\n${source}\n})()`
const result = await new Function(body)()
console.log('calls:', calls.length, 'maxConcurrent:', maxActive, 'errors:', errors.length)
console.log(calls.join('\n'))
console.log('result:', JSON.stringify(result).slice(0, 600))
if (errors.length > 0) process.exit(1)
