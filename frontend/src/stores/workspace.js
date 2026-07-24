import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiErrorMessage, downloadBlob } from '@/api/http'
import { meetingApi } from '@/api/meeting'
import { currentUser } from '@/auth/session'
const recentMeetingStorageKey = `meeting-helper:recent-meeting:${currentUser.tenantId}:${currentUser.id}`
function readRecentMeetingId() {
  if (typeof window === 'undefined') return ''
  try {
    return window.localStorage.getItem(recentMeetingStorageKey) || ''
  } catch {
    return ''
  }
}
function createWorkspaceStore() {
  const meetings = ref([])
  const activeMeetingId = ref(readRecentMeetingId())
  const workspace = ref()
  const loading = ref(false)
  const saving = ref(false)
  const selectedParticipantId = ref()
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
      if (!meetings.value.some((meeting) => meeting.id === activeMeetingId.value)) {
        rememberMeeting(meetings.value[0]?.id || '')
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
  async function switchMeeting(meetingId) {
    rememberMeeting(meetingId)
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
  async function assign(participantId, targetElementId) {
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
  async function unassign(participantId) {
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
  async function setLock(participantId, locked) {
    if (!workspace.value) return
    try {
      await meetingApi.setLock(workspace.value.plan.id, participantId, locked)
      await loadWorkspace()
      ElMessage.success(locked ? '已锁定座位' : '已解除锁定')
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
    }
  }
  async function removeParticipant(participantId) {
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
  async function exportPlan(type, versionId) {
    if (!workspace.value) return
    try {
      const data = await meetingApi.exportFile(workspace.value.meeting.id, type, versionId)
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
  function selectParticipant(participant) {
    selectedParticipantId.value = participant?.id
  }
  function rememberMeeting(meetingId) {
    activeMeetingId.value = meetingId
    if (typeof window === 'undefined') return
    try {
      if (meetingId) window.localStorage.setItem(recentMeetingStorageKey, meetingId)
      else window.localStorage.removeItem(recentMeetingStorageKey)
    } catch {
      // 浏览器禁用本地存储时仍保留当前会话内的最近会议。
    }
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
    rememberMeeting,
  }
}

const workspaceStore = reactive(createWorkspaceStore())

export function useWorkspaceStore() {
  return workspaceStore
}
