import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')
const normalize = (content) => content.replace(/\s+/g, ' ').trim()

test('新增弹窗将完整上下文和提交事件接入可注入动作', () => {
  const dialog = normalize(source('../src/components/AddParticipantDialog.vue'))
  const rawDialog = source('../src/components/AddParticipantDialog.vue')

  assert.match(dialog, /import \{ submitParticipant \} from '@\/utils\/participantActions'/)
  assert.match(
    dialog,
    /submitParticipant\(\{ addParticipant: meetingApi\.addParticipant, meetingId: props\.meetingId, form, targetElementId: props\.targetElementId, \}\)/,
  )
  assert.match(dialog, /<el-button type="primary" :loading="submitting" @click="submit">/)
  assert.match(dialog, /participant\?\.message/)
  assert.doesNotMatch(dialog, /hasDuplicateEmployeeNo/)
  assert.match(rawDialog, />\s*增加\s*</)
  assert.doesNotMatch(rawDialog, /placeholder=/)
  assert.match(rawDialog, /wx加6或7位数字/)
  assert.match(rawDialog, /:validate-on-rule-change="false"/)
  assert.match(rawDialog, /:validate-event="false"/)
  assert.doesNotMatch(rawDialog, /trigger:\s*(?:'|"|\[)/)
})

test('人员面板保持筛选、分页、分组和操作包装器的完整接线', () => {
  const rawPanel = source('../src/components/ParticipantPanel.vue')
  const panel = normalize(rawPanel)

  assert.match(panel, /from '@\/utils\/participantActions'/)
  assert.match(panel, /return filteredParticipants\(props\.participants, tab\.value, keyword\)/)
  assert.match(panel, /return paginateParticipants\(filtered\.value, currentPage\.value, pageSize\.value\)/)
  assert.match(panel, /return groupParticipants\(paged\.value, groupField\.value\)/)
  assert.match(panel, /currentPage\.value = resetParticipantPage\(\)/)
  assert.match(
    panel,
    /currentPage\.value = resolveParticipantPage\(currentPage\.value, total, pageSize\.value\)/,
  )
  assert.match(
    panel,
    /startParticipantDrag\(\{ event, participant, readonly: props\.readonly, locked: participant\.locked, onSelect: \(person\) => emit\('select', person\), onDragState: \(participantId\) => emit\('dragState', participantId\), \}\)/,
  )
  assert.match(
    panel,
    /dropParticipantToPending\(\{ event, readonly: props\.readonly, onUnassign: \(participantId\) => emit\('unassign', participantId\), onDrop: \(\) => \{ dropActive\.value = false \}, \}\)/,
  )
  assert.match(panel, /requestParticipantAttendance\(\{ readonly: props\.readonly, participant, emit \}\)/)
  assert.match(panel, /requestParticipantRemoval\(\{ readonly: props\.readonly, participant, emit \}\)/)
  assert.match(panel, /@drop="dropToPending"/)
  assert.match(panel, /@dragstart="dragStart\(\$event, person\)"/)
  assert.match(panel, /@click="changeAttendance\(person\)"/)
  assert.match(panel, /@click="removeParticipant\(person\)"/)
  assert.match(rawPanel, /participantStatus\(person\)/)
  assert.match(rawPanel, /participantStatusTitle\(person\)/)
  assert.match(rawPanel, /class="participant-status-dot"/)
  assert.match(rawPanel, /\.participant-status-dot\.status-assigned\s*\{[\s\S]*background:\s*#86efac;/)
  assert.match(rawPanel, /\.participant-status-dot\.status-absent\s*\{[\s\S]*background:\s*#fecaca;/)
  assert.match(rawPanel, /\.participant-status-dot\.status-pending\s*\{[\s\S]*background:\s*#fde68a;/)
  assert.doesNotMatch(rawPanel, /assigned-dot/)
})

test('座位画布保持悬浮信息和拖放包装器的完整接线', () => {
  const rawCanvas = source('../src/components/VenueCanvas.vue')
  const canvas = normalize(rawCanvas)

  assert.match(canvas, /participantTooltipRows\(participantFor\(element\.id\)\)/)
  assert.match(canvas, /tooltipSuppressed/)
  assert.match(canvas, /:disabled="seatTooltipDisabled"/)
  assert.match(
    canvas,
    /performParticipantDrag\(\{ event, participant, readonly: props\.readonly, locked: participant\.locked, onSelect: \(person\) => emit\('select', person\), onDragState: \(participantId\) => emit\('dragState', participantId\), \}\)/,
  )
  assert.match(canvas, /@dragstart="startParticipantDrag\(\$event, element\.id\)"/)
  assert.doesNotMatch(rawCanvas, /drop-copy/)
  assert.doesNotMatch(rawCanvas, /participantSeatSummary/)
})

test('排座工作台仅允许通用座位元素接收人员且保留只读与拖放能力', () => {
  const rawCanvas = source('../src/components/VenueCanvas.vue')
  const canvas = normalize(rawCanvas)
  const store = normalize(source('../src/stores/workspace.js'))
  const workbench = normalize(source('../src/views/WorkbenchView.vue'))

  assert.match(canvas, /const isSeat = \(element\) => element\.kind === 'SEAT'/)
  assert.match(canvas, /v-if="isSeat\(element\)"/)
  assert.match(canvas, /\{\{ seatLabelFor\(element\.id\) \}\}/)
  assert.match(canvas, /\{\{ element\.name \}\}/)
  assert.match(canvas, /@dragover="onDragOver\(\$event, element\)"/)
  assert.match(canvas, /@drop="onDrop\(\$event, element\)"/)
  assert.match(canvas, /@dblclick\.stop="onMarkerSeatToggle\(element\)"/)
  assert.match(canvas, /event\.button !== 2/)
  assert.match(canvas, /@wheel="onWheel"/)
  assert.doesNotMatch(canvas, /element\.(type|label|code|assignable|capacity|backgroundColor|rotation)/)

  assert.match(store, /target\?\.kind !== 'SEAT'/)
  assert.doesNotMatch(store, /target\?\.(assignable|capacity)/)
  assert.match(store, /if \(occupiedPerson && !originalTargetId\)/)
  assert.match(store, /occupiedPerson\.assignedElementId = originalTargetId/)
  assert.match(store, /person\.assignedElementId = undefined/)
  assert.match(store, /meetingApi\.saveAssignments/)
  assert.match(workbench, /:readonly="readonlyMode"/)
  assert.match(workbench, /v-if="workspace && !readonlyMode && fabReady"/)
  assert.match(workbench, /@assign="performAssign"/)
  assert.match(workbench, /@unassign="performUnassign"/)
  assert.match(workbench, /meetingApi\.versionSnapshot/)
  assert.match(workbench, /meetingApi\.updateMeetingLayout/)
  assert.match(workbench, /if \(readonlyMode\.value\) return/)
  assert.match(workbench, /resetAutoSaveTimer/)
  assert.match(workbench, /<h1>\{\{ workspace\.meeting\.layoutName \}\}<\/h1>/)
  assert.doesNotMatch(workbench, /venueApi/)
})

test('排号贴近所有元素区域两侧而不是只按座位外沿', () => {
  const canvas = source('../src/components/VenueCanvas.vue')

  assert.match(canvas, /rowLabelColumnBounds/)
  assert.match(canvas, /computeElementColumnBounds/)
  assert.match(canvas, /props\.workspace\.layout\.elements/)
  assert.match(canvas, /bounds\.minColumn/)
  assert.match(canvas, /bounds\.maxColumn/)
  assert.match(canvas, /white-space:\s*nowrap/)
  assert.match(canvas, /font-size:\s*clamp\(8px,\s*calc\(var\(--unit\) \* 0\.2\),\s*12px\)/)
  assert.match(canvas, /writing-mode:\s*horizontal-tb/)
  assert.doesNotMatch(canvas, /\[side\]: '-34px'/)
  assert.doesNotMatch(canvas, /rowLabelBounds\.value\.get\(rowLabel\.sourceRow\)/)
  assert.doesNotMatch(canvas, /\.filter\(isSeat\)[\s\S]*rowLabelColumnBounds/)
})
