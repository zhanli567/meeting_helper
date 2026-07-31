import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import { extname, join, relative } from 'node:path'
import test from 'node:test'

const root = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const sourceRoot = join(root, 'src')

function sourceFiles(dir = sourceRoot, files = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const filePath = join(dir, entry.name)
    if (entry.isDirectory()) {
      sourceFiles(filePath, files)
    } else if (['.js', '.vue'].includes(extname(entry.name))) {
      files.push(filePath)
    }
  }
  return files
}

function scriptContent(filePath) {
  const source = readFileSync(filePath, 'utf8')
  if (!filePath.endsWith('.vue')) {
    return source
  }
  const match = source.match(/<script[^>]*>([\s\S]*?)<\/script>/)
  return match?.[1] || ''
}

function stripCode(source) {
  let result = ''
  let index = 0
  let state = 'code'
  while (index < source.length) {
    const char = source[index]
    const next = source[index + 1]
    if (state === 'code') {
      if (char === '/' && next === '/') {
        state = 'line'
        result += '  '
        index += 2
      } else if (char === '/' && next === '*') {
        state = 'block'
        result += '  '
        index += 2
      } else if (char === '"' || char === "'" || char === '`') {
        state = char
        result += ' '
        index += 1
      } else {
        result += char
        index += 1
      }
    } else if (state === 'line') {
      if (char === '\n') {
        state = 'code'
        result += '\n'
      } else {
        result += ' '
      }
      index += 1
    } else if (state === 'block') {
      if (char === '*' && next === '/') {
        state = 'code'
        result += '  '
        index += 2
      } else {
        result += char === '\n' ? '\n' : ' '
        index += 1
      }
    } else if (char === '\\') {
      result += '  '
      index += 2
    } else if (char === state) {
      state = 'code'
      result += ' '
      index += 1
    } else {
      result += char === '\n' ? '\n' : ' '
      index += 1
    }
  }
  return result
}

function lineOf(source, index) {
  return source.slice(0, index).split(/\r?\n/).length
}

function matching(source, start, openChar, closeChar) {
  let depth = 0
  for (let index = start; index < source.length; index += 1) {
    if (source[index] === openChar) {
      depth += 1
    } else if (source[index] === closeChar) {
      depth -= 1
      if (depth === 0) {
        return index
      }
    }
  }
  return -1
}

function nextCodeIndex(source, index) {
  let cursor = index
  while (/\s/.test(source[cursor] || '')) {
    cursor += 1
  }
  return cursor
}

function splitTopLevel(rawText) {
  const parts = []
  let start = 0
  let depth = 0
  for (let index = 0; index < rawText.length; index += 1) {
    const char = rawText[index]
    if (char === '(' || char === '[' || char === '{' || char === '<') {
      depth += 1
    } else if (char === ')' || char === ']' || char === '}' || char === '>') {
      depth -= 1
    } else if (char === ',' && depth === 0) {
      parts.push(rawText.slice(start, index).trim())
      start = index + 1
    }
  }
  parts.push(rawText.slice(start).trim())
  return parts.filter(Boolean)
}

function parameterCount(rawParameters) {
  return splitTopLevel(rawParameters).length
}

function location(filePath, source, index, message) {
  return `${relative(root, filePath)}:${lineOf(source, index)} ${message}`
}

function ifBraceIssues(filePath) {
  const source = stripCode(scriptContent(filePath))
  const issues = []
  for (const match of source.matchAll(/\bif\s*\(/g)) {
    const open = source.indexOf('(', match.index)
    const close = matching(source, open, '(', ')')
    const bodyStart = nextCodeIndex(source, close + 1)
    if (source[bodyStart] !== '{') {
      issues.push(location(filePath, source, match.index, 'if statement must use braces'))
    }
  }
  return issues
}

function elseIfIssues(filePath) {
  const source = stripCode(scriptContent(filePath))
  const issues = []
  let cursor = 0
  while ((cursor = source.indexOf('else if', cursor)) >= 0) {
    let chainCursor = cursor
    let blockEnd = -1
    while (source.startsWith('else if', chainCursor)) {
      const open = source.indexOf('(', chainCursor)
      const close = matching(source, open, '(', ')')
      const bodyStart = nextCodeIndex(source, close + 1)
      if (source[bodyStart] !== '{') {
        break
      }
      blockEnd = matching(source, bodyStart, '{', '}') + 1
      chainCursor = nextCodeIndex(source, blockEnd)
    }
    if (blockEnd > 0 && !source.startsWith('else', chainCursor)) {
      issues.push(location(filePath, source, cursor, 'else-if chain must end with else'))
    }
    cursor += 'else if'.length
  }
  return issues
}

function switchIssues(filePath) {
  const source = stripCode(scriptContent(filePath))
  const issues = []
  for (const match of source.matchAll(/\bswitch\s*\(/g)) {
    const open = source.indexOf('(', match.index)
    const close = matching(source, open, '(', ')')
    const bodyStart = nextCodeIndex(source, close + 1)
    const bodyEnd = matching(source, bodyStart, '{', '}')
    if (bodyStart >= 0 && bodyEnd >= 0 && !/\bdefault\s*:/.test(source.slice(bodyStart, bodyEnd))) {
      issues.push(location(filePath, source, match.index, 'switch statement must include default'))
    }
  }
  return issues
}

function functionIssues(filePath) {
  const source = stripCode(scriptContent(filePath))
  const issues = []
  const patterns = [
    /\b(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(([^)]*)\)\s*\{/g,
    /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?\(([^)]*)\)\s*=>\s*\{/g,
  ]
  for (const pattern of patterns) {
    for (const match of source.matchAll(pattern)) {
      const bodyStart = source.indexOf('{', match.index)
      const bodyEnd = matching(source, bodyStart, '{', '}')
      if (bodyEnd < 0) {
        continue
      }
      const lineCount = lineOf(source, bodyEnd) - lineOf(source, match.index) + 1
      const params = parameterCount(match[2])
      if (params > 5) {
        issues.push(location(filePath, source, match.index, `${match[1]} parameter count must not exceed 5`))
      }
      if (lineCount > 50) {
        issues.push(location(filePath, source, match.index, `${match[1]} function length must not exceed 50 lines`))
      }
    }
  }
  return issues
}

test('frontend code follows brace, branch, switch, function length, and parameter conventions', () => {
  const issues = sourceFiles().flatMap((filePath) => [
    ...ifBraceIssues(filePath),
    ...elseIfIssues(filePath),
    ...switchIssues(filePath),
    ...functionIssues(filePath),
  ])

  assert.deepEqual(issues, [])
})
