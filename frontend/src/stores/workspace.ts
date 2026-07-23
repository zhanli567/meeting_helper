import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { apiErrorMessage, downloadBlob } from '@/api/http'
import { meetingApi } from '@/api/meeting'
import type { MeetingSummary, Participant, Workspace } from '@/types/workspace'

export const useWorkspaceStore = defineStore('workspace', () => {
  const meetings = ref<MeetingSummary[]>([])
  const activeMeetingId = ref('')
  const workspace = ref<Workspace>()
  const loading = ref(false)
  const saving = ref(false)
  const selectedParticipantId = ref<string>()

  const selectedParticipant = computed(() =>
    workspace.value?.participants.find((person) => person.id === selectedParticipantId.value),
  )
  const assignedCount = computed(
    () => workspace.value?.participants.filter((person) => person.assignedElementId).length ?? 0,
  )
  const pendingCount = computed(
    () => workspace.value?.participants.filter((person) => !person.assignedElementId).length ?? 0,
  )

  async function initialize() {
    loading.value = true
    try {
      meetings.value = await meetingApi.meetings()
      if (!activeMeetingId.value && meetings.value.length) {
        activeMeetingId.value = meetings.value[0]!.id
      }
      if (activeMeetingId.value) {
        await loadWorkspace()
      }
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
    } finally {
      loading.value = false
    }
  }

  async function loadWorkspace() {
    if (!activeMeetingId.value) return
    workspace.value = await meetingApi.workspace(activeMeetingId.value)
  }

  async function switchMeeting(meetingId: string) {
    activeMeetingId.value = meetingId
    selectedParticipantId.value = undefined
    loading.value = true
    try {
      await loadWorkspace()
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
    } finally {
      loading.value = false
    }
  }

  async function assign(participantId: string, targetElementId: string) {
    if (!workspace.value) return false
    saving.value = true
    try {
      await meetingApi.assign(workspace.value.plan.id, participantId, targetElementId)
      await loadWorkspace()
      selectedParticipantId.value = participantId
      return true
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
      return false
    } finally {
      saving.value = false
    }
  }

  async function unassign(participantId: string) {
    if (!workspace.value) return false
    saving.value = true
    try {
      await meetingApi.unassign(workspace.value.plan.id, participantId)
      await loadWorkspace()
      return true
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
      return false
    } finally {
      saving.value = false
    }
  }

  async function setLock(participantId: string, locked: boolean) {
    if (!workspace.value) return
    try {
      await meetingApi.setLock(workspace.value.plan.id, participantId, locked)
      await loadWorkspace()
      ElMessage.success(locked ? '已锁定座位' : '已解除锁定')
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
    }
  }

  async function removeParticipant(participantId: string) {
    if (!workspace.value) return
    try {
      await meetingApi.deleteParticipant(workspace.value.meeting.id, participantId)
      selectedParticipantId.value = undefined
      await loadWorkspace()
      ElMessage.success('已从会议名单中移除')
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
    }
  }

  async function exportPlan(type: 'excel' | 'pdf') {
    if (!workspace.value) return
    try {
      const data = await meetingApi.exportFile(workspace.value.meeting.id, type)
      downloadBlob(
        data,
        `${workspace.value.meeting.name}-排座.${type === 'excel' ? 'xlsx' : 'pdf'}`,
        type === 'excel'
          ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
          : 'application/pdf',
      )
      ElMessage.success('导出完成')
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
    }
  }

  function selectParticipant(participant?: Participant) {
    selectedParticipantId.value = participant?.id
  }

  return {
    meetings,
    activeMeetingId,
    workspace,
    loading,
    saving,
    selectedParticipantId,
    selectedParticipant,
    assignedCount,
    pendingCount,
    initialize,
    loadWorkspace,
    switchMeeting,
    assign,
    unassign,
    setLock,
    removeParticipant,
    exportPlan,
    selectParticipant,
  }
})
