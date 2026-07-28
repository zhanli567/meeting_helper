import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('变更记录每个分组内按时间从早到晚排列', async () => {
  const source = await readFile(new URL('../../CHANGELOG.md', import.meta.url), 'utf8')
  const sections = source.split(/\n(?=### )/).filter((section) => section.startsWith('### '))

  for (const section of sections) {
    const title = section.match(/^###\s+(.+)$/m)?.[1] || '未命名分组'
    const times = [...section.matchAll(/-\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})/g)].map(
      (match) => match[1],
    )
    for (let index = 1; index < times.length; index += 1) {
      assert.ok(
        times[index - 1] <= times[index],
        `${title} 中 ${times[index - 1]} 不应排在 ${times[index]} 前面`,
      )
    }
  }
})
