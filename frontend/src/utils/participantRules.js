export const PRESENT = 'PRESENT'
export const TEMPORARILY_ABSENT = 'TEMPORARILY_ABSENT'

export function isValidEmployeeNo(value) {
  return Boolean(value?.trim())
}

export function hasDuplicateEmployeeNo(value, participants = []) {
  const normalized = value?.trim().toLocaleLowerCase()
  if (!normalized) return false
  return participants.some(
    (participant) => participant.employeeNo?.trim().toLocaleLowerCase() === normalized,
  )
}

export function isTemporarilyAbsent(participant) {
  return participant?.attendanceStatus === TEMPORARILY_ABSENT
}

export function participantCanBeSeated(participant) {
  return Boolean(participant) && !isTemporarilyAbsent(participant)
}

export function attendingPendingCount(participants = []) {
  return participants.filter(
    (participant) => participantCanBeSeated(participant) && !participant.assignedElementId,
  ).length
}
