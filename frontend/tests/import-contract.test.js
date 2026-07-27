import assert from 'node:assert/strict'
import test from 'node:test'

import { http } from '../src/api/http.js'
import { importContract } from '../src/api/meeting.js'
import { venueApi } from '../src/api/venue.js'

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

test('请求层只暴露 GET 和 POST 且不再手工附加用户请求头', () => {
  assert.deepEqual(Object.keys(http).sort(), ['get', 'post'])
  assert.equal(typeof http.get, 'function')
  assert.equal(typeof http.post, 'function')
})

test('场馆请求层作为独立模块暴露完整场馆操作', () => {
  assert.deepEqual(Object.keys(venueApi).sort(), [
    'create',
    'detail',
    'layout',
    'list',
    'remove',
    'updateInfo',
    'updateLayout',
  ])
})
