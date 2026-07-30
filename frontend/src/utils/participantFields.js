const fixedFieldCodes = new Set(['employeeNo', 'name'])

function nonEmptyValue(value) {
  if (value === undefined || value === null) return ''
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
  if (!normalizedKeyword) return true

  const values = [
    participant?.employeeNo,
    participant?.name,
    ...Object.values(participant?.attributeValues || {}).flat(),
  ]
  return values.some((value) => nonEmptyValue(value).toLocaleLowerCase().includes(normalizedKeyword))
}

export function filteredParticipants(participants, tab, keyword) {
  return (participants || []).filter((participant) => {
    if (tab === 'pending' && participant.assignedElementId) return false
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

export function groupableFields(fieldDefinitions) {
  return (fieldDefinitions || []).filter(
    (field) => field.filterable && !fixedFieldCodes.has(field.code),
  )
}

export function participantSummary(participant, fieldDefinitions, limit = 2) {
  const maximum = Math.max(0, limit)
  if (!maximum) return []

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
  return {
    employeeNo: form.employeeNo,
    name: form.name,
    attributes: {
      ...(form.attributes || {}),
      ...extraAttributes,
    },
    targetElementId,
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
    if (!name) throw new Error('请输入列名')
    const key = normalizeFieldName(name)
    if (existing.has(key) || seen.has(key)) throw new Error('该字段已存在，请使用其他列名')
    if (!value) throw new Error('请填写该人员在新增列中的值')
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
  if (fieldNames.length) payload.fieldNames = fieldNames
  return payload
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

function customFieldNameMap(fields = []) {
  const names = new Map()
  for (const field of fields || []) {
    const name = nonEmptyValue(field?.label)
    if (!field?.custom || !name) continue
    if (field.code) names.set(field.code, name)
    if (field.id) names.set(field.id, name)
  }
  return names
}

function normalizeRecordAttributes(attributes = {}, fieldNameByCode = new Map()) {
  const normalized = {}
  Object.entries(attributes || {}).forEach(([key, value]) => {
    const fieldName = nonEmptyValue(fieldNameByCode.get(key) || key)
    const fieldValue = nonEmptyValue(value)
    if (fieldName && fieldValue) normalized[fieldName] = fieldValue
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
