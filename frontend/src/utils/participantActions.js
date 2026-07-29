import {
  createParticipantPayload,
  createParticipantUpdatePayload,
  participantDragData,
} from './participantFields.js'
import { isTemporarilyAbsent } from './participantRules.js'

export function submitParticipant({ addParticipant, meetingId, form, targetElementId }) {
  return addParticipant(meetingId, createParticipantPayload(form, targetElementId))
}

export function updateParticipantDetails({ updateParticipant, meetingId, participantId, form }) {
  return updateParticipant(meetingId, participantId, createParticipantUpdatePayload(form))
}

export function canStartParticipantDrag({ readonly, locked, participant }) {
  return !readonly && !locked && !isTemporarilyAbsent(participant)
}

export function createParticipantDragPreview(participant) {
  if (typeof document === 'undefined') return undefined
  const preview = document.createElement('div')
  preview.className = 'participant-drag-preview'
  preview.textContent = participant?.employeeNo
    ? `${participant.name || '参会人员'} · ${participant.employeeNo}`
    : participant?.name || '参会人员'
  document.body.appendChild(preview)
  return preview
}

export function disposeParticipantDragPreview(preview) {
  preview?.parentElement?.removeChild(preview)
}

function scheduleParticipantDragPreviewDisposal(preview) {
  if (!preview) return
  const cleanup = () => disposeParticipantDragPreview(preview)
  if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    window.requestAnimationFrame(cleanup)
    return
  }
  setTimeout(cleanup, 0)
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
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = dragData.effectAllowed
    if (typeof event.dataTransfer.setDragImage === 'function') {
      const preview = createParticipantDragPreview(participant)
      if (preview) {
        event.dataTransfer.setDragImage(preview, 18, 18)
        scheduleParticipantDragPreviewDisposal(preview)
      }
    }
  }
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
