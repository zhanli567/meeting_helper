function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function inlineMarkdown(value) {
  return escapeHtml(value)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
}

function isBlank(line) {
  return !line || !line.trim()
}

function isFence(line) {
  return line.trim().startsWith('```')
}

function isHeading(line) {
  return /^(#{1,4})\s+/.test(line.trim())
}

function isHorizontalRule(line) {
  return /^-{3,}$/.test(line.trim())
}

function isUnorderedListItem(line) {
  return /^\s*[-*]\s+/.test(line)
}

function isOrderedListItem(line) {
  return /^\s*\d+[.)]\s+/.test(line)
}

function isTableSeparator(line) {
  return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line)
}

function splitTableRow(line) {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim())
}

function isTableStart(lines, index) {
  return lines[index]?.includes('|') && isTableSeparator(lines[index + 1] || '')
}

function isBlockStart(lines, index) {
  const line = lines[index] || ''
  return (
    isBlank(line) ||
    isFence(line) ||
    isHeading(line) ||
    isHorizontalRule(line) ||
    isUnorderedListItem(line) ||
    isOrderedListItem(line) ||
    isTableStart(lines, index)
  )
}

function renderTable(lines, index) {
  const headers = splitTableRow(lines[index])
  const rows = []
  let cursor = index + 2
  while (cursor < lines.length && lines[cursor]?.includes('|') && !isBlank(lines[cursor])) {
    rows.push(splitTableRow(lines[cursor]))
    cursor += 1
  }

  const headerHtml = headers.map((header) => `<th>${inlineMarkdown(header)}</th>`).join('')
  const rowsHtml = rows
    .map((row) => `<tr>${row.map((cell) => `<td>${inlineMarkdown(cell)}</td>`).join('')}</tr>`)
    .join('')

  return {
    html: `<table><thead><tr>${headerHtml}</tr></thead><tbody>${rowsHtml}</tbody></table>`,
    nextIndex: cursor,
  }
}

function renderList(lines, index, ordered) {
  const tag = ordered ? 'ol' : 'ul'
  const matcher = ordered ? /^\s*\d+[.)]\s+/ : /^\s*[-*]\s+/
  const items = []
  let cursor = index
  while (cursor < lines.length && (ordered ? isOrderedListItem(lines[cursor]) : isUnorderedListItem(lines[cursor]))) {
    items.push(lines[cursor].replace(matcher, ''))
    cursor += 1
  }
  return {
    html: `<${tag}>${items.map((item) => `<li>${inlineMarkdown(item)}</li>`).join('')}</${tag}>`,
    nextIndex: cursor,
  }
}

function renderCodeBlock(lines, index) {
  const code = []
  let cursor = index + 1
  while (cursor < lines.length && !isFence(lines[cursor])) {
    code.push(lines[cursor])
    cursor += 1
  }
  return {
    html: `<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`,
    nextIndex: cursor < lines.length ? cursor + 1 : cursor,
  }
}

function renderParagraph(lines, index) {
  const paragraph = []
  let cursor = index
  while (cursor < lines.length && !isBlockStart(lines, cursor)) {
    paragraph.push(lines[cursor])
    cursor += 1
  }
  return {
    html: `<p>${paragraph.map(inlineMarkdown).join('<br>')}</p>`,
    nextIndex: cursor,
  }
}

export function renderAgentMarkdown(markdown) {
  const lines = String(markdown ?? '').replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n')
  const blocks = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index] || ''
    if (isBlank(line)) {
      index += 1
      continue
    }
    if (isFence(line)) {
      const block = renderCodeBlock(lines, index)
      blocks.push(block.html)
      index = block.nextIndex
      continue
    }
    if (isTableStart(lines, index)) {
      const block = renderTable(lines, index)
      blocks.push(block.html)
      index = block.nextIndex
      continue
    }
    if (isHeading(line)) {
      const [, markers] = line.trim().match(/^(#{1,4})\s+/)
      const level = Math.min(markers.length, 4)
      const text = line.trim().replace(/^#{1,4}\s+/, '')
      blocks.push(`<h${level}>${inlineMarkdown(text)}</h${level}>`)
      index += 1
      continue
    }
    if (isHorizontalRule(line)) {
      blocks.push('<hr>')
      index += 1
      continue
    }
    if (isUnorderedListItem(line)) {
      const block = renderList(lines, index, false)
      blocks.push(block.html)
      index = block.nextIndex
      continue
    }
    if (isOrderedListItem(line)) {
      const block = renderList(lines, index, true)
      blocks.push(block.html)
      index = block.nextIndex
      continue
    }

    const block = renderParagraph(lines, index)
    blocks.push(block.html)
    index = block.nextIndex
  }

  return blocks.join('\n')
}
