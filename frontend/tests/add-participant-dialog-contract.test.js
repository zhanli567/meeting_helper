import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('add participant import actions expose separate loading states and selected file feedback', () => {
  const dialog = source('../src/components/AddParticipantDialog.vue')

  assert.match(dialog, /templateDownloading/)
  assert.match(dialog, /previewing/)
  assert.match(dialog, /selectedFileName/)
  assert.match(dialog, /selected-file-name/)
  assert.match(dialog, /:show-file-list="false"/)
  assert.match(dialog, /ref="uploadRef"/)
  assert.match(dialog, /:on-exceed="onFileExceed"/)
  assert.match(dialog, /class="selected-file-remove"/)
  assert.match(dialog, /@click="clearFile"/)
  assert.match(dialog, /:loading="templateDownloading"/)
  assert.match(dialog, /:loading="previewing"/)
  assert.match(dialog, /:disabled="previewing \|\| !file"/)
  assert.doesNotMatch(dialog, /const importing = ref/)
  assert.doesNotMatch(dialog, /\.upload-surface\s+:deep\(\.el-upload-list\)\s*\{[\s\S]*display:\s*none;/)
})

test('add participant import preview merges parsed rows into the current draft instead of replacing it', () => {
  const dialog = source('../src/components/AddParticipantDialog.vue')

  assert.match(dialog, /mergePreviewRowsIntoParticipantDraft/)
  assert.match(dialog, /appendedCount/)
  assert.match(dialog, /skippedDuplicateCount/)
  assert.doesNotMatch(dialog, /form\.records\s*=\s*rows\.length\s*\?/)
  assert.doesNotMatch(dialog, /rows\.map\(\(row\) => \(\{/)
})
