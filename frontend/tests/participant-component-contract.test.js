import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')
const normalize = (content) => content.replace(/\s+/g, ' ').trim()

test('新增弹窗将完整上下文和提交事件接入可注入动作', () => {
  const dialog = normalize(source('../src/components/AddParticipantDialog.vue'))
  const rawDialog = source('../src/components/AddParticipantDialog.vue')
  const recordTable = source('../src/components/ParticipantRecordTable.vue')

  assert.match(dialog, /import \{ submitParticipant \} from '@\/utils\/participantActions'/)
  assert.match(rawDialog, /ParticipantRecordTable/)
  assert.match(rawDialog, /meetingApi\.previewImport/)
  assert.match(rawDialog, /applyPreviewRows\(preview\.value\)/)
  assert.match(rawDialog, /mergePreviewRowsIntoParticipantDraft/)
  assert.match(rawDialog, /addRow/)
  assert.match(rawDialog, /allowMultipleRows/)
  assert.match(
    dialog,
    /submitParticipant\(\{ addParticipant: meetingApi\.addParticipant, meetingId: props\.meetingId, form: row, targetElementId: addTargetElementId\(index\), \}\)/,
  )
  assert.match(dialog, /<el-button type="primary" :loading="submitting" @click="submit">/)
  assert.match(dialog, /results\.map\(\(participant\) => participant\?\.message\)/)
  assert.doesNotMatch(dialog, /hasDuplicateEmployeeNo/)
  assert.match(rawDialog, />\s*增加\s*</)
  assert.match(rawDialog, /title="新增人员"/)
  assert.match(rawDialog, /导入Excel/)
  assert.doesNotMatch(rawDialog, /<p[\s>]/)
  assert.doesNotMatch(rawDialog, /class="seat-add-note"/)
  assert.doesNotMatch(rawDialog, /placeholder=/)
  assert.doesNotMatch(rawDialog, /8位数字|1个小写字母加8位数字|wx加6或7位数字/)
  assert.doesNotMatch(rawDialog, /maxlength="9"/)
  assert.match(rawDialog, /:validate-on-rule-change="false"/)
  assert.match(recordTable, /:validate-event="false"/)
  assert.doesNotMatch(rawDialog, /trigger:\s*(?:'|"|\[)/)
})

test('人员面板保持筛选、分页、分组和操作包装器的完整接线', () => {
  const rawPanel = source('../src/components/ParticipantPanel.vue')
  const panel = normalize(rawPanel)

  assert.match(rawPanel, /SidePanelEmptyState/)
  assert.match(panel, /defineEmits\(\[[^\]]*'add'/)
  assert.match(panel, /defineEmits\(\[[^\]]*'unassignAll'/)
  assert.match(rawPanel, /@activate="emit\('add'\)"/)
  assert.match(panel, /from '@\/utils\/participantActions'/)
  assert.match(panel, /groupValueOptions/)
  assert.match(panel, /filterParticipantsByGroupValue/)
  assert.match(panel, /return filteredParticipants\(props\.participants, tab\.value, keyword\)/)
  assert.match(panel, /return filterParticipantsByGroupValue\(baseFiltered\.value, groupField\.value, groupValue\.value\)/)
  assert.match(panel, /return paginateParticipants\(filtered\.value, currentPage\.value, pageSize\.value\)/)
  assert.doesNotMatch(panel, /return groupParticipants\(paged\.value, groupField\.value\)/)
  assert.match(rawPanel, /class="group-filter-row"/)
  assert.match(rawPanel, /\.group-filter-row\s*\{[\s\S]*grid-template-columns:\s*minmax\(0,\s*1fr\) minmax\(0,\s*1fr\);/)
  assert.doesNotMatch(rawPanel, /v-if="groupField"[\s\S]*v-model="groupValue"/)
  assert.match(rawPanel, /@click="emit\('unassignAll'\)"/)
  assert.match(rawPanel, />\s*清空已排\s*</)
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

test('新增和编辑人员弹窗共用横向记录表格组件', () => {
  const addDialog = source('../src/components/AddParticipantDialog.vue')
  const editDialog = source('../src/components/EditParticipantDialog.vue')
  const table = source('../src/components/ParticipantRecordTable.vue')
  const fieldsUtil = source('../src/utils/participantFields.js')

  assert.match(addDialog, /import ParticipantRecordTable from '@\/components\/ParticipantRecordTable\.vue'/)
  assert.match(editDialog, /import ParticipantRecordTable from '@\/components\/ParticipantRecordTable\.vue'/)
  assert.match(table, /defineModel\('records'/)
  assert.match(table, /defineModel\('customFields'/)
  assert.match(table, /allowAddRows/)
  assert.match(table, /allowAddColumns/)
  assert.match(table, /addRecord/)
  assert.match(table, /addColumn/)
  assert.match(table, /removeCustomColumn/)
  assert.match(fieldsUtil, /export const MAX_PARTICIPANT_FIELD_COUNT = 15/)
  assert.match(table, /MAX_PARTICIPANT_FIELD_COUNT/)
  assert.match(table, /customFieldLimitReached/)
  assert.match(table, /已达到最多 15 个字段/)
  assert.match(table, /:disabled="customFieldLimitReached"/)
  assert.match(table, /validateCustomFields/)
  assert.match(table, /validateRecords/)
  assert.match(table, /scrollbar-always-on/)
  assert.match(table, /label="序号"/)
  assert.match(table, /\{\{ \$index \+ 1 \}\}/)
  assert.doesNotMatch(table, /记录 \{\{ \$index \+ 1 \}\}/)
  assert.match(table, /\.record-table-wrap\s+:deep\(\.el-table__header-wrapper th\)\s*\{[\s\S]*text-align:\s*center;/)
  assert.match(table, /\.record-table-wrap\s+:deep\(\.cell\)\s*\{[\s\S]*text-align:\s*center;/)
  assert.doesNotMatch(table, /获奖信息/)
  assert.doesNotMatch(table, /placeholder=/)
})

test('人员卡片悬浮操作层不拦截卡片主体选中点击', () => {
  const rawPanel = source('../src/components/ParticipantPanel.vue')
  const actionButtonBlock = rawPanel.match(/\.person-actions \.el-button\s*\{[^}]*\}/)?.[0] || ''
  const hoverActionButtonBlock = rawPanel.match(
    /\.person-card:hover \.person-actions \.el-button,\s*\n\.person-card:focus-within \.person-actions \.el-button\s*\{[^}]*\}/,
  )?.[0] || ''
  const hoverOverlayBlock = rawPanel.match(
    /\.person-card:hover \.person-actions,\s*\n\.person-card:focus-within \.person-actions\s*\{[^}]*\}/,
  )?.[0] || ''

  assert.match(rawPanel, /@click="emit\('select', person\)"/)
  assert.match(rawPanel, /\.person-actions\s*\{[\s\S]*pointer-events:\s*none;/)
  assert.match(actionButtonBlock, /pointer-events:\s*none;/)
  assert.match(hoverActionButtonBlock, /pointer-events:\s*auto;/)
  assert.doesNotMatch(hoverOverlayBlock, /pointer-events:\s*auto;/)
})

test('座位画布移除座位悬浮详情并统一拖拽预览', () => {
  const rawCanvas = source('../src/components/VenueCanvas.vue')
  const canvas = normalize(rawCanvas)
  const rawActions = source('../src/utils/participantActions.js')

  assert.doesNotMatch(rawCanvas, /<el-tooltip/)
  assert.doesNotMatch(rawCanvas, /participantTooltipRows/)
  assert.doesNotMatch(rawCanvas, /seatTooltipDisabled/)
  assert.doesNotMatch(rawCanvas, /tooltipSuppressed/)
  assert.doesNotMatch(rawCanvas, /tooltip-card/)
  assert.doesNotMatch(rawCanvas, /空座位，可拖入人员/)
  assert.match(
    canvas,
    /performParticipantDrag\(\{ event, participant, readonly: props\.readonly, locked: participant\.locked, onSelect: \(person\) => emit\('select', person\), onDragState: \(participantId\) => emit\('dragState', participantId\), \}\)/,
  )
  assert.match(canvas, /@dragstart="startParticipantDrag\(\$event, element\.id\)"/)
  assert.match(rawActions, /createParticipantDragPreview/)
  assert.match(rawActions, /setDragImage/)
  assert.match(rawActions, /participant-drag-preview/)
  assert.doesNotMatch(rawCanvas, /drop-copy/)
  assert.doesNotMatch(rawCanvas, /participantSeatSummary/)
})

test('拖拽人员时只高亮当前悬停座位', () => {
  const rawCanvas = source('../src/components/VenueCanvas.vue')
  const canvas = normalize(rawCanvas)

  assert.match(canvas, /'drop-target': dragTargetId === element\.id/)
  assert.match(canvas, /dragTargetId\.value = element\.id/)
  assert.match(rawCanvas, /\.seat-element\.drop-target\s*\{[\s\S]*border-color:\s*var\(--brand\)\s*!important;/)
  assert.doesNotMatch(rawCanvas, /'drop-ready':\s*draggingParticipantId/)
  assert.doesNotMatch(rawCanvas, /'swap-ready':\s*draggingParticipantId/)
  assert.doesNotMatch(rawCanvas, /\.canvas-scroll\.drag-active\s+\.seat-element\.drop-ready/)
  assert.doesNotMatch(rawCanvas, /\.canvas-scroll\.drag-active\s+\.seat-element\.swap-ready/)
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
  assert.match(store, /function unassignAll\(\)/)
  assert.match(store, /selectedParticipantId\.value = undefined/)
  assert.match(store, /meetingApi\.saveAssignments/)
  assert.match(workbench, /:readonly="readonlyMode"/)
  assert.match(workbench, /v-if="workspace && !readonlyMode && fabReady"/)
  assert.match(workbench, /@assign="performAssign"/)
  assert.match(workbench, /@unassign="performUnassign"/)
  assert.match(workbench, /@unassign-all="performUnassignAll"/)
  assert.match(workbench, /async function performUnassignAll\(\)/)
  assert.match(workbench, /ElMessageBox\.confirm\([\s\S]*清空已排人员/)
  assert.match(workbench, /store\.unassignAll\(\)/)
  assert.match(workbench, /meetingApi\.versionSnapshot/)
  assert.match(workbench, /meetingApi\.updateMeetingLayout/)
  assert.match(workbench, /if \(readonlyMode\.value\) \{ return \}/)
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
