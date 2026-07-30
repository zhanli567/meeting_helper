import assert from 'node:assert/strict'
import test from 'node:test'

import { mergePreviewRowsIntoParticipantDraft } from '../src/utils/participantFields.js'

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
