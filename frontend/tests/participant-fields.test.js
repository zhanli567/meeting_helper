import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createParticipantPayload,
  createParticipantUpdatePayload,
  filteredParticipants,
  firstParticipantSummary,
  groupParticipants,
  groupableFields,
  matchesParticipant,
  normalizeExtraFields,
  paginateParticipants,
  participantSummary,
  participantDragData,
  primaryFieldValue,
} from '../src/utils/participantFields.js'

const fieldDefinitions = [
  { code: 'employeeNo', label: '工号', filterable: true },
  { code: 'name', label: '姓名', filterable: true },
  { code: '批次', label: '批次', filterable: true },
  { code: '奖项', label: '奖项', filterable: true },
]

const person = {
  employeeNo: 'a12345678',
  name: '王创新',
  primaryAttributes: { 批次: '第二批', 奖项: '优秀项目奖' },
  attributeValues: { 批次: ['第二批', '第三批'], 奖项: ['优秀项目奖', '创新奖'] },
}

const personWithoutGroup = {
  employeeNo: '87654321',
  name: '李空白',
  primaryAttributes: {},
  attributeValues: {},
}

test('搜索匹配任意动态记录值', () => {
  assert.equal(matchesParticipant(person, '创新奖'), true)
  assert.equal(matchesParticipant(person, '王创'), true)
  assert.equal(matchesParticipant(person, 'a12345678'), true)
})

test('分组使用扩展字段主值且空值进入未填写', () => {
  const groups = groupParticipants([personWithoutGroup, person], '批次')

  assert.deepEqual(groups.map((group) => group.label), ['未填写', '第二批'])
  assert.deepEqual(groups[1].people, [person])
  assert.equal(primaryFieldValue(personWithoutGroup, '批次'), '未填写')
})

test('分组选项不包含工号和姓名', () => {
  assert.deepEqual(groupableFields(fieldDefinitions).map((field) => field.label), ['批次', '奖项'])
})

test('摘要按字段定义顺序返回非空主值', () => {
  assert.deepEqual(participantSummary(person, fieldDefinitions, 2), ['第二批', '优秀项目奖'])
})

test('新增人员请求仅传递后端 DTO 字段和动态属性', () => {
  const payload = createParticipantPayload(
    {
      employeeNo: 'a12345678',
      name: '王创新',
      attributes: { 批次: '第二批', 奖项: '创新奖' },
      ignored: '不会发送',
    },
    'seat-12',
  )

  assert.deepEqual(payload, {
    employeeNo: 'a12345678',
    name: '王创新',
    attributes: { 批次: '第二批', 奖项: '创新奖' },
    targetElementId: 'seat-12',
  })
  assert.deepEqual(createParticipantPayload({ employeeNo: '12345678', name: '李空白' }), {
    employeeNo: '12345678',
    name: '李空白',
    attributes: {},
    targetElementId: undefined,
  })
})

test('新增人员列名和值必须非空且不能重名', () => {
  assert.throws(
    () => normalizeExtraFields([{ name: '部门', value: '秘书处' }], [{ code: '部门' }]),
    /字段已存在|列已存在/,
  )
  assert.throws(
    () => normalizeExtraFields([{ name: ' ', value: '秘书处' }], []),
    /请输入列名/,
  )
  assert.throws(
    () => normalizeExtraFields([{ name: '部门', value: ' ' }], []),
    /请填写该人员在新增列中的值/,
  )
  assert.deepEqual(
    normalizeExtraFields([{ name: '部门', value: ' 秘书处 ' }], []),
    { 部门: '秘书处' },
  )
})

test('人员更新载荷按记录保留新增列值且不复制到其他记录', () => {
  assert.deepEqual(
    createParticipantUpdatePayload({
      name: '王创新',
      records: [
        { id: 'record-1', attributes: { 部门: '研发', 获奖批次: '第一批' } },
        { id: 'record-2', attributes: { 部门: '制造', 获奖批次: ' ' } },
      ],
      extraFields: [{ name: '获奖批次', value: '第一批' }],
      fieldDefinitions: [{ code: '部门' }],
    }),
    {
      name: '王创新',
      records: [
        { id: 'record-1', attributes: { 部门: '研发', 获奖批次: '第一批' } },
        { id: 'record-2', attributes: { 部门: '制造' } },
      ],
    },
  )
})

test('人员更新载荷保留无单元格值的新增列字段名', () => {
  assert.deepEqual(
    createParticipantUpdatePayload({
      name: '王创新',
      records: [{ id: 'record-1', attributes: {} }],
      customFields: [
        { id: 'custom-field-1', code: 'custom-field-1', label: ' 获奖批次 ', custom: true },
      ],
    }),
    {
      name: '王创新',
      records: [],
      fieldNames: ['获奖批次'],
    },
  )
})

test('人员更新载荷用表头列名保存新增列单元格值', () => {
  assert.deepEqual(
    createParticipantUpdatePayload({
      name: '王创新',
      records: [
        { id: 'record-1', attributes: { 'custom-field-1': ' 第一批 ' } },
      ],
      customFields: [
        { id: 'custom-field-1', code: 'custom-field-1', label: '获奖批次', custom: true },
      ],
    }),
    {
      name: '王创新',
      records: [
        { id: 'record-1', attributes: { 获奖批次: '第一批' } },
      ],
      fieldNames: ['获奖批次'],
    },
  )
})

test('人员更新载荷忽略全空新增记录并修剪单元格值', () => {
  assert.deepEqual(
    createParticipantUpdatePayload({
      name: ' 王创新 ',
      records: [
        { id: 'record-1', attributes: { 部门: ' 研发 ', 奖项: '' } },
        { id: undefined, attributes: { 部门: ' ', 奖项: undefined } },
        { id: undefined, attributes: { 部门: ' 终端 ' } },
      ],
    }),
    {
      name: '王创新',
      records: [
        { id: 'record-1', attributes: { 部门: '研发' } },
        { id: undefined, attributes: { 部门: '终端' } },
      ],
    },
  )
})

test('待排列表保留临时不出席人员，排座人员仅在全部列表显示', () => {
  const participants = [
    { ...personWithoutGroup, id: 'absent', attendanceStatus: 'TEMPORARILY_ABSENT' },
    { ...person, id: 'pending', attendanceStatus: 'PRESENT' },
    { ...person, id: 'assigned', assignedElementId: 'seat-1', attendanceStatus: 'PRESENT' },
  ]

  assert.deepEqual(
    filteredParticipants(participants, 'pending', '').map((participant) => participant.id),
    ['absent', 'pending'],
  )
  assert.deepEqual(
    filteredParticipants(participants, 'all', '创新奖').map((participant) => participant.id),
    ['pending', 'assigned'],
  )
})

test('分页后仅按当前页人员分组', () => {
  const people = [
    { ...person, id: 'first', primaryAttributes: { 批次: '第一批' } },
    { ...person, id: 'second', primaryAttributes: { 批次: '第二批' } },
    { ...person, id: 'third', primaryAttributes: { 批次: '第二批' } },
  ]
  const currentPage = paginateParticipants(people, 2, 2)

  assert.deepEqual(currentPage.map((participant) => participant.id), ['third'])
  assert.deepEqual(groupParticipants(currentPage, '批次').map((group) => group.label), ['第二批'])
})

test('座位摘要取首个动态值，拖放数据只使用人员 ID', () => {
  assert.equal(firstParticipantSummary(person, fieldDefinitions), '第二批')
  assert.deepEqual(participantDragData({ id: 'person-7', name: '不应携带' }), {
    type: 'text/participant-id',
    value: 'person-7',
    effectAllowed: 'move',
  })
})
