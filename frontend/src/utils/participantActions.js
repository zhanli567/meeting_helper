import { createParticipantPayload, participantDragData } from './participantFields.js'
import { isTemporarilyAbsent } from './participantRules.js'

export function submitParticipant({ addParticipant, meetingId, form, targetElementId }) {
  return addParticipant(meetingId, createParticipantPayload(form, targetElementId))
}

export function canStartParticipantDrag({ readonly, locked, participant }) {
  return !readonly && !locked && !isTemporarilyAbsent(participant)
}

export function startParticipantDrag({
  event,
  participant,
  readonly,
  locked,
  onSelect,
  onDragState,
}) {
  if (!canStartParticipantDrag({ readonly, locked, participant })) {
    event.preventDefault()
    return false
  }
  const dragData = participantDragData(participant)
  event.dataTransfer?.setData(dragData.type, dragData.value)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = dragData.effectAllowed
  onSelect(participant)
  onDragState(participant.id)
  return true
}

export function dropParticipantToPending({ event, readonly, onUnassign, onDrop }) {
  if (readonly) return false
  event.preventDefault()
  onDrop()
  const participantId = event.dataTransfer?.getData('text/participant-id')
  if (!participantId) return false
  onUnassign(participantId)
  return true
}

export function resetParticipantPage() {
  return 1
}

export function resolveParticipantPage(currentPage, total, pageSize) {
  const lastPage = Math.max(1, Math.ceil(total / pageSize))
  return currentPage > lastPage ? lastPage : currentPage
}

export function requestParticipantAttendance({ readonly, participant, emit }) {
  if (readonly) return false
  emit('attendance', participant, isTemporarilyAbsent(participant) ? 'PRESENT' : 'TEMPORARILY_ABSENT')
  return true
}

export function requestParticipantRemoval({ readonly, participant, emit }) {
  if (readonly) return false
  emit('remove', participant)
  return true
}
