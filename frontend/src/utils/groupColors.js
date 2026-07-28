export const GROUP_COLOR_PALETTE = [
  { backgroundColor: '#FEF3C7', textColor: '#7C2D12' },
  { backgroundColor: '#DBEAFE', textColor: '#1D4ED8' },
  { backgroundColor: '#DCFCE7', textColor: '#166534' },
  { backgroundColor: '#FCE7F3', textColor: '#BE185D' },
  { backgroundColor: '#EDE9FE', textColor: '#6D28D9' },
  { backgroundColor: '#CCFBF1', textColor: '#0F766E' },
  { backgroundColor: '#FFEDD5', textColor: '#C2410C' },
  { backgroundColor: '#E0F2FE', textColor: '#0369A1' },
  { backgroundColor: '#FDE68A', textColor: '#854D0E' },
  { backgroundColor: '#D9F99D', textColor: '#3F6212' },
]

export function participantFieldValue(participant, fieldCode) {
  if (!participant || !fieldCode) return ''
  if (fieldCode === 'name') return participant.name || ''
  if (fieldCode === 'employeeNo') return participant.employeeNo || ''
  const value =
    participant.primaryAttributes?.[fieldCode] ||
    participant.attributeValues?.[fieldCode]?.find((item) => String(item || '').trim())
  return String(value || '').trim()
}

export function buildParticipantColorMap(participants, fieldCode, palette = GROUP_COLOR_PALETTE) {
  const colorsByValue = new Map()
  const colorsByParticipantId = new Map()
  if (!fieldCode || !palette.length) return colorsByParticipantId

  ;(participants || []).forEach((participant) => {
    const value = participantFieldValue(participant, fieldCode)
    if (!value) return
    if (!colorsByValue.has(value)) {
      colorsByValue.set(value, {
        ...palette[colorsByValue.size % palette.length],
        value,
      })
    }
    colorsByParticipantId.set(participant.id, colorsByValue.get(value))
  })
  return colorsByParticipantId
}
