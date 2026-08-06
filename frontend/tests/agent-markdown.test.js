import assert from 'node:assert/strict'
import test from 'node:test'

import { renderAgentMarkdown } from '../src/agent/runtime/markdown.js'

test('agent markdown renders headings, bold text and tables', () => {
  const html = renderAgentMarkdown(
    '## 📊 座位占用概况\n\n'
      + '| 项目 | 数量 |\n'
      + '| --- | --- |\n'
      + '| 座位总数 | **65** |\n'
      + '| 空座位 | **63** |\n',
  )

  assert.match(html, /<h2>[\s\S]*座位占用概况[\s\S]*<\/h2>/)
  assert.match(html, /<table>/)
  assert.match(html, /<th>项目<\/th>/)
  assert.match(html, /<strong>65<\/strong>/)
})

test('agent markdown escapes raw html before rendering formatting', () => {
  const html = renderAgentMarkdown('<script>alert(1)</script>\n\n**安全文本**')

  assert.doesNotMatch(html, /<script>/)
  assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/)
  assert.match(html, /<strong>安全文本<\/strong>/)
})
