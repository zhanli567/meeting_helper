import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')
const normalize = (content) => content.replace(/\s+/g, ' ').trim()

test('新增弹窗将完整上下文和提交事件接入可注入动作', () => {
  const dialog = normalize(source('../src/components/AddParticipantDialog.vue'))

  assert.match(dialog, /import \{ submitParticipant \} from '@\/utils\/participantActions'/)
  assert.match(
    dialog,
    /submitParticipant\(\{ addParticipant: meetingApi\.addParticipant, meetingId: props\.meetingId, form, targetElementId: props\.targetElementId, \}\)/,
  )
  assert.match(dialog, /<el-button type="primary" :loading="submitting" @click="submit">/)
})

test('人员面板保持筛选、分页、分组和操作包装器的完整接线', () => {
  const panel = normalize(source('../src/components/ParticipantPanel.vue'))

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
})

test('座位画布保持动态摘要和拖放包装器的完整接线', () => {
  const rawCanvas = source('../src/components/VenueCanvas.vue')
  const canvas = normalize(rawCanvas)

  assert.match(
    canvas,
    /return firstParticipantSummary\(participantFor\(elementId\), props\.workspace\.fieldDefinitions\)/,
  )
  assert.match(
    canvas,
    /performParticipantDrag\(\{ event, participant, readonly: props\.readonly, locked: participant\.locked, onSelect: \(person\) => emit\('select', person\), onDragState: \(participantId\) => emit\('dragState', participantId\), \}\)/,
  )
  assert.match(canvas, /@dragstart="startParticipantDrag\(\$event, element\.id\)"/)
  const summarySpans = rawCanvas.match(
    /<span v-if="participantSeatSummary\(element\.id\)"[\s\S]*?<\/span>/g,
  )
  assert.equal(summarySpans?.length, 2)
  summarySpans.forEach((span) => {
    assert.match(span, /\{\{ participantSeatSummary\(element\.id\) \}\}/)
  })
})

test('排座工作台仅允许通用座位元素接收人员且保留只读与拖放能力', () => {
  const rawCanvas = source('../src/components/VenueCanvas.vue')
  const canvas = normalize(rawCanvas)
  const store = normalize(source('../src/stores/workspace.js'))
  const workbench = normalize(source('../src/views/WorkbenchView.vue'))

  assert.match(canvas, /const isSeat = \(element\) => element\.kind === 'SEAT'/)
  assert.match(canvas, /v-if="isSeat\(element\)"/)
  assert.match(canvas, /\{\{ element\.name \|\| '座位' \}\}/)
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
