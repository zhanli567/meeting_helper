import assert from 'node:assert/strict'
import test from 'node:test'

import Aurora from '../src/api/aurora.js'
import { meetingApi } from '../src/api/meeting.js'
import { venueApi } from '../src/api/venue.js'
import {
  COMMON_ELEMENT_SUGGESTIONS,
  DEFAULT_CANVAS,
  ELEMENT_KINDS,
  MIN_CANVAS_SIZE,
  emptyVenueInfo,
  normalizeVenueInfo,
  toCreateVenuePayload,
  toElementPayload,
} from '../src/utils/venueModel.js'

test('空场馆信息草稿覆盖全部固定字段', () => {
  assert.deepEqual(emptyVenueInfo(), {
    location: '',
    campus: '',
    mainScreenResolution: '',
    stageDimensions: '',
    manualCapacity: null,
    contactInfo: '',
    bookingUrl: '',
    meetingRoomFunctions: '',
    servicesProvided: '',
    description: '',
    remarks: '',
  })
})

test('场馆元素只生成通用字段', () => {
  assert.deepEqual(
    toElementPayload({
      kind: 'SEAT',
      name: '领导席',
      row: 2,
      column: 3,
      rowSpan: 1,
      columnSpan: 2,
      fillColor: '#ffffff',
      borderColor: '#8fb4e8',
      rotation: 90,
      code: 'A01',
    }),
    {
      kind: 'SEAT',
      name: '领导席',
      row: 2,
      column: 3,
      rowSpan: 1,
      columnSpan: 2,
      fillColor: '#ffffff',
      borderColor: '#8fb4e8',
    },
  )
})

test('场馆信息将空白可选字段规范化为 null', () => {
  assert.deepEqual(
    normalizeVenueInfo({
      location: '  A101  ',
      campus: ' ',
      mainScreenResolution: undefined,
      stageDimensions: null,
      manualCapacity: '',
      contactInfo: '  李四  ',
      bookingUrl: '  ',
      meetingRoomFunctions: '',
      servicesProvided: null,
      description: '  ',
      remarks: undefined,
    }),
    {
      location: 'A101',
      campus: null,
      mainScreenResolution: null,
      stageDimensions: null,
      manualCapacity: null,
      contactInfo: '李四',
      bookingUrl: null,
      meetingRoomFunctions: null,
      servicesProvided: null,
      description: null,
      remarks: null,
    },
  )
})

test('创建场馆载荷组合信息、画布与通用元素字段', () => {
  assert.deepEqual(
    toCreateVenuePayload(
      { location: ' A101 ', manualCapacity: '36' },
      {
        gridRows: 20,
        gridColumns: 30,
        elements: [
          {
            kind: 'SEAT',
            name: ' 座位 ',
            row: 1,
            column: 2,
            rowSpan: 1,
            columnSpan: 1,
            fillColor: '#fff',
            borderColor: '#000',
            capacity: 1,
          },
        ],
      },
    ),
    {
      location: 'A101',
      campus: null,
      mainScreenResolution: null,
      stageDimensions: null,
      manualCapacity: 36,
      contactInfo: null,
      bookingUrl: null,
      meetingRoomFunctions: null,
      servicesProvided: null,
      description: null,
      remarks: null,
      gridRows: 20,
      gridColumns: 30,
      elements: [
        {
          kind: 'SEAT',
          name: '座位',
          row: 1,
          column: 2,
          rowSpan: 1,
          columnSpan: 1,
          fillColor: '#fff',
          borderColor: '#000',
        },
      ],
    },
  )
})

test('场馆画布提供默认尺寸和最小边长', () => {
  assert.deepEqual(DEFAULT_CANVAS, { rows: 20, columns: 30 })
  assert.equal(MIN_CANVAS_SIZE, 5)
})

