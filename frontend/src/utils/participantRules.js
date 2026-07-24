export const PRESENT = 'PRESENT'
export const TEMPORARILY_ABSENT = 'TEMPORARILY_ABSENT'

export function isValidEmployeeNo(value) {
  return /^(?:\d{8}|[a-z]\d{8})$/.test(value?.trim() || '')
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
