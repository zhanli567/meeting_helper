const fixedFieldCodes = new Set(['employeeNo', 'name'])

function nonEmptyValue(value) {
  if (value === undefined || value === null) {
    return ''
  }
  return String(value).trim()
}

function normalizeFieldName(value) {
  return nonEmptyValue(value).toLocaleLowerCase()
}

export function primaryFieldValue(participant, fieldName) {
  return nonEmptyValue(participant?.primaryAttributes?.[fieldName]) || '未填写'
}

export function matchesParticipant(participant, keyword) {
  const normalizedKeyword = nonEmptyValue(keyword).toLocaleLowerCase()
  if (!normalizedKeyword) {
    return true
  }

  const values = [
    participant?.employeeNo,
    participant?.name,
    ...Object.values(participant?.attributeValues || {}).flat(),
  ]
  return values.some((value) => nonEmptyValue(value).toLocaleLowerCase().includes(normalizedKeyword))
}

export function filteredParticipants(participants, tab, keyword) {
  return (participants || []).filter((participant) => {
    if (tab === 'pending' && participant.assignedElementId) {
      return false
    }
    return matchesParticipant(participant, keyword)
  })
}

export function paginateParticipants(participants, currentPage, pageSize) {
  const start = (currentPage - 1) * pageSize
  return (participants || []).slice(start, start + pageSize)
}

export function groupParticipants(participants, fieldName) {
  const groups = new Map()
  participants.forEach((participant) => {
    const label = primaryFieldValue(participant, fieldName)
    const people = groups.get(label) || []
    people.push(participant)
    groups.set(label, people)
  })
  return Array.from(groups, ([key, people]) => ({ key, label: key, people }))
}

function participantGroupFieldValues(participant, fieldName) {
  const values = [
    participant?.primaryAttributes?.[fieldName],
    ...[participant?.attributeValues?.[fieldName]].flat(),
  ]
    .map(nonEmptyValue)
    .filter(Boolean)
  const dedupedValues = [...new Set(values)]
  return dedupedValues.length ? dedupedValues : ['未填写']
}

export function groupValueOptions(participants, fieldName) {
  if (!fieldName) {
    return []
  }
  const groups = new Map()
  for (const participant of participants || []) {
    for (const label of participantGroupFieldValues(participant, fieldName)) {
      groups.set(label, (groups.get(label) || 0) + 1)
    }
  }
  return Array.from(groups, ([label, count]) => ({ value: label, label, count }))
}

export function filterParticipantsByGroupValue(participants, fieldName, groupValue) {
  if (!fieldName || !groupValue) {
    return participants || []
  }
  return (participants || []).filter(
    (participant) => participantGroupFieldValues(participant, fieldName).includes(groupValue),
  )
}

export function groupableFields(fieldDefinitions) {
  return (fieldDefinitions || []).filter(
    (field) => field.filterable && !fixedFieldCodes.has(field.code),
  )
}

export function participantSummary(participant, fieldDefinitions, limit = 2) {
  const maximum = Math.max(0, limit)
  if (!maximum) {
    return []
  }

  return (fieldDefinitions || [])
    .filter((field) => !fixedFieldCodes.has(field.code))
    .map((field) => nonEmptyValue(participant?.primaryAttributes?.[field.code]))
    .filter(Boolean)
    .slice(0, maximum)
}

export function firstParticipantSummary(participant, fieldDefinitions) {
  return participantSummary(participant, fieldDefinitions, 1)[0]
}

export function createParticipantPayload(form, targetElementId) {
  const extraAttributes = normalizeExtraFields(form.extraFields, form.fieldDefinitions)
  const customFieldNames = customFieldNameMap(form.customFields)
  return {
    employeeNo: nonEmptyValue(form.employeeNo),
    name: nonEmptyValue(form.name),
    attributes: {
      ...normalizeRecordAttributes(form.attributes || {}, customFieldNames),
      ...extraAttributes,
    },
    targetElementId,
  }
}

export function mergePreviewRowsIntoParticipantDraft({
  records = [],
  customFields = [],
  fieldDefinitions = [],
  preview,
} = {}) {
  const fieldKeys = knownFieldKeys(fieldDefinitions, customFields)
  const nextCustomFields = [...(customFields || [])]
  const currentRows = (records || [])
    .filter((row) => !isBlankParticipantDraftRow(row))
    .map((row) => cloneParticipantDraftRow(row))
  const seen = new Set(currentRows.map(participantDraftRowSignature))
  let appendedCount = 0
  let skippedDuplicateCount = 0

  for (const row of preview?.rows || []) {
    Object.keys(row?.attributes || {}).forEach((fieldName) => {
      const label = nonEmptyValue(fieldName)
      const key = normalizeFieldName(label)
      if (!label || fieldKeys.has(key)) {
        return
      }
      nextCustomFields.push({
        id: label,
        code: label,
        label,
        custom: true,
      })
      fieldKeys.add(key)
    })

    const nextRow = cloneParticipantDraftRow({
      employeeNo: row?.employeeNo,
      name: row?.name,
      attributes: row?.attributes,
      sourceRow: row?.sourceRow,
      expectedAction: row?.expectedAction,
      createdInDialog: true,
    })
    const signature = participantDraftRowSignature(nextRow)
    if (seen.has(signature)) {
      skippedDuplicateCount += 1
      continue
    }
    seen.add(signature)
    currentRows.push(nextRow)
    appendedCount += 1
  }

  return {
    records: currentRows.length
      ? currentRows
      : [{ employeeNo: '', name: '', attributes: {}, createdInDialog: true }],
    customFields: nextCustomFields,
    appendedCount,
    skippedDuplicateCount,
  }
}

