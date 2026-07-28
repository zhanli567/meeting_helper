import assert from 'node:assert/strict'
import test from 'node:test'

import {
  dropParticipantToPending,
  requestParticipantAttendance,
  requestParticipantRemoval,
  resolveParticipantPage,
  resetParticipantPage,
  startParticipantDrag,
  submitParticipant,
  updateParticipantDetails,
} from '../src/utils/participantActions.js'

function dragEvent(participantId = 'person-7') {
  const state = { prevented: false, calls: [], effectAllowed: undefined }
  return {
    state,
    preventDefault() {
      state.prevented = true
    },
    dataTransfer: {
      setData(type, value) {
        state.calls.push([type, value])
      },
      getData(type) {
        return type === 'text/participant-id' ? participantId : ''
      },
      set effectAllowed(value) {
        state.effectAllowed = value
      },
    },
  }
}

test('新增动作调用注入 API 并返回新增人员', async () => {
  const calls = []
  const created = { id: 'person-7', name: '王创新' }
  const addParticipant = async (...args) => {
    calls.push(args)
    return created
  }

  const result = await submitParticipant({
    addParticipant,
    meetingId: 'meeting-1',
    form: {
      employeeNo: 'a12345678',
      name: '王创新',
      attributes: { 批次: '第二批', 奖项: '创新奖' },
      ignored: '不能发送',
    },
    targetElementId: 'seat-12',
  })

  assert.equal(result, created)
  assert.deepEqual(calls, [
    [
      'meeting-1',
      {
        employeeNo: 'a12345678',
        name: '王创新',
        attributes: { 批次: '第二批', 奖项: '创新奖' },
        targetElementId: 'seat-12',
      },
    ],
  ])
})

test('更新人员动作调用注入 API 并返回更新结果', async () => {
  const calls = []
  const updated = { id: 'person-7', name: '王创新' }
  const updateParticipant = async (...args) => {
    calls.push(args)
    return updated
  }

  const result = await updateParticipantDetails({
    updateParticipant,
    meetingId: 'meeting-1',
    participantId: 'person-7',
    form: {
      name: '王创新',
      records: [{ id: 'record-1', attributes: { 部门: '研发' } }],
      extraFields: [],
      fieldDefinitions: [],
    },
  })

  assert.equal(result, updated)
  assert.deepEqual(calls, [
    ['meeting-1', 'person-7', { name: '王创新', records: [{ id: 'record-1', attributes: { 部门: '研发' } }] }],
  ])
})

test('拖放动作写入人员 ID，并在只读、锁定或临时不出席时拒绝', () => {
  const selected = []
  const dragging = []
  const event = dragEvent()

  assert.equal(
    startParticipantDrag({
      event,
      participant: { id: 'person-7', attendanceStatus: 'PRESENT' },
      readonly: false,
      locked: false,
      onSelect: (participant) => selected.push(participant.id),
      onDragState: (id) => dragging.push(id),
    }),
    true,
  )
  assert.deepEqual(event.state.calls, [['text/participant-id', 'person-7']])
  assert.equal(event.state.effectAllowed, 'move')
  assert.deepEqual(selected, ['person-7'])
  assert.deepEqual(dragging, ['person-7'])

  for (const denied of [
    { readonly: true, locked: false, attendanceStatus: 'PRESENT' },
    { readonly: false, locked: true, attendanceStatus: 'PRESENT' },
    { readonly: false, locked: false, attendanceStatus: 'TEMPORARILY_ABSENT' },
  ]) {
    const deniedEvent = dragEvent()
    assert.equal(
      startParticipantDrag({
        event: deniedEvent,
        participant: { id: 'person-8', attendanceStatus: denied.attendanceStatus },
        readonly: denied.readonly,
        locked: denied.locked,
        onSelect: () => assert.fail('拒绝拖放时不应选中人员'),
        onDragState: () => assert.fail('拒绝拖放时不应更新拖放状态'),
      }),
      false,
    )
    assert.equal(deniedEvent.state.prevented, true)
    assert.deepEqual(deniedEvent.state.calls, [])
  }
})

test('拖回待排动作读取拖放 ID 并触发回调，唯读时不触发', () => {
  const unassigned = []
  const dropped = []
  const event = dragEvent('person-9')

  assert.equal(
    dropParticipantToPending({
      event,
      readonly: false,
      onUnassign: (id) => unassigned.push(id),
      onDrop: () => dropped.push('done'),
    }),
    true,
  )
  assert.equal(event.state.prevented, true)
  assert.deepEqual(unassigned, ['person-9'])
  assert.deepEqual(dropped, ['done'])

  const readonlyEvent = dragEvent('person-10')
  assert.equal(
    dropParticipantToPending({
      event: readonlyEvent,
      readonly: true,
      onUnassign: () => assert.fail('只读时不应移回待排'),
      onDrop: () => assert.fail('只读时不应处理放置'),
    }),
    false,
  )
  assert.equal(readonlyEvent.state.prevented, false)
})

test('人员面板页码重置和越界控制保持现有边界', () => {
  assert.equal(resetParticipantPage(), 1)
  assert.equal(resolveParticipantPage(4, 17, 8), 3)
  assert.equal(resolveParticipantPage(3, 0, 8), 1)
  assert.equal(resolveParticipantPage(2, 16, 8), 2)
})

test('出席和移出会议动作在只读时不触发，可编辑时发出正确事件', () => {
  const events = []
  const emit = (...args) => events.push(args)
  const absent = { id: 'absent', attendanceStatus: 'TEMPORARILY_ABSENT' }
  const present = { id: 'present', attendanceStatus: 'PRESENT' }

  assert.equal(requestParticipantAttendance({ readonly: true, participant: absent, emit }), false)
  assert.equal(requestParticipantRemoval({ readonly: true, participant: present, emit }), false)
  assert.deepEqual(events, [])

  assert.equal(requestParticipantAttendance({ readonly: false, participant: absent, emit }), true)
  assert.equal(requestParticipantAttendance({ readonly: false, participant: present, emit }), true)
  assert.equal(requestParticipantRemoval({ readonly: false, participant: present, emit }), true)
  assert.deepEqual(events, [
    ['attendance', absent, 'PRESENT'],
    ['attendance', present, 'TEMPORARILY_ABSENT'],
    ['remove', present],
  ])
})
