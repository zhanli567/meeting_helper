import assert from 'node:assert/strict'
import test from 'node:test'

import {
  MAX_PARTICIPANT_FIELD_COUNT,
  mergePreviewRowsIntoParticipantDraft,
} from '../src/utils/participantFields.js'

test('import preview rows append to non-empty manual rows and keep custom fields unique', () => {
  const result = mergePreviewRowsIntoParticipantDraft({
    records: [
      {
        employeeNo: '1001',
        name: 'Alice',
        attributes: { Department: 'Product' },
        createdInDialog: true,
      },
      {
        employeeNo: '',
        name: '',
        attributes: {},
        createdInDialog: true,
      },
    ],
    customFields: [{ id: 'Department', code: 'Department', label: 'Department', custom: true }],
    fieldDefinitions: [{ code: 'Batch', label: 'Batch' }],
    preview: {
      rows: [
        {
          employeeNo: '1002',
          name: 'Bob',
          attributes: { Department: 'Design', Batch: 'A', SeatZone: 'Front' },
          sourceRow: 2,
          expectedAction: 'append',
        },
        {
          employeeNo: '1001',
          name: 'Alice',
          attributes: { Department: 'Product' },
          sourceRow: 3,
        },
      ],
    },
  })

  assert.deepEqual(
    result.records.map((row) => [row.employeeNo, row.name, row.attributes]),
    [
      ['1001', 'Alice', { Department: 'Product' }],
      ['1002', 'Bob', { Department: 'Design', Batch: 'A', SeatZone: 'Front' }],
    ],
  )
  assert.deepEqual(
    result.customFields.map((field) => field.label),
    ['Department', 'SeatZone'],
  )
  assert.equal(result.appendedCount, 1)
  assert.equal(result.skippedDuplicateCount, 1)
})

test('import preview rows cannot append custom fields beyond meeting field limit', () => {
  const fieldDefinitions = Array.from(
    { length: MAX_PARTICIPANT_FIELD_COUNT - 1 },
    (_, index) => ({ code: `field-${index + 1}`, label: `字段${index + 1}` }),
  )

  assert.throws(
    () => mergePreviewRowsIntoParticipantDraft({
      fieldDefinitions,
      preview: {
        rows: [
          {
            employeeNo: '1003',
            name: 'Carol',
            attributes: { 新字段一: 'A', 新字段二: 'B' },
          },
        ],
      },
    }),
    /最多 15 个字段/,
  )
})
