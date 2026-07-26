import assert from 'node:assert/strict'
import test from 'node:test'

import { currentUser } from '../src/auth/session.js'
import { http } from '../src/api/http.js'
import { importContract } from '../src/api/meeting.js'

test('通用人员导入使用单一后端路径契约', () => {
  assert.equal(importContract.templatePath, '/imports/template')
  assert.equal(importContract.previewPath('m1'), '/meetings/m1/imports/preview')
  assert.equal(importContract.commitPath('m1', 't1'), '/meetings/m1/imports/t1/commit')
})

test('导入预览存在顶层阻断错误时不能提交', () => {
  assert.equal(importContract.canCommit(undefined), false)
  assert.equal(importContract.canCommit({ errors: [] }), true)
  assert.equal(importContract.canCommit({ errors: ['工号a12345678已对应人员张三'] }), false)
})

test('请求拦截器为请求附加当前用户标识', async () => {
  const interceptor = http.interceptors.request.handlers.find((handler) => handler?.fulfilled)
  const config = await interceptor.fulfilled({ headers: {} })

  assert.equal(config.headers['X-User-Id'], currentUser.id)
})
