import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiErrorMessage, downloadBlob } from '@/api/http'
import { meetingApi } from '@/api/meeting'
import { currentUser } from '@/auth/session'
import {
  attendingPendingCount,
  participantCanBeSeated,
  TEMPORARILY_ABSENT,
} from '@/utils/participantRules'
const recentMeetingStorageKey = `meeting-helper:recent-meeting:${currentUser.tenantId}:${currentUser.id}`
function readRecentMeetingSession() {
  const fallback = { meetingId: '', versionKey: 'draft' }
  if (typeof window === 'undefined') {
    return fallback
  }
  try {
    const raw = window.localStorage.getItem(recentMeetingStorageKey) || ''
    if (!raw) {
      return fallback
    }
    if (!raw.trim().startsWith('{')) {
      return { meetingId: raw, versionKey: 'draft' }
    }
    const parsed = JSON.parse(raw)
    return {
      meetingId: typeof parsed.meetingId === 'string' ? parsed.meetingId : '',
      versionKey: typeof parsed.versionKey === 'string' ? parsed.versionKey : 'draft',
    }
  } catch {
    return fallback
  }
}
const exportTimestampPattern = 'yyyyMMddHHmm'
function padTime(value) {
  return String(value).padStart(2, '0')
}
function formatExportTimestamp(date = new Date()) {
  return `${date.getFullYear()}${padTime(date.getMonth() + 1)}${padTime(date.getDate())}${padTime(date.getHours())}${padTime(date.getMinutes())}`
}
function safeFilenamePart(value, fallback) {
  const name = String(value || fallback)
    .trim()
    .replace(/[\\/:*?"<>|]/g, '-')
    .replace(/\s+/g, ' ')
    .slice(0, 80)
  return name || fallback
}
function buildExportFilename(workspace, versionId, date = new Date()) {
  const meetingName = safeFilenamePart(workspace?.meeting?.name, '会议')
  const versionName = safeFilenamePart(
    versionId
      ? workspace?.versions?.find((version) => version.id === versionId)?.versionName
      : '草稿',
    '草稿',
  )
  const timestamp = formatExportTimestamp(date)
  return `${meetingName}-${versionName}-${timestamp}.xlsx`
}
function normalizeWorkspace(value) {
  return {
    ...value,
    fieldDefinitions: value.fieldDefinitions || [],
    participants: (value.participants || []).map((participant) => ({
      ...participant,
      primaryAttributes: participant.primaryAttributes || {},
      attributeValues: participant.attributeValues || {},
      records: participant.records || [],
    })),
  }
}
function createWorkspaceStore() {
  const initialRecentSession = readRecentMeetingSession()
  const meetings = ref([])
  const activeMeetingId = ref(initialRecentSession.meetingId)
  const recentVersionKey = ref(initialRecentSession.versionKey)
  const workspace = ref()
  const loading = ref(false)
  const saving = ref(false)
  const dirty = ref(false)
  const selectedParticipantId = ref()
  let saveAssignmentsPromise
  const selectedParticipant = computed(() =>
    workspace.value?.participants.find((person) => person.id === selectedParticipantId.value),
  )
  const assignedCount = computed(
    () => workspace.value?.participants.filter((person) => person.assignedElementId).length ?? 0,
  )
  const pendingCount = computed(
    () => attendingPendingCount(workspace.value?.participants),
  )
  async function initialize() {
    loading.value = true
    try {
      meetings.value = await meetingApi.meetings()
      if (!meetings.value.some((meeting) => meeting.id === activeMeetingId.value)) {
        rememberMeeting(meetings.value[0]?.id || '', 'draft')
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
    if (!activeMeetingId.value) {
      return
    }
    workspace.value = normalizeWorkspace(await meetingApi.workspace(activeMeetingId.value))
    dirty.value = false
  }
  function replaceWorkspace(value) {
    workspace.value = normalizeWorkspace(value)
    dirty.value = false
  }
  async function switchMeeting(meetingId) {
    rememberMeeting(meetingId, 'draft')
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
  function assign(participantId, targetElementId) {
    if (!workspace.value) {
      return false
    }
    const person = workspace.value.participants.find((value) => value.id === participantId)
    const target = workspace.value.layout.elements.find((value) => value.id === targetElementId)
    if (!participantCanBeSeated(person) || target?.kind !== 'SEAT') {
      return false
    }
    if (person.locked) {
      ElMessage.warning('该人员已锁定，无法移动')
      return false
    }
    const originalTargetId = person.assignedElementId
    if (originalTargetId === targetElementId) {
      return true
    }
    const targetItem = workspace.value.items.find((item) =>
      item.targetElementIds.includes(targetElementId),
    )
    if (targetItem && targetItem.type !== 'PERSON') {
      ElMessage.warning('目标座位已被设备、预留或禁用状态占用')
      return false
    }
    const occupiedPerson = workspace.value.participants.find(
      (value) => value.assignedElementId === targetElementId,
    )
    if (occupiedPerson?.locked || targetItem?.locked) {
      ElMessage.warning('目标座位已锁定')
      return false
    }
    if (occupiedPerson && !originalTargetId) {
      ElMessage.warning('待排人员只能拖入空座位')
      return false
    }

    const currentItem = ensurePersonItem(person)
    if (occupiedPerson) {
      const occupiedItem = ensurePersonItem(occupiedPerson)
      occupiedPerson.assignedElementId = originalTargetId
      occupiedItem.targetElementIds = [originalTargetId]
    }
    person.assignedElementId = targetElementId
    currentItem.targetElementIds = [targetElementId]
    selectedParticipantId.value = participantId
    dirty.value = true
    return true
  }

  function unassign(participantId) {
    if (!workspace.value) {
      return false
    }
    const person = workspace.value.participants.find((value) => value.id === participantId)
    const item = workspace.value.items.find(
      (value) => value.type === 'PERSON' && value.participantId === participantId,
    )
    if (!person?.assignedElementId) {
      return false
    }
    if (person.locked || item?.locked) {
      ElMessage.warning('该座位已锁定')
      return false
    }
    person.assignedElementId = undefined
    workspace.value.items = workspace.value.items.filter((value) => value !== item)
    dirty.value = true
    return true
  }

  async function saveAssignments({ silent = false } = {}) {
    if (saveAssignmentsPromise) {
      return saveAssignmentsPromise
    }
    if (!workspace.value || !dirty.value) {
      return true
    }
    saving.value = true
    saveAssignmentsPromise = (async () => {
      try {
        const assignments = workspace.value.participants
          .filter((person) => person.assignedElementId)
          .map((person) => ({
            participantId: person.id,
            targetElementId: person.assignedElementId,
          }))
        await meetingApi.saveAssignments(workspace.value.plan.id, assignments)
        await loadWorkspace()
        if (!silent) {
          ElMessage.success('排座草稿已保存')
        }
        return true
      } catch (error) {
        ElMessage.error(apiErrorMessage(error))
        return false
      } finally {
        saving.value = false
        saveAssignmentsPromise = undefined
      }
    })()
    return saveAssignmentsPromise
  }
  async function setLock(participantId, locked) {
    if (!workspace.value) {
      return
    }
    try {
      await meetingApi.setLock(workspace.value.plan.id, participantId, locked)
      await loadWorkspace()
      ElMessage.success(locked ? '已锁定座位' : '已解除锁定')
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
    }
  }
  async function removeParticipant(participantId) {
    if (!workspace.value) {
      return
    }
    try {
      await meetingApi.deleteParticipant(workspace.value.meeting.id, participantId)
      selectedParticipantId.value = undefined
      await loadWorkspace()
      ElMessage.success('已从会议名单中移除')
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
    }
  }
  async function updateAttendance(participantId, attendanceStatus) {
    if (!workspace.value) {
      return false
    }
    try {
      if (dirty.value) {
        const saved = await saveAssignments({ silent: true })
        if (!saved) {
          return false
        }
      }
      await meetingApi.updateAttendance(
        workspace.value.meeting.id,
        participantId,
        attendanceStatus,
      )
      if (attendanceStatus === TEMPORARILY_ABSENT && selectedParticipantId.value === participantId) {
        selectedParticipantId.value = undefined
      }
      await loadWorkspace()
      ElMessage.success(
        attendanceStatus === TEMPORARILY_ABSENT ? '已标记为临时不出席' : '已恢复出席',
      )
      return true
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
      return false
    }
  }
  async function exportPlan(versionId, options) {
    if (!workspace.value) {
      return false
    }
    try {
      const data = await meetingApi.exportExcel(workspace.value.meeting.id, versionId, options)
      downloadBlob(
        data,
        buildExportFilename(workspace.value, versionId),
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      )
      ElMessage.success('导出完成')
      return true
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
      return false
    }
  }
  function selectParticipant(participant) {
    selectedParticipantId.value = participant?.id
  }
  function ensurePersonItem(person) {
    let item = workspace.value.items.find(
      (value) => value.type === 'PERSON' && value.participantId === person.id,
    )
    if (!item) {
      item = {
        id:
          typeof crypto !== 'undefined' && crypto.randomUUID
            ? `local-${crypto.randomUUID()}`
            : `local-${Date.now()}-${Math.random()}`,
        type: 'PERSON',
        participantId: person.id,
        label: person.name,
        locked: false,
        backgroundColor: undefined,
        textColor: undefined,
        bold: false,
        targetElementIds: [],
      }
      workspace.value.items.push(item)
    }
    return item
  }
  function rememberMeeting(meetingId, versionKey = 'draft') {
    activeMeetingId.value = meetingId
    recentVersionKey.value = versionKey || 'draft'
    if (typeof window === 'undefined') {
      return
    }
    try {
      if (meetingId) {
        window.localStorage.setItem(
          recentMeetingStorageKey,
          JSON.stringify({ meetingId, versionKey: recentVersionKey.value }),
        )
      } else {
        window.localStorage.removeItem(recentMeetingStorageKey)
      }
    } catch {
      // 浏览器禁用本地存储时仍保留当前会话内的最近会议。
    }
  }
  return {
    meetings,
    activeMeetingId,
    recentVersionKey,
    workspace,
    loading,
    saving,
    dirty,
    selectedParticipantId,
    selectedParticipant,
    assignedCount,
    pendingCount,
    initialize,
    loadWorkspace,
    replaceWorkspace,
    switchMeeting,
    assign,
    unassign,
    saveAssignments,
    setLock,
    removeParticipant,
    updateAttendance,
    exportPlan,
    selectParticipant,
    rememberMeeting,
  }
}

const workspaceStore = reactive(createWorkspaceStore())

export function useWorkspaceStore() {
  return workspaceStore
}
