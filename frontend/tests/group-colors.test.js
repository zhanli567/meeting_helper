import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildParticipantColorMap,
  participantFieldValue,
  participantFieldValues,
} from '../src/utils/groupColors.js'

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
  assert.deepEqual(participantFieldValues(person, 'batch'), ['第一批'])
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

test('重复人员同一字段存在多个值时使用多段浅色标识', () => {
  const colors = buildParticipantColorMap([
    {
      id: 'p1',
      primaryAttributes: { batch: '第一批' },
      attributeValues: { batch: ['第一批', '第三批'] },
    },
    {
      id: 'p2',
      primaryAttributes: { batch: '第二批' },
      attributeValues: { batch: ['第二批'] },
    },
  ], 'batch')

  assert.deepEqual(participantFieldValues({
    primaryAttributes: { batch: '第一批' },
    attributeValues: { batch: ['第一批', '第三批', ' '] },
  }, 'batch'), ['第一批', '第三批'])
  assert.equal(colors.get('p1').multiValue, true)
  assert.match(colors.get('p1').backgroundImage, /linear-gradient/)
  assert.equal(colors.get('p1').value, '第一批、第三批')
  assert.equal(colors.get('p2').multiValue, false)
})