test('常用元素建议仅使用通用元素字段', () => {
  assert.equal(ELEMENT_KINDS.SEAT, 'SEAT')
  assert.equal(ELEMENT_KINDS.GENERIC, 'GENERIC')
  assert.deepEqual(
    COMMON_ELEMENT_SUGGESTIONS.map((suggestion) => suggestion.name),
    ['座位', '门', '墙', '桌子', '摄像', '舞台', '显示屏'],
  )
  assert.deepEqual(COMMON_ELEMENT_SUGGESTIONS[0], {
    name: '座位',
    kind: 'SEAT',
    fillColor: '#ffffff',
    borderColor: '#8fb4e8',
  })
  assert.ok(
    COMMON_ELEMENT_SUGGESTIONS.every(
      (suggestion) =>
        Object.keys(suggestion).every((key) =>
          ['name', 'kind', 'fillColor', 'borderColor'].includes(key),
        ) && ['SEAT', 'GENERIC'].includes(suggestion.kind),
    ),
  )
})

test('场馆 API 使用约定的 GET 和 POST 路径', async () => {
  const previousNetwork = Aurora.service.network
  const calls = []
  Aurora.service.network = {
    get(path, config) {
      calls.push({ method: 'get', path, config })
      return Promise.resolve({ data: { code: 0, data: path } })
    },
    post(path, data, config) {
      calls.push({ method: 'post', path, data, config })
      return Promise.resolve({ data: { code: 0, data: path } })
    },
  }

  try {
    assert.equal(await venueApi.list({ pageNum: 2 }), '/venues')
    assert.equal(
      await venueApi.locationAvailability('A101', 'v1'),
      '/venues/location-availability',
    )
    assert.equal(await venueApi.detail('v1'), '/venues/v1')
    assert.equal(await venueApi.layout('v1'), '/venues/v1/layout')
    assert.equal(await venueApi.create({ location: 'A101' }), '/venues/create')
    assert.equal(await venueApi.updateInfo('v1', { location: 'A102' }), '/venues/v1/info/update')
    assert.equal(await venueApi.updateLayout('v1', { elements: [] }), '/venues/v1/layout/update')
    assert.equal(await venueApi.remove('v1'), '/venues/v1/delete')
    assert.deepEqual(calls, [
      { method: 'get', path: '/venues', config: { params: { pageNum: 2 } } },
      {
        method: 'get',
        path: '/venues/location-availability',
        config: { params: { location: 'A101', excludeId: 'v1' } },
      },
      { method: 'get', path: '/venues/v1', config: undefined },
      { method: 'get', path: '/venues/v1/layout', config: undefined },
      { method: 'post', path: '/venues/create', data: { location: 'A101' }, config: undefined },
      {
        method: 'post',
        path: '/venues/v1/info/update',
        data: { location: 'A102' },
        config: undefined,
      },
      {
        method: 'post',
        path: '/venues/v1/layout/update',
        data: { elements: [] },
        config: undefined,
      },
      { method: 'post', path: '/venues/v1/delete', data: undefined, config: undefined },
    ])
  } finally {
    Aurora.service.network = previousNetwork
  }
})

test('会议创建从场馆快照接口发起，会议 API 不再暴露场馆方法', async () => {
  const previousNetwork = Aurora.service.network
  const calls = []
  Aurora.service.network = {
    post(path, data) {
      calls.push({ path, data })
      return Promise.resolve({ data: { code: 0, data: 'm1' } })
    },
  }

  try {
    assert.equal(await meetingApi.createMeeting('评审会', 'v1'), 'm1')
    assert.equal(await meetingApi.updateMeetingName('m1', '复盘会'), 'm1')
    assert.equal(await meetingApi.deleteMeeting('m1'), 'm1')
    assert.deepEqual(calls, [
      { path: '/meetings/create-from-venue', data: { name: '评审会', venueTemplateId: 'v1' } },
      { path: '/meetings/m1/name/update', data: { name: '复盘会' } },
      { path: '/meetings/m1/delete', data: undefined },
    ])
    assert.equal('venues' in meetingApi, false)
    assert.equal('venue' in meetingApi, false)
    assert.equal('createVenue' in meetingApi, false)
    assert.equal('updateVenue' in meetingApi, false)
    assert.equal('deleteVenue' in meetingApi, false)
  } finally {
    Aurora.service.network = previousNetwork
  }
})
