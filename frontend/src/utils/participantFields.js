const fixedFieldCodes = new Set(['employeeNo', 'name'])

function nonEmptyValue(value) {
  if (value === undefined || value === null) return ''
  return String(value).trim()
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
  return {
    employeeNo: form.employeeNo,
    name: form.name,
    attributes: form.attributes || {},
    targetElementId,
  }
}

export function participantDragData(participant) {
  return {
    type: 'text/participant-id',
    value: participant.id,
    effectAllowed: 'move',
  }
}
