import assert from 'node:assert/strict'
import test from 'node:test'

import {
  attendingPendingCount,
  isValidEmployeeNo,
  participantCanBeSeated,
} from '../src/utils/participantRules.js'

test('工号只接受 8 位数字或小写字母加 8 位数字', () => {
  assert.equal(isValidEmployeeNo('12345678'), true)
  assert.equal(isValidEmployeeNo('a12345678'), true)
  assert.equal(isValidEmployeeNo('A12345678'), false)
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
