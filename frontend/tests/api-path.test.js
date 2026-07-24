import assert from 'node:assert/strict'
import test from 'node:test'

import { apiBaseUrl, apiDownloadUrl } from '../src/utils/apiPath.js'

test('开发环境通过 Vite 的 /api 代理访问后端', () => {
  assert.equal(apiBaseUrl(true), '/api')
  assert.equal(apiDownloadUrl('/import-templates/general/file', true), '/api/import-templates/general/file')
})

test('生产环境直接访问无 /api 前缀的控制层路径', () => {
  assert.equal(apiBaseUrl(false), '')
  assert.equal(apiDownloadUrl('/import-templates/general/file', false), '/import-templates/general/file')
})
