import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('layout mode separates canvas and element panel into card areas', async () => {
  const source = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')

  assert.match(source, /\.editor-body\s*\{[\s\S]*gap:\s*14px;[\s\S]*padding:\s*14px;/)
  assert.match(source, /\.canvas-pane\s*\{[\s\S]*background:\s*#fff;[\s\S]*border:\s*1px solid var\(--line\);[\s\S]*border-radius:\s*var\(--radius-md\);[\s\S]*box-shadow:\s*var\(--shadow\);/)
  assert.match(source, /\.venue-element-panel\s*\{[\s\S]*border:\s*1px solid var\(--line\);[\s\S]*border-radius:\s*var\(--radius-md\);[\s\S]*box-shadow:\s*var\(--shadow\);/)
  assert.doesNotMatch(source, /border-left:\s*1px solid #dbe4f0;[\s\S]*border-right:\s*1px solid #dbe4f0;/)
})
