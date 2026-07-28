import assert from 'node:assert/strict'
import test from 'node:test'

import * as participantRules from '../src/utils/participantRules.js'

const { attendingPendingCount, isValidEmployeeNo, participantCanBeSeated } = participantRules

test('工号支持 8 位数字、小写字母加 8 位数字和 wx 加 6 或 7 位数字', () => {
  assert.equal(isValidEmployeeNo('12345678'), true)
  assert.equal(isValidEmployeeNo('a12345678'), true)
  assert.equal(isValidEmployeeNo('wx123456'), true)
  assert.equal(isValidEmployeeNo('wx1234567'), true)
  assert.equal(isValidEmployeeNo('A12345678'), false)
  assert.equal(isValidEmployeeNo('wx12345'), false)
  assert.equal(isValidEmployeeNo('wx12345678'), false)
  assert.equal(isValidEmployeeNo('123456789'), false)
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
