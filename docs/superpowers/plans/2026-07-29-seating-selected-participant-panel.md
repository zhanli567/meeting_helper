# Seating Selected Participant Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace obstructive seat hover details with a selected-participant floating information panel, pin color legend to the lower-left canvas corner, and improve participant drag preview.

**Architecture:** Keep selection state in the existing workspace store. Add one focused frontend component for the selected participant panel, simplify `VenueCanvas.vue` by removing seat tooltip rendering, and keep drag image creation in `participantActions.js` so list-to-seat and seat-to-seat dragging share one preview style.

**Tech Stack:** Vue 3, Element Plus, Node built-in test runner, existing frontend utility tests.

## Global Constraints

- Work on `main`; do not create a new branch.
- Every commit message must be Chinese.
- Push `main` after the implementation is verified.
- The selected participant floating panel only appears in seating mode.
- Seat hover tooltip is removed instead of reduced.
- Color legend is fixed at the lower-left of the canvas body.
- Drag target seats use highlight only and do not render helper text.

---

## File Structure

- Create `frontend/src/components/SelectedParticipantInfo.vue`: collapsible selected-person information panel.
- Modify `frontend/src/views/WorkbenchView.vue`: mount the new panel in seating mode, pass seat labels, and keep color legend lower-left.
- Modify `frontend/src/components/VenueCanvas.vue`: remove seat tooltip markup and tooltip state.
- Modify `frontend/src/components/GroupColorLegend.vue`: remove vertical dragging and pin with bottom-left CSS.
- Modify `frontend/src/utils/participantActions.js`: add shared drag preview generation.
- Modify `frontend/tests/participant-component-contract.test.js`: update hover and drag contract.
- Modify `frontend/tests/group-colors.test.js`: update fixed lower-left legend contract.
- Create `frontend/tests/selected-participant-info-contract.test.js`: assert the new component displays fixed and dynamic participant details.
- Modify `CHANGELOG.md`: add a Chinese changelog entry in chronological order.

---

### Task 1: Tests for the New Interaction Contract

**Files:**
- Modify: `frontend/tests/participant-component-contract.test.js`
- Modify: `frontend/tests/group-colors.test.js`
- Create: `frontend/tests/selected-participant-info-contract.test.js`

**Interfaces:**
- Produces test expectations that `VenueCanvas.vue` has no `el-tooltip` seat wrapper.
- Produces test expectations that `SelectedParticipantInfo.vue` accepts `participant`, `fieldDefinitions`, `seatLabel`, `collapsed`, and `readonly`.

- [ ] **Step 1: Write the failing canvas and drag contract assertions**

Update `frontend/tests/participant-component-contract.test.js` so the canvas test asserts:

```js
assert.doesNotMatch(rawCanvas, /<el-tooltip/)
assert.doesNotMatch(rawCanvas, /participantTooltipRows/)
assert.doesNotMatch(rawCanvas, /tooltip-card/)
assert.match(canvas, /createParticipantDragPreview/)
assert.doesNotMatch(rawCanvas, /空座位，可拖入人员/)
```

Also assert `ParticipantPanel.vue` and `VenueCanvas.vue` both use the same drag helper from `participantActions.js`.

- [ ] **Step 2: Write the failing color legend contract assertions**

Update `frontend/tests/group-colors.test.js` so the legend test asserts:

```js
assert.doesNotMatch(source, /legendTop/)
assert.doesNotMatch(source, /startLegendDrag/)
assert.match(source, /bottom:\s*14px;/)
assert.match(source, /left:\s*14px;/)
assert.match(source, /closeColorPopovers\(\)[\s\S]*emit\('update:collapsed'/)
```

- [ ] **Step 3: Add the selected participant panel contract test**

