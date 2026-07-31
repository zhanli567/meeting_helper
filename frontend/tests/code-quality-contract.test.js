import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import { extname, join, relative } from 'node:path'
import test from 'node:test'

const repoRoot = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const sourceRoots = ['backend/src/main/java', 'frontend/src'].map((dir) => join(repoRoot, dir))
const sourceExtensions = new Set(['.java', '.js', '.vue'])
const dangerousPatterns = [
  /\beval\s*\(/,
  /\bnew\s+Function\s*\(/,
  /\bFunction\s*\(/,
  /\binnerHTML\b/,
  /\bouterHTML\b/,
  /\bdocument\.write\s*\(/,
  /\bRuntime\.getRuntime\s*\(/,
  /\bProcessBuilder\s*\(/,
  /\bSystem\.exit\s*\(/,
  /\bThread\.stop\s*\(/,
  /\bsun\.misc\.Unsafe\b/,
]

function sourceFiles(dir, files = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const filePath = join(dir, entry.name)
    if (entry.isDirectory()) {
      sourceFiles(filePath, files)
    } else if (sourceExtensions.has(extname(entry.name))) {
      files.push(filePath)
    }
  }
  return files
}

function scriptContent(filePath, source) {
  if (!filePath.endsWith('.vue')) {
    return source
  }
  const match = source.match(/<script[^>]*>([\s\S]*?)<\/script>/)
  return match?.[1] || ''
}

function stripComments(source, keepStrings = true) {
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
        result += keepStrings ? char : ' '
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
    } else {
      result += keepStrings ? char : char === '\n' ? '\n' : ' '
      if (char === '\\') {
        result += keepStrings && index + 1 < source.length ? source[index + 1] : ' '
        index += 2
      } else if (char === state) {
        state = 'code'
        index += 1
      } else {
        index += 1
      }
    }
  }
  return result
}

function extractComments(source) {
  const comments = []
  let index = 0
  let state = 'code'
  while (index < source.length) {
    const char = source[index]
    const next = source[index + 1]
    if (state === 'code') {
      if (char === '/' && next === '/') {
        const end = source.indexOf('\n', index + 2)
        comments.push([index, source.slice(index, end < 0 ? source.length : end)])
        index = end < 0 ? source.length : end
      } else if (char === '/' && next === '*') {
        const end = source.indexOf('*/', index + 2)
        if (end < 0) {
          break
        }
        comments.push([index, source.slice(index, end + 2)])
        index = end + 2
      } else if (char === '"' || char === "'" || char === '`') {
        state = char
        index += 1
      } else {
        index += 1
      }
    } else if (char === '\\') {
      index += 2
    } else if (char === state) {
      state = 'code'
      index += 1
    } else {
      index += 1
    }
  }
  return comments
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

function normalizedCodeLines(source) {
  return stripComments(source)
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/\s+/g, ' '))
    .filter(Boolean)
}

function commentLanguageIssues(filePath, source) {
  return extractComments(source)
    .filter(([, comment]) => /[A-Za-z]{3,}/.test(comment) && !/[\u4e00-\u9fff]/.test(comment))
    .map(([index]) => `${relative(repoRoot, filePath)}:${lineOf(source, index)} 注释必须使用中文`)
}

function dangerousCallIssues(filePath, code) {
  return dangerousPatterns
    .filter((pattern) => pattern.test(code))
    .map((pattern) => `${relative(repoRoot, filePath)} 包含危险调用：${pattern}`)
}

function javaFunctions(filePath, code) {
  const functions = []
  const pattern =
    /^[ \t]*(?:@[^\r\n]+[ \t]*\r?\n[ \t]*)*(?:(public|protected|private)\s+)?(?:static\s+|final\s+|abstract\s+|synchronized\s+|native\s+|strictfp\s+)*(?!class\b|interface\b|enum\b|record\b)([\w<>\[\], ?]+)\s+([A-Za-z_]\w*)\s*\([^;{}]*\)\s*\{/gm
  for (const match of code.matchAll(pattern)) {
    const lineStart = code.lastIndexOf('\n', Math.max(0, match.index - 1)) + 1
    const declaration = code.slice(lineStart, code.indexOf('{', match.index))
    if (/\b(class|interface|enum|record)\b/.test(declaration)) {
      continue
    }
    const fileName = filePath.split(/[\\/]/).at(-1).replace(/\.java$/, '')
    if (match[3] === fileName) {
      continue
    }
    functions.push(functionMetric(filePath, code, match.index))
  }
  return functions
}

function scriptFunctions(filePath, code) {
  const patterns = [
    /\b(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{/g,
    /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{/g,
  ]
  return patterns.flatMap((pattern) =>
    [...code.matchAll(pattern)].map((match) => functionMetric(filePath, code, match.index)),
  )
}

function functionMetric(filePath, code, start) {
  const bodyStart = code.indexOf('{', start)
  const bodyEnd = matching(code, bodyStart, '{', '}')
  const body = bodyEnd < 0 ? '' : code.slice(bodyStart + 1, bodyEnd)
  const lines = body.split(/\r?\n/).filter((line) => line.trim()).length
  const complexity = 1 + (body.match(/\b(if|for|while|case|catch)\b|&&|\|\||\?/g) || []).length
  return { filePath, lines, complexity, line: lineOf(code, start) }
}

function duplicateMetrics(files) {
  const windows = new Map()
  const normalizedFiles = new Map()
  let effectiveLines = 0
  for (const filePath of files) {
    const source = scriptContent(filePath, readFileSync(filePath, 'utf8'))
    const lines = normalizedCodeLines(source)
    effectiveLines += lines.length
    const fileKey = lines.join('\n')
    if (fileKey) {
      normalizedFiles.set(fileKey, [...(normalizedFiles.get(fileKey) || []), filePath])
    }
    for (let index = 0; index + 10 < lines.length; index += 1) {
      const key = lines.slice(index, index + 11).join('\n')
      windows.set(key, [...(windows.get(key) || []), [filePath, index]])
    }
  }

  const duplicateLines = new Set()
  for (const occurrences of windows.values()) {
    if (new Set(occurrences.map(([filePath]) => filePath)).size <= 1) {
      continue
    }
    for (const [filePath, start] of occurrences) {
      for (let offset = 0; offset < 11; offset += 1) {
        duplicateLines.add(`${filePath}:${start + offset}`)
      }
    }
  }

  const duplicateFileCount = [...normalizedFiles.values()]
    .filter((group) => group.length > 1)
    .reduce((total, group) => total + group.length, 0)
  return {
    duplicateFileRate: duplicateFileCount / files.length,
    duplicateLineRate: duplicateLines.size / effectiveLines,
    redundantDensity: (duplicateLines.size / effectiveLines) * 1000,
  }
}

function projectMetrics(files) {
  let totalLines = 0
  const functions = []
  const issues = []
  for (const filePath of files) {
    const source = readFileSync(filePath, 'utf8')
    const content = scriptContent(filePath, source)
    const code = stripComments(content, false)
    const lines = normalizedCodeLines(content)
    totalLines += lines.length
    issues.push(...commentLanguageIssues(filePath, source))
    issues.push(...dangerousCallIssues(filePath, code))
    functions.push(...(filePath.endsWith('.java') ? javaFunctions(filePath, code) : scriptFunctions(filePath, code)))
  }
  const duplicate = duplicateMetrics(files)
  return {
    issues,
    fileCount: files.length,
    averageFileLines: totalLines / files.length,
    functionCount: functions.length,
    averageFunctionLines: functions.reduce((total, item) => total + item.lines, 0) / functions.length,
    averageComplexity: functions.reduce((total, item) => total + item.complexity, 0) / functions.length,
    hugeComplexityRatio: functions.filter((item) => item.complexity > 20).length / functions.length,
    hugeFunctionRatio: functions.filter((item) => item.lines > 100).length / functions.length,
    ...duplicate,
  }
}

test('project code quality stays within pipeline smell thresholds', () => {
  const files = sourceRoots.flatMap((dir) => sourceFiles(dir))
  const metrics = projectMetrics(files)
  const issues = [...metrics.issues]

  if (metrics.averageFileLines > 2000) {
    issues.push(`平均文件代码行 ${metrics.averageFileLines.toFixed(2)} 超过 2000`)
  }
  if (metrics.averageComplexity > 5) {
    issues.push(`平均圈复杂度 ${metrics.averageComplexity.toFixed(2)} 超过 5`)
  }
  if (metrics.averageFunctionLines > 50) {
    issues.push(`平均函数代码行 ${metrics.averageFunctionLines.toFixed(2)} 超过 50`)
  }
  if (metrics.duplicateFileRate > 0.04) {
    issues.push(`总文件重复率 ${(metrics.duplicateFileRate * 100).toFixed(2)}% 超过 4%`)
  }
  if (metrics.hugeComplexityRatio > 0.0023) {
    issues.push(`超大圈复杂度函数占比 ${(metrics.hugeComplexityRatio * 100).toFixed(2)}% 超过 0.23%`)
  }
  if (metrics.hugeFunctionRatio > 0.008) {
    issues.push(`超大函数比例 ${(metrics.hugeFunctionRatio * 100).toFixed(2)}% 超过 0.8%`)
  }
  if (metrics.redundantDensity > 2) {
    issues.push(`冗余代码块密度 ${metrics.redundantDensity.toFixed(2)} 超过 2`)
  }
  if (metrics.duplicateLineRate > 0.06) {
    issues.push(`总代码重复率 ${(metrics.duplicateLineRate * 100).toFixed(2)}% 超过 6%`)
  }

  assert.deepEqual(issues, [])
})
