import assert from 'node:assert/strict'
import test from 'node:test'

import { http } from '../src/api/http.js'
import { importContract } from '../src/api/meeting.js'
import { venueApi } from '../src/api/venue.js'

test('通用人员导入使用单一后端路径契约', () => {
  assert.equal(importContract.templatePath, '/imports/template')
  assert.equal(importContract.previewPath('m1'), '/meetings/m1/imports/preview')
})

test('前端导入只保留解析预览，最终保存走新增人员提交', () => {
  assert.equal('commitPath' in importContract, false)
  assert.equal('canCommit' in importContract, false)
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
    'locationAvailability',
    'remove',
    'updateInfo',
    'updateLayout',
  ])
})
