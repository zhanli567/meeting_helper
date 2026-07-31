import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')
const normalize = (content) => content.replace(/\s+/g, ' ').trim()

test('workspace save waits for an in-flight save instead of treating duplicate clicks as success', () => {
  const store = source('../src/stores/workspace.js')

  assert.match(store, /let saveAssignmentsPromise/)
  assert.match(normalize(store), /if \(saveAssignmentsPromise\) \{ return saveAssignmentsPromise \}/)
  assert.match(store, /saveAssignmentsPromise = \(async \(\) =>/)
  assert.match(store, /finally\s*\{[\s\S]*saving\.value = false[\s\S]*saveAssignmentsPromise = undefined/)
})

test('workbench locks duplicate async actions at page, export, version, and participant levels', () => {
  const workbench = normalize(source('../src/views/WorkbenchView.vue'))

  assert.match(workbench, /const exporting = ref\(false\)/)
  assert.match(workbench, /const restoringDraft = ref\(false\)/)
  assert.match(workbench, /const switchingMeetingId = ref\(''\)/)
  assert.match(workbench, /const switchingVersionKey = ref\(''\)/)
  assert.match(workbench, /const participantBusyActions = ref\(\{\}\)/)
  assert.match(workbench, /function participantBusyAction\(participantId\)/)
  assert.match(workbench, /async function runParticipantBusyAction\(participantId, action, task\)/)
  assert.match(workbench, /if \(exporting\.value\) \{ return \}/)
  assert.match(workbench, /exporting\.value = true/)
  assert.match(workbench, /:loading="restoringDraft"/)
  assert.match(workbench, /:loading="exporting"/)
  assert.match(workbench, /:submitting="exporting"/)
  assert.match(workbench, /:busy-actions="participantBusyActions"/)
})

test('participant panel receives per-person busy actions and shows loading only on the active button', () => {
  const panel = normalize(source('../src/components/ParticipantPanel.vue'))

  assert.match(panel, /busyActions: \{ type: Object, default: \(\) => \(\{\}\) \}/)
  assert.match(panel, /function participantAction\(person\)/)
  assert.match(panel, /:draggable="!readonly && !participantAction\(person\)/)
  assert.match(panel, /:loading="participantAction\(person\) === 'edit'"/)
  assert.match(panel, /:loading="participantAction\(person\) === 'attendance'"/)
  assert.match(panel, /:loading="participantAction\(person\) === 'remove'"/)
  assert.match(panel, /:disabled="Boolean\(participantAction\(person\)\)"/)
})

test('participant dialogs guard repeated download parse and submit clicks while disabling form areas', () => {
  const addDialog = normalize(source('../src/components/AddParticipantDialog.vue'))
  const editDialog = normalize(source('../src/components/EditParticipantDialog.vue'))

  assert.match(addDialog, /if \(templateDownloading\.value\) \{ return \}/)
  assert.match(addDialog, /if \(previewing\.value\) \{ return \}/)
  assert.match(addDialog, /if \(submitting\.value\) \{ return \}/)
  assert.match(addDialog, /:disabled="submitting"/)
  assert.match(addDialog, /v-loading="submitting"/)
  assert.match(addDialog, /:disabled="previewing \|\| submitting"/)
  assert.match(addDialog, /:disabled="previewing \|\| submitting \|\| templateDownloading"/)
  assert.match(addDialog, /:disabled="previewing \|\| !file"/)

  assert.match(editDialog, /if \(submitting\.value\) \{ return \}/)
  assert.match(editDialog, /:disabled="submitting"/)
  assert.match(editDialog, /v-loading="submitting"/)
})

test('venue library row operations use row-scoped busy states', () => {
  const library = normalize(source('../src/views/VenueLibraryView.vue'))

  assert.match(library, /const venueRowActions = ref\(\{\}\)/)
  assert.match(library, /function venueRowAction\(venueId\)/)
  assert.match(library, /async function runVenueRowAction\(venueId, action, task\)/)
  assert.match(library, /venueRowAction\(row\.id\) === 'detail'/)
  assert.match(library, /venueRowAction\(row\.id\) === 'meeting'/)
  assert.match(library, /venueRowAction\(row\.id\) === 'delete'/)
  assert.match(library, /:disabled="Boolean\(venueRowAction\(row\.id\)\)"/)
})

test('secondary dialogs do not emit duplicate submit events while parent submitting is true', () => {
  const publishDialog = normalize(source('../src/components/PublishVersionDialog.vue'))
  const regionDialog = normalize(source('../src/components/RegionCreateDialog.vue'))
  const exportDialog = normalize(source('../src/components/ExportOptionsDialog.vue'))

  assert.match(publishDialog, /if \(props\.submitting\) \{ return \}/)
  assert.match(publishDialog, /:disabled="submitting"/)
  assert.match(regionDialog, /if \(props\.submitting\) \{ return \}/)
  assert.match(regionDialog, /:disabled="submitting"/)
  assert.match(exportDialog, /if \(props\.submitting\) \{ return \}/)
  assert.match(exportDialog, /:loading="submitting"/)
})