Create `frontend/tests/selected-participant-info-contract.test.js`:

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('选中人员信息窗展示完整人员信息并支持收缩编辑', async () => {
  const source = await readFile(new URL('../src/components/SelectedParticipantInfo.vue', import.meta.url), 'utf8')

  assert.match(source, /defineProps\(\{[\s\S]*participant/)
  assert.match(source, /fieldDefinitions/)
  assert.match(source, /seatLabel/)
  assert.match(source, /collapsed/)
  assert.match(source, /defineEmits\(\['update:collapsed', 'edit'\]\)/)
  assert.match(source, /人员信息/)
  assert.match(source, /工号/)
  assert.match(source, /座位/)
  assert.match(source, /状态/)
  assert.match(source, /记录 \{\{ index \+ 1 \}\}/)
  assert.doesNotMatch(source, /获奖信息/)
})
```

- [ ] **Step 4: Run tests to verify they fail**

Run:

```powershell
cd frontend
npm.cmd test -- tests/participant-component-contract.test.js tests/group-colors.test.js tests/selected-participant-info-contract.test.js
```

Expected: fail because the panel does not exist and the old tooltip/legend behavior still exists.

---

### Task 2: Selected Participant Panel and Workbench Mount

**Files:**
- Create: `frontend/src/components/SelectedParticipantInfo.vue`
- Modify: `frontend/src/views/WorkbenchView.vue`

**Interfaces:**
- Produces component props:

```js
participant: Object
fieldDefinitions: Array
seatLabel: String
collapsed: Boolean
readonly: Boolean
```

- Produces events:

```js
emit('update:collapsed', value)
emit('edit', participant)
```

- [ ] **Step 1: Implement `SelectedParticipantInfo.vue`**

The component computes status text from `attendanceStatus` and `assignedElementId`, groups dynamic record rows by existing `fieldDefinitions`, and renders fixed rows for name, employee number, status, and seat label.

- [ ] **Step 2: Mount the panel in `WorkbenchView.vue`**

Add:

```js
const participantInfoCollapsed = ref(false)
const selectedParticipantSeatLabel = computed(() => {
  const elementId = store.selectedParticipant?.assignedElementId
  if (!elementId || !canvasWorkspace.value) return ''
  return computeSeatLabels(canvasWorkspace.value.layout.elements || []).labelsByElementId.get(elementId) || ''
})
```

Render inside `.canvas-body` after the canvas:

```vue
<SelectedParticipantInfo
  v-if="workbenchMode === 'seating' && store.selectedParticipant"
  v-model:collapsed="participantInfoCollapsed"
  :participant="store.selectedParticipant"
  :field-definitions="workspace.fieldDefinitions"
  :seat-label="selectedParticipantSeatLabel"
  :readonly="readonlyMode"
  @edit="openParticipantEdit"
/>
```

- [ ] **Step 3: Keep selection behavior unchanged**

Verify existing `selectParticipant`, `clearCanvasSelection`, `onParticipantAdded`, and `onParticipantUpdated` continue to set or clear `store.selectedParticipantId`.

---

### Task 3: Remove Seat Tooltip and Improve Drag Preview

**Files:**
- Modify: `frontend/src/components/VenueCanvas.vue`
- Modify: `frontend/src/utils/participantActions.js`
- Modify: `frontend/src/components/ParticipantPanel.vue`

**Interfaces:**
- Produces:

```js
export function createParticipantDragPreview(participant)
export function disposeParticipantDragPreview(preview)
```

- [ ] **Step 1: Remove tooltip state and template from `VenueCanvas.vue`**

Delete `seatTooltipDisabled`, `participantTooltipRows`, `tooltipSuppressed`, and the `el-tooltip` wrapper. Keep the seat `div.layout-element.seat-element` directly under the element loop.

- [ ] **Step 2: Add shared drag image helpers**

In `participantActions.js`, create a fixed offscreen preview element:

```js
export function createParticipantDragPreview(participant) {
  const preview = document.createElement('div')
  preview.className = 'participant-drag-preview'
  preview.textContent = participant?.employeeNo
    ? `${participant.name} · ${participant.employeeNo}`
    : participant?.name || '参会人员'
  document.body.appendChild(preview)
  return preview
}
```

Use `event.dataTransfer.setDragImage(preview, 18, 18)` when available, and remove the preview on the next animation frame.

- [ ] **Step 3: Ensure both drag sources share the helper**

`ParticipantPanel.vue` and `VenueCanvas.vue` already call `startParticipantDrag`; keep that path so the preview style applies to both list dragging and seat dragging.

- [ ] **Step 4: Add global CSS for the drag preview**

Add to `frontend/src/styles/main.css`:

```css
.participant-drag-preview {
  position: fixed;
  top: -1000px;
  left: -1000px;
  z-index: 9999;
  max-width: 180px;
  padding: 7px 12px;
  overflow: hidden;
  color: #172033;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(148, 163, 184, 0.38);
  border-radius: 8px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.16);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}
```

---

### Task 4: Pin Color Legend to Bottom Left

**Files:**
- Modify: `frontend/src/components/GroupColorLegend.vue`

**Interfaces:**
- Keeps existing props and events unchanged.

- [ ] **Step 1: Remove dragging code**

Delete `legendTop`, `dragState`, `legendStyle`, `startLegendDrag`, `dragLegend`, `stopLegendDrag`, `clampLegendTop`, and the `onBeforeUnmount` import.

- [ ] **Step 2: Make header non-draggable**

Remove `@pointerdown.left="startLegendDrag"` and cursor styles from `.legend-header`.

- [ ] **Step 3: Pin by CSS**

Set `.group-color-legend` to:

```css
position: absolute;
left: 14px;
bottom: 14px;
```

Keep the existing small expand/collapse button behavior.

---

### Task 5: Verification, Changelog, Commit, Push

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Run focused frontend tests**

Run:

```powershell
cd frontend
npm.cmd test -- tests/participant-component-contract.test.js tests/group-colors.test.js tests/selected-participant-info-contract.test.js tests/seat-display-contract.test.js tests/workbench-modes-contract.test.js
```

- [ ] **Step 2: Run full frontend tests**

Run:

```powershell
cd frontend
npm.cmd test
```

- [ ] **Step 3: Run frontend build**

Run:

```powershell
cd frontend
npm.cmd run build
```

- [ ] **Step 4: Update changelog**

Add a chronological Chinese entry under `变更`:

```markdown
- `2026-07-29 HH:mm`：排座模式改为选中人员后通过画布左上角信息窗查看详情，并优化人员拖拽预览。
```

- [ ] **Step 5: Verify repository status**

Run:

```powershell
git diff --check
git status --short --branch
```

- [ ] **Step 6: Commit and push**

Run:

```powershell
git add docs/superpowers/specs/2026-07-29-seating-selected-participant-panel-design.md docs/superpowers/plans/2026-07-29-seating-selected-participant-panel.md frontend/src/components/SelectedParticipantInfo.vue frontend/src/views/WorkbenchView.vue frontend/src/components/VenueCanvas.vue frontend/src/components/GroupColorLegend.vue frontend/src/utils/participantActions.js frontend/src/styles/main.css frontend/tests/participant-component-contract.test.js frontend/tests/group-colors.test.js frontend/tests/selected-participant-info-contract.test.js frontend/tests/seat-display-contract.test.js CHANGELOG.md
git commit -m "优化排座人员信息查看体验"
git push origin main
```

---

## Self-Review

Spec coverage:

- Seat hover details removed: Task 1 and Task 3.
- Selected person information panel: Task 1 and Task 2.
- Color legend fixed bottom-left: Task 1 and Task 4.
- Drag preview and target text simplification: Task 1 and Task 3.
- Chinese changelog, commit, push: Task 5.

Placeholder scan:

- No `TBD`, `TODO`, `implement later`, or unspecified test steps remain.

Type consistency:

- `SelectedParticipantInfo.vue` props match the mount in `WorkbenchView.vue`.
- Drag preview helpers are exported from and consumed through `participantActions.js`.
- `GroupColorLegend.vue` keeps existing prop and event names, so `WorkbenchView.vue` does not need API changes for color overrides.
