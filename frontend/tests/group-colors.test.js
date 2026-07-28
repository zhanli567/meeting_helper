import assert from 'node:assert/strict'
import test from 'node:test'

import { buildParticipantColorMap, participantFieldValue } from '../src/utils/groupColors.js'

test('人员字段取值优先读取主属性并忽略空值', () => {
  const person = {
    id: 'p1',
    name: '张三',
    employeeNo: '001',
    primaryAttributes: { department: '研发部' },
    attributeValues: { batch: ['', '第一批'] },
  }

  assert.equal(participantFieldValue(person, 'name'), '张三')
  assert.equal(participantFieldValue(person, 'department'), '研发部')
  assert.equal(participantFieldValue(person, 'batch'), '第一批')
})

test('相同字段值使用相同浅色且空字段不着色', () => {
  const colors = buildParticipantColorMap([
    { id: 'p1', primaryAttributes: { department: '研发部' } },
    { id: 'p2', primaryAttributes: { department: '市场部' } },
    { id: 'p3', primaryAttributes: { department: '研发部' } },
    { id: 'p4', primaryAttributes: { department: '' } },
  ], 'department')

  assert.equal(colors.get('p1').backgroundColor, colors.get('p3').backgroundColor)
  assert.notEqual(colors.get('p1').backgroundColor, colors.get('p2').backgroundColor)
  assert.equal(colors.has('p4'), false)
})
