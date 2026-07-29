import assert from 'node:assert/strict'
import test from 'node:test'

import * as participantRules from '../src/utils/participantRules.js'

const { attendingPendingCount, isValidEmployeeNo, participantCanBeSeated } = participantRules

test('工号只要求非空且不限制格式', () => {
  assert.equal(isValidEmployeeNo('12345678'), true)
  assert.equal(isValidEmployeeNo('a12345678'), true)
  assert.equal(isValidEmployeeNo('wx12345678'), true)
  assert.equal(isValidEmployeeNo('自由工号-001'), true)
  assert.equal(isValidEmployeeNo('  外部工号  '), true)
  assert.equal(isValidEmployeeNo(''), false)
  assert.equal(isValidEmployeeNo('   '), false)
  assert.equal(isValidEmployeeNo(undefined), false)
})

test('临时不出席人员不参与待排校验且不能排座', () => {
  const participants = [
    { assignedElementId: undefined, attendanceStatus: 'PRESENT' },
    { assignedElementId: undefined, attendanceStatus: 'TEMPORARILY_ABSENT' },
    { assignedElementId: 'seat-1', attendanceStatus: 'PRESENT' },
  ]
  assert.equal(attendingPendingCount(participants), 1)
  assert.equal(participantCanBeSeated(participants[1]), false)
})

test('新增人员时按工号忽略大小写识别会议内重复人员', () => {
  assert.equal(typeof participantRules.hasDuplicateEmployeeNo, 'function')
  const participants = [{ employeeNo: 'a12345678' }, { employeeNo: '87654321' }]

  assert.equal(participantRules.hasDuplicateEmployeeNo('A12345678', participants), true)
  assert.equal(participantRules.hasDuplicateEmployeeNo('12345678', participants), false)
})