export function normalizeExtraFields(extraFields = [], existingFields = []) {
  const existing = new Set(
    (existingFields || []).map((field) => normalizeFieldName(field.code || field.label)),
  )
  const seen = new Set()
  const attributes = {}

  for (const row of extraFields || []) {
    const name = nonEmptyValue(row?.name)
    const value = nonEmptyValue(row?.value)
    if (!name) {
      throw new Error('请输入列名')
    }
    const key = normalizeFieldName(name)
    if (existing.has(key) || seen.has(key)) {
      throw new Error('该字段已存在，请使用其他列名')
    }
    if (!value) {
      throw new Error('请填写该人员在新增列中的值')
    }
    seen.add(key)
    attributes[name] = value
  }

  return attributes
}

export function createParticipantUpdatePayload(form) {
  const fieldNameByCode = customFieldNameMap(form.customFields)
  const fieldNames = normalizeCustomFieldNames(form.customFields)
  const records = (form.records?.length ? form.records : [{ attributes: {} }])
    .map((record) => ({
      id: record.id,
      attributes: normalizeRecordAttributes(record.attributes, fieldNameByCode),
    }))
    .filter((record) => Object.keys(record.attributes).length)
  const payload = {
    name: nonEmptyValue(form.name),
    records,
  }
  if (fieldNames.length) {
    payload.fieldNames = fieldNames
  }
  return payload
}

export function participantRecordHasValue(record) {
  return Object.values(record?.attributes || {}).some((value) => nonEmptyValue(value))
}

export function canAddParticipantRecord(records = []) {
  return !(records || []).some(
    (record) => record?.createdInDialog && !participantRecordHasValue(record),
  )
}

function normalizeCustomFieldNames(fields = []) {
  const seen = new Set()
  const names = []
  for (const field of fields || []) {
    const name = nonEmptyValue(field?.label || field?.code)
    const key = normalizeFieldName(name)
    if (name && !seen.has(key)) {
      seen.add(key)
      names.push(name)
    }
  }
  return names
}

function knownFieldKeys(fieldDefinitions = [], customFields = []) {
  return new Set(
    [...(fieldDefinitions || []), ...(customFields || [])]
      .flatMap((field) => [field?.code, field?.label, field?.id])
      .map(normalizeFieldName)
      .filter(Boolean),
  )
}

function cloneParticipantDraftRow(row = {}) {
  return {
    ...row,
    employeeNo: nonEmptyValue(row.employeeNo),
    name: nonEmptyValue(row.name),
    attributes: { ...(row.attributes || {}) },
    createdInDialog: row.createdInDialog !== false,
  }
}

function isBlankParticipantDraftRow(row = {}) {
  return (
    !nonEmptyValue(row.employeeNo) &&
    !nonEmptyValue(row.name) &&
    !Object.values(row.attributes || {}).some(nonEmptyValue)
  )
}

function participantDraftRowSignature(row = {}) {
  const attributes = Object.entries(row.attributes || {})
    .map(([key, value]) => [nonEmptyValue(key), nonEmptyValue(value)])
    .filter(([key, value]) => key && value)
    .sort(([left], [right]) => left.localeCompare(right))
  return JSON.stringify([
    normalizeFieldName(row.employeeNo),
    nonEmptyValue(row.name),
    attributes,
  ])
}

function customFieldNameMap(fields = []) {
  const names = new Map()
  for (const field of fields || []) {
    const name = nonEmptyValue(field?.label)
    if (!field?.custom || !name) {
      continue
    }
    if (field.code) {
      names.set(field.code, name)
    }
    if (field.id) {
      names.set(field.id, name)
    }
  }
  return names
}

function normalizeRecordAttributes(attributes = {}, fieldNameByCode = new Map()) {
  const normalized = {}
  Object.entries(attributes || {}).forEach(([key, value]) => {
    const fieldName = nonEmptyValue(fieldNameByCode.get(key) || key)
    const fieldValue = nonEmptyValue(value)
    if (fieldName && fieldValue) {
      normalized[fieldName] = fieldValue
    }
  })
  return normalized
}

export function participantDragData(participant) {
  return {
    type: 'text/participant-id',
    value: participant.id,
    effectAllowed: 'move',
  }
}
