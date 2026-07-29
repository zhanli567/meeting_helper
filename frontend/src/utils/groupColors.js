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
  return participantFieldValues(participant, fieldCode)[0] || ''
}

export function participantFieldValues(participant, fieldCode) {
  if (!participant || !fieldCode) return []
  if (fieldCode === 'name') return cleanUniqueValues([participant.name])
  if (fieldCode === 'employeeNo') return cleanUniqueValues([participant.employeeNo])
  return cleanUniqueValues([
    participant.primaryAttributes?.[fieldCode],
    ...(participant.attributeValues?.[fieldCode] || []),
  ])
}

export function buildParticipantColorMap(participants, fieldCode, palette = GROUP_COLOR_PALETTE) {
  const colorsByValue = new Map()
  const colorsByParticipantId = new Map()
  if (!fieldCode || !palette.length) return colorsByParticipantId

  ;(participants || []).forEach((participant) => {
    const values = participantFieldValues(participant, fieldCode)
    if (!values.length) return
    const styles = values.map((value) => styleForValue(colorsByValue, value, palette))
    if (styles.length === 1) {
      colorsByParticipantId.set(participant.id, { ...styles[0], multiValue: false })
      return
    }
    colorsByParticipantId.set(participant.id, {
      backgroundColor: '#f8fafc',
      backgroundImage: multiValueGradient(styles),
      textColor: '#172033',
      value: values.join('、'),
      multiValue: true,
    })
  })
  return colorsByParticipantId
}

function cleanUniqueValues(values) {
  const seen = new Set()
  return (values || [])
    .map((value) => String(value || '').trim())
    .filter((value) => {
      if (!value || seen.has(value)) return false
      seen.add(value)
      return true
    })
}

function styleForValue(colorsByValue, value, palette) {
  if (!colorsByValue.has(value)) {
    colorsByValue.set(value, {
      ...palette[colorsByValue.size % palette.length],
      value,
    })
  }
  return colorsByValue.get(value)
}

function multiValueGradient(styles) {
  const step = 100 / styles.length
  const stops = styles.flatMap((style, index) => {
    const start = (index * step).toFixed(2)
    const end = ((index + 1) * step).toFixed(2)
    return [`${style.backgroundColor} ${start}%`, `${style.backgroundColor} ${end}%`]
  })
  return `linear-gradient(135deg, ${stops.join(', ')})`
}
