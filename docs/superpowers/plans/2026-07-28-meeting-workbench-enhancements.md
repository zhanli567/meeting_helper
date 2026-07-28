# Meeting Workbench Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved meeting workbench enhancements: editable draft layouts, region markers, participant field editing, dynamic seat labels, color grouping, and Excel-only exports.

**Architecture:** Keep venue templates and meeting layouts separate. Meeting draft layout editing writes only `t_meeting_elements`, region markers reuse `PlanItemType.RESERVED`, and participant field updates reuse the existing meeting dynamic field registry. Shared frontend and backend seat-numbering helpers keep canvas display and Excel export consistent.

**Tech Stack:** Vue 3, Element Plus, Pinia-style store module, Node built-in test runner, Spring Boot 3.3.5, Java 21, MyBatis-Plus, PostgreSQL integration tests, Apache POI for `.xlsx`.

## Global Constraints

- Work on `main`; do not create a new branch.
- Every commit message must be Chinese.
- Push `main` after every completed implementation task.
- Published versions are read-only; draft is the only editable version.
- Meeting layout edits update the meeting snapshot only and never update the source venue template.
- Region markers occupy seats and block assigning concrete participants until removed.
- Seat labels are computed dynamically as `m排n`.
- Exports are Excel only; remove PDF UI, endpoints, implementation, tests, and dependency if it is only used by export.
- Backend controllers continue using only `GET` and `POST`.
- Frontend API paths continue without a manual `/api` prefix.

---

## File Structure

Create these focused helpers:

- `frontend/src/utils/seatNumbering.js`: compute `m排n`, row labels, and element-to-label maps from layout elements.
- `frontend/src/utils/seatRegions.js`: work with `RESERVED` items, selected seats, connected region groups, and label anchor points.
- `frontend/src/utils/participantColorGroups.js`: assign stable light colors to non-empty dynamic-field values.
- `backend/src/main/java/com/company/meetinghelper/seating/service/SeatLabelService.java`: backend seat-numbering equivalent used by export and tests.
- `backend/src/main/java/com/company/meetinghelper/meeting/api/dto/request/MeetingElementInput.java`: meeting layout element input with optional existing ID.
- `backend/src/main/java/com/company/meetinghelper/meeting/api/dto/request/UpdateMeetingLayoutRequest.java`: full draft layout replacement request.
- `backend/src/main/java/com/company/meetinghelper/seating/api/dto/request/ReservedAreaInput.java`: region marker request item.
- `backend/src/main/java/com/company/meetinghelper/seating/api/dto/request/SaveReservedAreasRequest.java`: complete reserved-area collection for a plan.
- `backend/src/main/java/com/company/meetinghelper/participant/api/dto/request/ParticipantRecordInput.java`: participant dynamic record input.
- `backend/src/main/java/com/company/meetinghelper/participant/api/dto/request/UpdateParticipantRequest.java`: participant update request.
- `frontend/src/components/EditParticipantDialog.vue`: edit participant name, dynamic fields, multi-record rows, and newly added fields.
- `frontend/src/components/RegionMarkerPanel.vue`: create and edit region markers in region marker mode.

Modify these existing files:

- `frontend/src/views/WorkbenchView.vue`: modes, toolbar controls, marker state, color grouping, export actions.
- `frontend/src/components/VenueCanvas.vue`: dynamic seat labels, row labels, reserved area overlays, color grouping, marker selection events.
- `frontend/src/components/ParticipantPanel.vue`: three icon actions and edit event.
- `frontend/src/components/AddParticipantDialog.vue`: new-field rows and validation.
- `frontend/src/utils/participantFields.js`: payload building for new fields and update records.
- `frontend/src/utils/participantActions.js`: submit/update helpers for participants.
- `frontend/src/api/meeting.js`: participant update, layout update, reserved areas, Excel export endpoints.
- `frontend/tests/*.test.js`: add focused contract and helper tests.
- `backend/src/main/java/com/company/meetinghelper/participant/service/ParticipantService.java`: update participant transaction.
- `backend/src/main/java/com/company/meetinghelper/participant/api/ParticipantController.java`: update endpoint.
- `backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingService.java`: draft layout replacement.
- `backend/src/main/java/com/company/meetinghelper/meeting/api/MeetingController.java`: layout update endpoint.
- `backend/src/main/java/com/company/meetinghelper/seating/service/SeatingService.java`: save reserved areas and clean invalid targets after layout replacement.
- `backend/src/main/java/com/company/meetinghelper/seating/api/SeatingController.java`: reserved-area endpoint.
- `backend/src/main/java/com/company/meetinghelper/export/service/ExportService.java`: Excel-only participant and layout exports.
- `backend/src/main/java/com/company/meetinghelper/export/api/ExportController.java`: Excel-only endpoints.
- `backend/pom.xml`: remove PDFBox property and dependency after PDF code is gone.
- `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`: backend integration coverage.
- `CHANGELOG.md`: add implementation entries in chronological order.

---

### Task 1: Shared Seat Numbering

**Files:**
- Create: `frontend/src/utils/seatNumbering.js`
- Create: `frontend/tests/seat-numbering.test.js`
- Create: `backend/src/main/java/com/company/meetinghelper/seating/service/SeatLabelService.java`
- Create: `backend/src/test/java/com/company/meetinghelper/SeatLabelServiceTests.java`

**Interfaces:**
- Produces frontend function:

```js
export function computeSeatLabels(elements) {
  return {
    labelsByElementId: new Map(),
    rows: [],
  }
}
```

- Produces backend service methods:

```java
public Map<String, String> labelsByElementId(List<WorkspaceResponse.ElementView> elements)
public List<RowLabel> rowLabels(List<WorkspaceResponse.ElementView> elements)
public record RowLabel(int sourceRow, int displayRow)
```

- Consumes existing element shape: `{ id, kind, row, column }` on frontend and `WorkspaceResponse.ElementView` on backend.

- [ ] **Step 1: Write failing frontend tests**

Add `frontend/tests/seat-numbering.test.js`:

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import { computeSeatLabels } from '../src/utils/seatNumbering.js'

test('座位编号只统计有座位的行且同排从左到右', () => {
  const result = computeSeatLabels([
    { id: 'stage', kind: 'GENERIC', row: 1, column: 1 },
    { id: 'seat-b', kind: 'SEAT', row: 3, column: 4 },
    { id: 'seat-a', kind: 'SEAT', row: 3, column: 2 },
    { id: 'seat-c', kind: 'SEAT', row: 6, column: 1 },
  ])

  assert.equal(result.labelsByElementId.get('seat-a'), '1排1')
  assert.equal(result.labelsByElementId.get('seat-b'), '1排2')
  assert.equal(result.labelsByElementId.get('seat-c'), '2排1')
  assert.deepEqual(result.rows, [
    { sourceRow: 3, displayRow: 1 },
    { sourceRow: 6, displayRow: 2 },
  ])
})
```

- [ ] **Step 2: Run frontend test to verify it fails**

Run:

```powershell
cd frontend
npm.cmd test -- tests/seat-numbering.test.js
```

Expected: FAIL because `../src/utils/seatNumbering.js` does not exist.

- [ ] **Step 3: Implement frontend helper**

Create `frontend/src/utils/seatNumbering.js`:

```js
const isSeat = (element) => element?.kind === 'SEAT'

export function computeSeatLabels(elements) {
  const seats = (elements || [])
    .filter(isSeat)
    .map((element) => ({
      id: element.id,
      row: Number(element.row),
      column: Number(element.column),
    }))
    .filter((element) => element.id && Number.isFinite(element.row) && Number.isFinite(element.column))

  const sortedRows = [...new Set(seats.map((seat) => seat.row))].sort((left, right) => left - right)
  const displayRowBySource = new Map(sortedRows.map((row, index) => [row, index + 1]))
  const labelsByElementId = new Map()

  sortedRows.forEach((sourceRow) => {
    seats
      .filter((seat) => seat.row === sourceRow)
      .sort((left, right) => left.column - right.column || String(left.id).localeCompare(String(right.id)))
      .forEach((seat, index) => {
        labelsByElementId.set(seat.id, `${displayRowBySource.get(sourceRow)}排${index + 1}`)
      })
  })

  return {
    labelsByElementId,
    rows: sortedRows.map((sourceRow) => ({
      sourceRow,
      displayRow: displayRowBySource.get(sourceRow),
    })),
  }
}
```

- [ ] **Step 4: Run frontend test to verify it passes**

Run:

```powershell
cd frontend
npm.cmd test -- tests/seat-numbering.test.js
```

Expected: PASS.

- [ ] **Step 5: Write failing backend tests**

Add `backend/src/test/java/com/company/meetinghelper/SeatLabelServiceTests.java`:

```java
package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.seating.service.SeatLabelService;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatLabelServiceTests {
    private final SeatLabelService service = new SeatLabelService();

    @Test
    void labelsSeatsByOccupiedRowsThenColumns() {
        List<WorkspaceResponse.ElementView> elements = List.of(
                element("stage", "GENERIC", 1, 1),
                element("seat-b", "SEAT", 3, 4),
                element("seat-a", "SEAT", 3, 2),
                element("seat-c", "SEAT", 6, 1)
        );

        assertThat(service.labelsByElementId(elements))
                .containsEntry("seat-a", "1排1")
                .containsEntry("seat-b", "1排2")
                .containsEntry("seat-c", "2排1")
                .doesNotContainKey("stage");
        assertThat(service.rowLabels(elements))
                .extracting(SeatLabelService.RowLabel::sourceRow)
                .containsExactly(3, 6);
    }

    private WorkspaceResponse.ElementView element(String id, String kind, int row, int column) {
        return new WorkspaceResponse.ElementView(id, kind, "x", row, column, 1, 1, "#fff", "#aaa");
    }
}
```

- [ ] **Step 6: Run backend test to verify it fails**

Run:

```powershell
cd backend
mvn.cmd test -Dtest=SeatLabelServiceTests
```

Expected: FAIL because `SeatLabelService` does not exist.

- [ ] **Step 7: Implement backend helper**

Create `backend/src/main/java/com/company/meetinghelper/seating/service/SeatLabelService.java`:

```java
package com.company.meetinghelper.seating.service;

import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SeatLabelService {
    public Map<String, String> labelsByElementId(List<WorkspaceResponse.ElementView> elements) {
        LinkedHashMap<String, String> labels = new LinkedHashMap<String, String>();
        List<Integer> sourceRows = seatRows(elements);
        for (int rowIndex = 0; rowIndex < sourceRows.size(); rowIndex++) {
            int sourceRow = sourceRows.get(rowIndex);
            List<WorkspaceResponse.ElementView> rowSeats = seats(elements).stream()
                    .filter(element -> element.row() == sourceRow)
                    .sorted(Comparator.comparingInt(WorkspaceResponse.ElementView::column)
                            .thenComparing(WorkspaceResponse.ElementView::id))
                    .toList();
            for (int seatIndex = 0; seatIndex < rowSeats.size(); seatIndex++) {
                labels.put(rowSeats.get(seatIndex).id(), (rowIndex + 1) + "排" + (seatIndex + 1));
            }
        }
        return labels;
    }

    public List<RowLabel> rowLabels(List<WorkspaceResponse.ElementView> elements) {
        List<Integer> rows = seatRows(elements);
        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(index -> new RowLabel(rows.get(index), index + 1))
                .toList();
    }

    private List<Integer> seatRows(List<WorkspaceResponse.ElementView> elements) {
        return seats(elements).stream()
                .map(WorkspaceResponse.ElementView::row)
                .distinct()
                .sorted()
                .toList();
    }

    private List<WorkspaceResponse.ElementView> seats(List<WorkspaceResponse.ElementView> elements) {
        return (elements == null ? List.<WorkspaceResponse.ElementView>of() : elements).stream()
                .filter(element -> "SEAT".equals(element.kind()))
                .collect(Collectors.toList());
    }

    public record RowLabel(int sourceRow, int displayRow) {
    }
}
```

- [ ] **Step 8: Run backend test to verify it passes**

Run:

```powershell
cd backend
mvn.cmd test -Dtest=SeatLabelServiceTests
```

Expected: PASS.

- [ ] **Step 9: Commit and push**

Run:

```powershell
git add frontend/src/utils/seatNumbering.js frontend/tests/seat-numbering.test.js backend/src/main/java/com/company/meetinghelper/seating/service/SeatLabelService.java backend/src/test/java/com/company/meetinghelper/SeatLabelServiceTests.java
git commit -m "新增座位动态编号计算"
git push origin main
```

---

### Task 2: Participant Add and Edit Field Model

**Files:**
- Modify: `frontend/src/utils/participantFields.js`
- Modify: `frontend/src/utils/participantActions.js`
- Modify: `frontend/src/components/AddParticipantDialog.vue`
- Create: `frontend/src/components/EditParticipantDialog.vue`
- Modify: `frontend/src/components/ParticipantPanel.vue`
- Modify: `frontend/src/api/meeting.js`
- Modify: `frontend/tests/participant-fields.test.js`
- Modify: `frontend/tests/participant-actions.test.js`
- Create: `frontend/tests/participant-edit-contract.test.js`

**Interfaces:**
- Produces frontend helpers:

```js
export function normalizeExtraFields(extraFields, existingFields)
export function createParticipantPayload(form, targetElementId)
export function createParticipantUpdatePayload(form)
```

- Produces component event:

```js
emit('edit', participant)
```

- Consumes backend endpoint to be added in Task 3:

```js
meetingApi.updateParticipant(meetingId, participantId, payload)
```

- [ ] **Step 1: Write failing helper tests**

Extend `frontend/tests/participant-fields.test.js`:

```js
test('新增人员列名和值必须非空且不能重名', () => {
  assert.throws(
    () => normalizeExtraFields([{ name: '部门', value: '秘书处' }], [{ code: '部门' }]),
    /字段已存在/,
  )
  assert.throws(
    () => normalizeExtraFields([{ name: ' ', value: '秘书处' }], []),
    /请输入列名/,
  )
  assert.throws(
    () => normalizeExtraFields([{ name: '部门', value: ' ' }], []),
    /请填写该人员在新增列中的值/,
  )
  assert.deepEqual(
    normalizeExtraFields([{ name: '部门', value: ' 秘书处 ' }], []),
    { 部门: '秘书处' },
  )
})

test('人员更新载荷保留姓名和动态记录并携带新增列', () => {
  assert.deepEqual(
    createParticipantUpdatePayload({
      name: '王创新',
      records: [{ id: 'record-1', attributes: { 部门: '研发' } }],
      extraFields: [{ name: '获奖批次', value: '第一批' }],
      fieldDefinitions: [{ code: '部门' }],
    }),
    {
      name: '王创新',
      records: [{ id: 'record-1', attributes: { 部门: '研发', 获奖批次: '第一批' } }],
    },
  )
})
```

Expected imports to add:

```js
import {
  createParticipantPayload,
  createParticipantUpdatePayload,
  normalizeExtraFields,
} from '../src/utils/participantFields.js'
```

- [ ] **Step 2: Run helper tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- tests/participant-fields.test.js
```

Expected: FAIL because `normalizeExtraFields` and `createParticipantUpdatePayload` do not exist.

- [ ] **Step 3: Implement helper functions**

Modify `frontend/src/utils/participantFields.js`:

```js
function normalizeFieldName(value) {
  return nonEmptyValue(value).toLocaleLowerCase()
}

export function normalizeExtraFields(extraFields = [], existingFields = []) {
  const existing = new Set((existingFields || []).map((field) => normalizeFieldName(field.code || field.label)))
  const seen = new Set()
  const attributes = {}
  for (const row of extraFields || []) {
    const name = nonEmptyValue(row?.name)
    const value = nonEmptyValue(row?.value)
    if (!name) throw new Error('请输入列名')
    const key = normalizeFieldName(name)
    if (existing.has(key) || seen.has(key)) throw new Error('该列已存在，请使用其他列名')
    if (!value) throw new Error('请填写该人员在新增列中的值')
    seen.add(key)
    attributes[name] = value
  }
  return attributes
}

export function createParticipantUpdatePayload(form) {
  const extraAttributes = normalizeExtraFields(form.extraFields, form.fieldDefinitions)
  const records = (form.records?.length ? form.records : [{ attributes: {} }]).map((record) => ({
    id: record.id,
    attributes: {
      ...(record.attributes || {}),
      ...extraAttributes,
    },
  }))
  return {
    name: nonEmptyValue(form.name),
    records,
  }
}
```

Modify `createParticipantPayload` to merge `normalizeExtraFields(form.extraFields, form.fieldDefinitions)` into `form.attributes`.

- [ ] **Step 4: Run helper tests to verify pass**

Run:

```powershell
cd frontend
npm.cmd test -- tests/participant-fields.test.js
```

Expected: PASS.

- [ ] **Step 5: Write failing action/component contract tests**

Extend `frontend/tests/participant-actions.test.js`:

```js
test('更新人员动作调用注入 API 并返回更新结果', async () => {
  const calls = []
  const updated = { id: 'person-7', name: '王创新' }
  const updateParticipant = async (...args) => {
    calls.push(args)
    return updated
  }

  const result = await updateParticipantDetails({
    updateParticipant,
    meetingId: 'meeting-1',
    participantId: 'person-7',
    form: {
      name: '王创新',
      records: [{ id: 'record-1', attributes: { 部门: '研发' } }],
      extraFields: [],
      fieldDefinitions: [],
    },
  })

  assert.equal(result, updated)
  assert.deepEqual(calls, [
    ['meeting-1', 'person-7', { name: '王创新', records: [{ id: 'record-1', attributes: { 部门: '研发' } }] }],
  ])
})
```

Add `updateParticipantDetails` import from `../src/utils/participantActions.js`.

Create `frontend/tests/participant-edit-contract.test.js`:

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('人员面板使用三个统一图标按钮并暴露编辑事件', async () => {
  const source = await readFile(new URL('../src/components/ParticipantPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /defineEmits\(\[[^\]]*'edit'/s)
  assert.match(source, /@click="emit\('edit', person\)"/)
  assert.match(source, /class="person-actions icon-actions"/)
  assert.doesNotMatch(source, /移出会议<\/el-button>/)
})

test('编辑人员弹窗工号只读并支持多记录表格', async () => {
  const source = await readFile(new URL('../src/components/EditParticipantDialog.vue', import.meta.url), 'utf8')

  assert.match(source, /label="工号"/)
  assert.match(source, /disabled/)
  assert.match(source, /el-table/)
  assert.match(source, /添加列/)
})
```

- [ ] **Step 6: Run action/component tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- tests/participant-actions.test.js tests/participant-edit-contract.test.js
```

Expected: FAIL because `updateParticipantDetails` and `EditParticipantDialog.vue` do not exist, and `ParticipantPanel.vue` has no edit event.

- [ ] **Step 7: Implement frontend participant UI model**

Modify `frontend/src/utils/participantActions.js`:

```js
import { createParticipantPayload, createParticipantUpdatePayload, participantDragData } from './participantFields.js'

export async function updateParticipantDetails({ updateParticipant, meetingId, participantId, form }) {
  return updateParticipant(meetingId, participantId, createParticipantUpdatePayload(form))
}
```

Modify `frontend/src/api/meeting.js`:

```js
async updateParticipant(meetingId, participantId, data) {
  return unwrap(http.post(`/meetings/${meetingId}/participants/${participantId}/update`, data))
}
```

Modify `AddParticipantDialog.vue`:

- Add `extraFields` to the reactive form.
- Render an “添加列” section with field name and value.
- Pass `fieldDefinitions` into the form object before submit.
- Show `ElMessage.error(error.message)` when helper validation throws.

Create `EditParticipantDialog.vue`:

- Props: `meetingId`, `participant`, `fieldDefinitions`, `submitting`.
- Dialog title: `编辑人员`.
- Disabled employee number input.
- Required name input.
- For one record, render field inputs in a form.
- For two or more records, render an `el-table` with one row per record and editable fields.
- Include same new-field row pattern as add dialog.
- Emit `done` with backend result after success.

Modify `ParticipantPanel.vue`:

- Import `Edit`.
- Add `edit` to `defineEmits`.
- Change actions container class to `person-actions icon-actions`.
- Render three icon buttons with tooltip or title: edit, attendance toggle, delete.

- [ ] **Step 8: Run frontend tests**

Run:

```powershell
cd frontend
npm.cmd test -- tests/participant-fields.test.js tests/participant-actions.test.js tests/participant-edit-contract.test.js
```

Expected: PASS.

- [ ] **Step 9: Commit and push**

Run:

```powershell
git add frontend/src/utils/participantFields.js frontend/src/utils/participantActions.js frontend/src/components/AddParticipantDialog.vue frontend/src/components/EditParticipantDialog.vue frontend/src/components/ParticipantPanel.vue frontend/src/api/meeting.js frontend/tests/participant-fields.test.js frontend/tests/participant-actions.test.js frontend/tests/participant-edit-contract.test.js
git commit -m "完善人员新增和编辑前端模型"
git push origin main
```

---

### Task 3: Backend Participant Update API

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/participant/api/dto/request/ParticipantRecordInput.java`
- Create: `backend/src/main/java/com/company/meetinghelper/participant/api/dto/request/UpdateParticipantRequest.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/api/ParticipantController.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/service/ParticipantService.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`

**Interfaces:**
- Produces endpoint:

```http
POST /meetings/{meetingId}/participants/{participantId}/update
```

- Request:

```json
{
  "name": "王创新",
  "records": [
    { "id": "record-1", "attributes": { "部门": "研发", "获奖批次": "第一批" } }
  ]
}
```

- Response: `ParticipantResult`.

- Consumes `ParticipantFieldRegistrationService.registerFields(meetingId, fieldNames)` and existing repositories.

- [ ] **Step 1: Write failing integration tests**

Add tests to `MeetingHelperIntegrationTests`:

```java
@Test
void participantCanBeUpdatedWithoutChangingEmployeeNoAndRegistersNewFields() throws Exception {
    String meetingId = createImportMeeting();
    ParticipantResult participant = participantService.create(
            meetingId,
            new CreateParticipantRequest("a90000001", "旧姓名", Map.of("部门", "研发"), null)
    );

    MvcResult result = mockMvc.perform(post(
                    "/meetings/{meetingId}/participants/{participantId}/update",
                    meetingId,
                    participant.id()
            ).header(USER_HEADER, DEFAULT_USER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "name": "新姓名",
                              "records": [
                                {"attributes": {"部门": "市场", "获奖批次": "第一批"}}
                              ]
                            }
                            """))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(responseJson(result).path("name").asText()).isEqualTo("新姓名");
    WorkspaceResponse workspace = workspaceService.getWorkspace(meetingId);
    ParticipantView updated = workspace.participants().stream()
            .filter(value -> value.id().equals(participant.id()))
            .findFirst()
            .orElseThrow();
    assertThat(updated.employeeNo()).isEqualTo("a90000001");
    assertThat(updated.primaryAttributes())
            .containsEntry("部门", "市场")
            .containsEntry("获奖批次", "第一批");
}

@Test
void participantUpdateRejectsBlankNameAndBlankNewFieldValues() throws Exception {
    String meetingId = createImportMeeting();
    ParticipantResult participant = participantService.create(
            meetingId,
            new CreateParticipantRequest("a90000002", "待更新", Map.of(), null)
    );

    mockMvc.perform(post(
                    "/meetings/{meetingId}/participants/{participantId}/update",
                    meetingId,
                    participant.id()
            ).header(USER_HEADER, DEFAULT_USER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":" ","records":[{"attributes":{"新字段":"值"}}]}
                            """))
            .andExpect(status().isBadRequest());

    mockMvc.perform(post(
                    "/meetings/{meetingId}/participants/{participantId}/update",
                    meetingId,
                    participant.id()
            ).header(USER_HEADER, DEFAULT_USER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"待更新","records":[{"attributes":{"新字段":" "}}]}
                            """))
            .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run backend tests to verify failure**

Run:

```powershell
cd backend
mvn.cmd test -Dtest=MeetingHelperIntegrationTests#participantCanBeUpdatedWithoutChangingEmployeeNoAndRegistersNewFields,MeetingHelperIntegrationTests#participantUpdateRejectsBlankNameAndBlankNewFieldValues
```

Expected: FAIL because update DTO and endpoint do not exist.

- [ ] **Step 3: Add DTOs**

Create `ParticipantRecordInput.java`:

```java
package com.company.meetinghelper.participant.api.dto.request;

import java.util.Map;

public record ParticipantRecordInput(String id, Map<String, String> attributes) {
}
```

Create `UpdateParticipantRequest.java`:

```java
package com.company.meetinghelper.participant.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UpdateParticipantRequest(
        @NotBlank String name,
        List<ParticipantRecordInput> records
) {
}
```

- [ ] **Step 4: Add controller endpoint**

Modify `ParticipantController.java`:

```java
@PostMapping("/{participantId}/update")
public ParticipantResult update(
        @PathVariable String meetingId,
        @PathVariable String participantId,
        @Valid @RequestBody UpdateParticipantRequest request
) {
    return participantService.update(meetingId, participantId, request);
}
```

- [ ] **Step 5: Implement service update**

Modify `ParticipantService.java`:

- Add `update(String meetingId, String participantId, UpdateParticipantRequest request)`.
- Require owned meeting.
- Find participant by ID and meeting ID.
- Trim and save name.
- Collect non-blank field names from request records.
- Reject blank attribute values for non-blank field names from request records.
- Register fields with `fieldRegistrationService.registerFields`.
- Delete existing records for participant.
- Recreate records in request order, skipping empty records.
- Return `ParticipantResult`.

Core implementation shape:

```java
@Transactional
public ParticipantResult update(String meetingId, String participantId, UpdateParticipantRequest request) {
    meetingAccessService.requireOwnedMeeting(meetingId);
    ParticipantEntity participant = participantRepository.findById(participantId)
            .filter(value -> value.getMeetingId().equals(meetingId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
    String name = request.name().trim();
    if (name.isBlank()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "姓名不能为空");
    }
    List<ParticipantRecordInput> records = request.records() == null
            ? List.of()
            : request.records();
    List<String> fieldNames = records.stream()
            .flatMap(record -> (record.attributes() == null ? Map.<String,String>of() : record.attributes()).keySet().stream())
            .map(value -> value == null ? "" : value.trim())
            .filter(value -> !value.isBlank())
            .toList();
    Map<String,String> canonicalNames = fieldRegistrationService.registerFields(meetingId, fieldNames);
    participant.setName(name);
    participantRepository.save(participant);
    recordRepository.deleteAll(recordRepository.findAllByParticipantIdOrderByRecordOrderAsc(participantId));
    int order = 0;
    for (ParticipantRecordInput source : records) {
        Map<String,String> attributes = canonicalAttributes(source.attributes(), canonicalNames);
        if (attributes.isEmpty()) {
            continue;
        }
        ParticipantRecordEntity record = new ParticipantRecordEntity();
        record.setParticipantId(participantId);
        record.setRecordOrder(++order);
        record.setAttributesJson(writeAttributes(attributes));
        recordRepository.save(record);
    }
    return new ParticipantResult(participant.getId(), participant.getEmployeeNo(), participant.getName());
}
```

Adjust `canonicalAttributes` to throw `BAD_REQUEST` when a request contains a non-blank field name with blank value.

- [ ] **Step 6: Run backend tests**

Run:

```powershell
cd backend
mvn.cmd test -Dtest=MeetingHelperIntegrationTests#participantCanBeUpdatedWithoutChangingEmployeeNoAndRegistersNewFields,MeetingHelperIntegrationTests#participantUpdateRejectsBlankNameAndBlankNewFieldValues
```

Expected: PASS.

- [ ] **Step 7: Commit and push**

Run:

```powershell
git add backend/src/main/java/com/company/meetinghelper/participant/api/dto/request/ParticipantRecordInput.java backend/src/main/java/com/company/meetinghelper/participant/api/dto/request/UpdateParticipantRequest.java backend/src/main/java/com/company/meetinghelper/participant/api/ParticipantController.java backend/src/main/java/com/company/meetinghelper/participant/service/ParticipantService.java backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java
git commit -m "新增人员信息编辑接口"
git push origin main
```

---

### Task 4: Reserved Area Backend and Draft Layout Update

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/meeting/api/dto/request/MeetingElementInput.java`
- Create: `backend/src/main/java/com/company/meetinghelper/meeting/api/dto/request/UpdateMeetingLayoutRequest.java`
- Create: `backend/src/main/java/com/company/meetinghelper/seating/api/dto/request/ReservedAreaInput.java`
- Create: `backend/src/main/java/com/company/meetinghelper/seating/api/dto/request/SaveReservedAreasRequest.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/repository/MeetingElementRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/api/MeetingController.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/service/SeatingService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/api/SeatingController.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`

**Interfaces:**
- Produces endpoint:

```http
POST /meetings/{meetingId}/layout/update
```

- Produces endpoint:

```http
POST /plans/{planId}/reserved-areas/save
```

- `UpdateMeetingLayoutRequest`:

```java
public record UpdateMeetingLayoutRequest(int gridRows, int gridColumns, List<MeetingElementInput> elements) {}
```

- `MeetingElementInput`:

```java
public record MeetingElementInput(String id, String kind, String name, int row, int column, int rowSpan, int columnSpan, String fillColor, String borderColor) {}
```

- `SaveReservedAreasRequest`:

```java
public record SaveReservedAreasRequest(List<ReservedAreaInput> reservedAreas) {}
```

- `ReservedAreaInput`:

```java
public record ReservedAreaInput(String id, String label, String backgroundColor, String textColor, boolean bold, List<String> targetElementIds) {}
```

- [ ] **Step 1: Write failing integration tests**

Add tests to `MeetingHelperIntegrationTests`:

```java
@Test
void reservedAreasOccupySeatsAndBlockPersonAssignments() throws Exception {
    VenueSummary venue = defaultVenue();
    MeetingSummary meeting = meetingService.create(new CreateMeetingRequest("区域标记-" + UUID.randomUUID(), venue.id()));
    WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
    String planId = workspace.plan().id();
    String seatId = workspace.layout().elements().stream()
            .filter(value -> value.kind().equals("SEAT"))
            .findFirst()
            .orElseThrow()
            .id();

    mockMvc.perform(post("/plans/{planId}/reserved-areas/save", planId)
                    .header(USER_HEADER, DEFAULT_USER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"reservedAreas":[{"label":"嘉宾","backgroundColor":"#FEF3C7","textColor":"#172033","bold":true,"targetElementIds":["%s"]}]}
                            """.formatted(seatId)))
            .andExpect(status().isOk());

    ParticipantResult participant = participantService.create(
            meeting.id(),
            new CreateParticipantRequest("a91000001", "王嘉宾", Map.of(), null)
    );
    assertThatThrownBy(() -> seatingService.assign(
            planId,
            new AssignmentRequest(participant.id(), seatId)
    )).hasMessageContaining("目标座位已被设备、预留或禁用状态占用");
}

@Test
void draftLayoutUpdateRemovesAssignmentsForDeletedSeatsButNotVenueTemplate() throws Exception {
    VenueSummary venue = defaultVenue();
    MeetingSummary meeting = meetingService.create(new CreateMeetingRequest("布局编辑-" + UUID.randomUUID(), venue.id()));
    WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
    String planId = workspace.plan().id();
    ElementView removedSeat = workspace.layout().elements().stream()
            .filter(value -> value.kind().equals("SEAT"))
            .findFirst()
            .orElseThrow();
    ParticipantResult participant = participantService.create(
            meeting.id(),
            new CreateParticipantRequest("a91000002", "待退回", Map.of(), null)
    );
    seatingService.assign(planId, new AssignmentRequest(participant.id(), removedSeat.id()));

    String elementsJson = workspace.layout().elements().stream()
            .filter(value -> !value.id().equals(removedSeat.id()))
            .map(value -> """
                    {"id":"%s","kind":"%s","name":"%s","row":%d,"column":%d,"rowSpan":%d,"columnSpan":%d,"fillColor":"%s","borderColor":"%s"}
                    """.formatted(value.id(), value.kind(), value.name(), value.row(), value.column(), value.rowSpan(), value.columnSpan(), value.fillColor(), value.borderColor()))
            .collect(Collectors.joining(","));

    mockMvc.perform(post("/meetings/{meetingId}/layout/update", meeting.id())
                    .header(USER_HEADER, DEFAULT_USER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"gridRows":5,"gridColumns":5,"elements":[%s]}
                            """.formatted(elementsJson)))
            .andExpect(status().isOk());

    WorkspaceResponse updated = workspaceService.getWorkspace(meeting.id());
    assertThat(updated.participants().getFirst().assignedElementId()).isNull();
    assertThat(venueService.getLayout(venue.id()).seatCount()).isEqualTo(25);
}
```

- [ ] **Step 2: Run backend tests to verify failure**

Run:

```powershell
cd backend
mvn.cmd test -Dtest=MeetingHelperIntegrationTests#reservedAreasOccupySeatsAndBlockPersonAssignments,MeetingHelperIntegrationTests#draftLayoutUpdateRemovesAssignmentsForDeletedSeatsButNotVenueTemplate
```

Expected: FAIL because endpoints and DTOs do not exist.

- [ ] **Step 3: Add request DTOs**

Create the four DTO files named in this task’s file list. Add validation annotations:

- `@Min(5)` for grid rows and columns.
- `@NotBlank` for element kind, element name, and reserved-area label.
- `@NotEmpty` for reserved-area target IDs.
- `@Pattern(regexp = "^#[0-9a-fA-F]{6}$")` for colors.

- [ ] **Step 4: Add repository helpers**

Modify `MeetingElementRepository.java`:

```java
public void deleteAllByMeetingId(String meetingId) {
    findAllByMeetingIdOrderByStartRowAscStartColumnAsc(meetingId)
            .forEach(element -> elementMapper.physicalDeleteById(element.getId()));
}
```

Add `deleteAll(Collection<MeetingElementEntity> elements)` override that calls `elementMapper.physicalDeleteById(element.getId())` for each element.

- [ ] **Step 5: Implement layout update service and endpoint**

Modify `MeetingService.java`:

```java
@Transactional
public WorkspaceResponse updateLayout(String meetingId, UpdateMeetingLayoutRequest request) {
    MeetingEntity meeting = meetingAccessService.requireOwnedMeeting(meetingId);
    List<MeetingElementEntity> before = meetingElementRepository.findAllByMeetingIdOrderByStartRowAscStartColumnAsc(meetingId);
    Set<String> retainedIds = request.elements().stream()
            .map(MeetingElementInput::id)
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toSet());
    Set<String> removedIds = before.stream()
            .map(MeetingElementEntity::getId)
            .filter(id -> !retainedIds.contains(id))
            .collect(Collectors.toSet());
    seatingService.releaseTargetsForRemovedElements(meetingId, removedIds);
    meetingElementRepository.deleteAllByMeetingId(meetingId);
    meetingElementRepository.saveAll(request.elements().stream()
            .map(input -> toMeetingElement(meetingId, input))
            .toList());
    meeting.setGridRows(request.gridRows());
    meeting.setGridColumns(request.gridColumns());
    meeting.setLayoutVersion(meeting.getLayoutVersion() + 1);
    meetingRepository.save(meeting);
    return workspaceService.getWorkspace(meetingId);
}
```

Use the same geometry rules as `VenueLayoutValidator`. Add a private `validateMeetingLayout(UpdateMeetingLayoutRequest request)` method in `MeetingService` that checks minimum canvas size, element bounds, legal kind values, legal color values, non-blank names, and pairwise overlap before saving.

Modify `MeetingController.java`:

```java
@PostMapping("/{meetingId}/layout/update")
public WorkspaceResponse updateLayout(
        @PathVariable String meetingId,
        @Valid @RequestBody UpdateMeetingLayoutRequest request
) {
    return meetingService.updateLayout(meetingId, request);
}
```

- [ ] **Step 6: Implement reserved area service and endpoint**

Modify `SeatingService.java`:

```java
@Transactional
public void replaceReservedAreas(String planId, SaveReservedAreasRequest request) {
    SeatingPlanEntity plan = requirePlan(planId);
    Map<String, MeetingElementEntity> elements = elementRepository
            .findAllByMeetingIdOrderByStartRowAscStartColumnAsc(plan.getMeetingId())
            .stream()
            .collect(Collectors.toMap(MeetingElementEntity::getId, Function.identity()));
    Set<String> targetIds = new HashSet<String>();
    for (ReservedAreaInput area : request.reservedAreas()) {
        for (String elementId : area.targetElementIds()) {
            MeetingElementEntity element = elements.get(elementId);
            if (element == null || element.getElementKind() != ElementKind.SEAT) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "区域标记只能选择座位");
            }
            if (!targetIds.add(elementId)) {
                throw new ApiException(HttpStatus.CONFLICT, "同一座位不能加入多个区域标记");
            }
        }
    }
    List<PlanItemEntity> current = itemRepository.findAllByPlanIdOrderByCreatedAtAsc(planId);
    List<PlanItemEntity> reserved = current.stream()
            .filter(item -> item.getItemType() == PlanItemType.RESERVED)
            .toList();
    reserved.forEach(item -> targetRepository.deleteAllByPlanItemId(item.getId()));
    itemRepository.deleteAll(reserved);
    for (ReservedAreaInput area : request.reservedAreas()) {
        PlanItemEntity item = new PlanItemEntity();
        item.setPlanId(planId);
        item.setItemType(PlanItemType.RESERVED);
        item.setLabel(area.label().trim());
        item.setBackgroundColor(area.backgroundColor());
        item.setTextColor(area.textColor());
        item.setBold(area.bold());
        itemRepository.save(item);
        for (String targetId : area.targetElementIds()) {
            targetRepository.save(createTarget(item.getId(), targetId));
        }
    }
    touch(plan);
}
```

Add `releaseTargetsForRemovedElements(String meetingId, Set<String> removedElementIds)`:

- Find plan by meeting.
- Find all items and targets.
- Delete targets whose `meetingElementId` is removed.
- Delete non-person items with no targets.
- Delete person items with no targets so participants return to pending.

Modify `SeatingController.java`:

```java
@PostMapping("/{planId}/reserved-areas/save")
public ApiResponse<Void> replaceReservedAreas(
        @PathVariable String planId,
        @Valid @RequestBody SaveReservedAreasRequest request
) {
    seatingService.replaceReservedAreas(planId, request);
    return ApiResponse.success(null);
}
```

- [ ] **Step 7: Run backend tests**

Run:

```powershell
cd backend
mvn.cmd test -Dtest=MeetingHelperIntegrationTests#reservedAreasOccupySeatsAndBlockPersonAssignments,MeetingHelperIntegrationTests#draftLayoutUpdateRemovesAssignmentsForDeletedSeatsButNotVenueTemplate
```

Expected: PASS.

- [ ] **Step 8: Commit and push**

Run:

```powershell
git add backend/src/main/java/com/company/meetinghelper/meeting/api/dto/request/MeetingElementInput.java backend/src/main/java/com/company/meetinghelper/meeting/api/dto/request/UpdateMeetingLayoutRequest.java backend/src/main/java/com/company/meetinghelper/seating/api/dto/request/ReservedAreaInput.java backend/src/main/java/com/company/meetinghelper/seating/api/dto/request/SaveReservedAreasRequest.java backend/src/main/java/com/company/meetinghelper/meeting/repository/MeetingElementRepository.java backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingService.java backend/src/main/java/com/company/meetinghelper/meeting/api/MeetingController.java backend/src/main/java/com/company/meetinghelper/seating/service/SeatingService.java backend/src/main/java/com/company/meetinghelper/seating/api/SeatingController.java backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java
git commit -m "新增会议草稿布局和区域标记接口"
git push origin main
```

---

### Task 5: Workbench Modes and Meeting Layout Editing UI

**Files:**
- Modify: `frontend/src/views/WorkbenchView.vue`
- Modify: `frontend/src/components/VenueLayoutEditor.vue`
- Modify: `frontend/src/components/VenueElementPanel.vue`
- Modify: `frontend/src/api/meeting.js`
- Create: `frontend/tests/workbench-modes-contract.test.js`
- Modify: `frontend/tests/venue-layout-editor.runtime.test.js`

**Interfaces:**
- Produces modes in `WorkbenchView.vue`:

```js
const workbenchMode = ref('seating') // seating | layout | marker
```

- Produces API call:

```js
meetingApi.updateMeetingLayout(meetingId, payload)
```

- Extends `VenueLayoutEditor.vue` props:

```js
protectedElementIds: { type: Array, default: () => [] }
deleteConfirmMessage: { type: Function, default: undefined }
```

- [ ] **Step 1: Write failing contract tests**

Create `frontend/tests/workbench-modes-contract.test.js`:

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('排座工作台提供三段模式且发布版本只读', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /workbenchMode\s*=\s*ref\('seating'\)/)
  assert.match(source, /label="排座模式"/)
  assert.match(source, /label="布局编辑模式"/)
  assert.match(source, /label="区域标记模式"/)
  assert.match(source, /:disabled="readonlyMode"/)
})

test('布局编辑模式复用编辑器并保存会议布局', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /VenueLayoutEditor/)
  assert.match(source, /saveMeetingLayout/)
  assert.match(source, /updateMeetingLayout/)
})

test('布局编辑器双击删除走统一删除保护入口', async () => {
  const source = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')

  assert.match(source, /protectedElementIds/)
  assert.match(source, /requestDeleteElement/)
  assert.match(source, /@dblclick\.stop="requestDeleteElement\(element\)"/)
})
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- tests/workbench-modes-contract.test.js
```

Expected: FAIL because mode UI and editor delete hook do not exist.

- [ ] **Step 3: Extend API client**

Modify `frontend/src/api/meeting.js`:

```js
async updateMeetingLayout(meetingId, data) {
  return unwrap(http.post(`/meetings/${meetingId}/layout/update`, data))
}
```

- [ ] **Step 4: Extend `VenueLayoutEditor.vue` carefully**

Add props:

```js
protectedElementIds: {
  type: Array,
  default: () => [],
},
deleteConfirmMessage: {
  type: Function,
  default: undefined,
},
```

Replace direct `@dblclick.stop="deleteElement(element)"` with:

```vue
@dblclick.stop="requestDeleteElement(element)"
```

Add:

```js
async function requestDeleteElement(element) {
  if (props.protectedElementIds.includes(element.id) && props.deleteConfirmMessage) {
    const allowed = await props.deleteConfirmMessage(element)
    if (!allowed) return
  }
  deleteElement(element)
}
```

Keep existing template behavior unchanged for venue templates by using empty protected IDs by default.

- [ ] **Step 5: Add workbench mode UI**

Modify `WorkbenchView.vue`:

- Add `workbenchMode`.
- Add computed `layoutDraft` from `workspace.layout`.
- Add `protectedElementIds` computed from `workspace.items.flatMap(item.targetElementIds)`.
- Add an `el-segmented` or `el-radio-group` in the toolbar with labels `排座模式`, `布局编辑模式`, `区域标记模式`.
- Disable layout and marker choices when `readonlyMode`.
- In layout mode, render `VenueLayoutEditor` inside `canvas-body` with `showBack=false`, `title="编辑会议布局"`, `saveLabel="保存会议布局"`, and `protectedElementIds`.
- Keep `ParticipantPanel` visible unless the editor needs full width; if keeping visible causes cramped layout, collapse it automatically while in layout mode and restore prior collapse state when returning to seating mode.

Add save:

```js
async function saveMeetingLayout() {
  if (!store.workspace || readonlyMode.value) return
  try {
    const updated = await meetingApi.updateMeetingLayout(store.workspace.meeting.id, {
      gridRows: layoutDraft.value.gridRows,
      gridColumns: layoutDraft.value.gridColumns,
      elements: layoutDraft.value.elements.map(toElementPayload),
    })
    store.replaceWorkspace(updated)
    ElMessage.success('会议布局已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  }
}
```

Add `replaceWorkspace` in `frontend/src/stores/workspace.js`:

```js
function replaceWorkspace(value) {
  workspace.value = normalizeWorkspace(value)
  dirty.value = false
}
```

- [ ] **Step 6: Run contract tests**

Run:

```powershell
cd frontend
npm.cmd test -- tests/workbench-modes-contract.test.js tests/venue-layout-editor.runtime.test.js
```

Expected: PASS.

- [ ] **Step 7: Commit and push**

Run:

```powershell
git add frontend/src/views/WorkbenchView.vue frontend/src/components/VenueLayoutEditor.vue frontend/src/components/VenueElementPanel.vue frontend/src/stores/workspace.js frontend/src/api/meeting.js frontend/tests/workbench-modes-contract.test.js frontend/tests/venue-layout-editor.runtime.test.js
git commit -m "新增会议工作台模式和布局编辑入口"
git push origin main
```

---

### Task 6: Region Marker Frontend

**Files:**
- Create: `frontend/src/utils/seatRegions.js`
- Create: `frontend/tests/seat-regions.test.js`
- Create: `frontend/src/components/RegionMarkerPanel.vue`
- Modify: `frontend/src/components/VenueCanvas.vue`
- Modify: `frontend/src/views/WorkbenchView.vue`
- Modify: `frontend/src/api/meeting.js`
- Create: `frontend/tests/region-marker-contract.test.js`

**Interfaces:**
- Produces helper:

```js
export function reservedItems(items)
export function connectedSeatGroups(elementIds, elements)
export function regionLabelAnchors(regionItem, elements)
export function toggleSeatSelection(selection, elementId)
```

- Produces API call:

```js
meetingApi.saveReservedAreas(planId, { reservedAreas })
```

- `VenueCanvas` emits:

```js
emit('marker-seat-toggle', element)
emit('marker-select', item)
```

- [ ] **Step 1: Write failing helper tests**

Create `frontend/tests/seat-regions.test.js`:

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import {
  connectedSeatGroups,
  regionLabelAnchors,
  reservedItems,
  toggleSeatSelection,
} from '../src/utils/seatRegions.js'

const elements = [
  { id: 'a', kind: 'SEAT', row: 1, column: 1, rowSpan: 1, columnSpan: 1 },
  { id: 'b', kind: 'SEAT', row: 1, column: 2, rowSpan: 1, columnSpan: 1 },
  { id: 'c', kind: 'SEAT', row: 4, column: 4, rowSpan: 1, columnSpan: 1 },
]

test('区域标记只读取 RESERVED 项', () => {
  assert.deepEqual(
    reservedItems([{ type: 'PERSON' }, { type: 'RESERVED', id: 'r1' }]).map((item) => item.id),
    ['r1'],
  )
})

test('分散区域拆成多个连续标签组', () => {
  assert.deepEqual(connectedSeatGroups(['a', 'b', 'c'], elements), [['a', 'b'], ['c']])
})

test('区域标签锚点按连续区域中心计算', () => {
  const anchors = regionLabelAnchors({ label: '嘉宾', targetElementIds: ['a', 'b', 'c'] }, elements)
  assert.equal(anchors.length, 2)
  assert.equal(anchors[0].label, '嘉宾')
})

test('双击和单击都复用座位选择切换', () => {
  assert.deepEqual([...toggleSeatSelection(new Set(['a']), 'a')], [])
  assert.deepEqual([...toggleSeatSelection(new Set(['a']), 'b')].sort(), ['a', 'b'])
})
```

- [ ] **Step 2: Run helper tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- tests/seat-regions.test.js
```

Expected: FAIL because `seatRegions.js` does not exist.

- [ ] **Step 3: Implement `seatRegions.js`**

Implement:

- `reservedItems(items)`: filter `item.type === 'RESERVED'`.
- `toggleSeatSelection(selection, elementId)`: clone a `Set`, delete if present, add otherwise.
- `connectedSeatGroups(elementIds, elements)`: use adjacency by grid cells sharing an edge. For 1x1 seats this is direct row/column neighbor; for spans, compare occupied cells.
- `regionLabelAnchors(regionItem, elements)`: split into connected groups, compute bounding box center per group, return `{ id, label, elementIds, row, column, rowSpan, columnSpan, centerRow, centerColumn }`.

- [ ] **Step 4: Run helper tests**

Run:

```powershell
cd frontend
npm.cmd test -- tests/seat-regions.test.js
```

Expected: PASS.

- [ ] **Step 5: Write failing component contract tests**

Create `frontend/tests/region-marker-contract.test.js`:

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('画布渲染区域标记中心标签并支持双击切换座位', async () => {
  const source = await readFile(new URL('../src/components/VenueCanvas.vue', import.meta.url), 'utf8')

  assert.match(source, /region-label/)
  assert.match(source, /markerMode/)
  assert.match(source, /@dblclick\.stop="onMarkerSeatToggle\(element\)"/)
  assert.match(source, /marker-seat-toggle/)
})

test('区域标记面板提供名称颜色座位数和保存删除操作', async () => {
  const source = await readFile(new URL('../src/components/RegionMarkerPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /标记名称/)
  assert.match(source, /已选座位/)
  assert.match(source, /添加座位/)
  assert.match(source, /移除座位/)
  assert.match(source, /删除标记/)
})

test('工作台区域标记模式保存 RESERVED 区域', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /saveReservedAreas/)
  assert.match(source, /RegionMarkerPanel/)
  assert.match(source, /markerSelection/)
})
```

- [ ] **Step 6: Run component contract tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- tests/region-marker-contract.test.js
```

Expected: FAIL because `RegionMarkerPanel.vue` and marker canvas hooks do not exist.

- [ ] **Step 7: Add API method**

Modify `frontend/src/api/meeting.js`:

```js
async saveReservedAreas(planId, data) {
  return unwrap(http.post(`/plans/${planId}/reserved-areas/save`, data))
}
```

- [ ] **Step 8: Implement `RegionMarkerPanel.vue`**

Component contract:

- Props: `modelValue`, `selectedSeatCount`, `mode`, `submitting`.
- Emits: `update:modelValue`, `mode`, `save`, `delete`, `cancel`.
- Fields:
  - `标记名称`
  - color swatches
  - selected seat count
  - mode buttons `添加座位` and `移除座位`
  - save/delete/cancel actions

Use default swatches:

```js
const markerSwatches = [
  { name: '浅黄', value: '#FEF3C7' },
  { name: '浅蓝', value: '#DBEAFE' },
  { name: '浅绿', value: '#DCFCE7' },
  { name: '浅粉', value: '#FCE7F3' },
  { name: '浅紫', value: '#EDE9FE' },
  { name: '浅橙', value: '#FFEDD5' },
  { name: '浅青', value: '#CCFBF1' },
]
```

- [ ] **Step 9: Modify `VenueCanvas.vue`**

Add props:

```js
markerMode: { type: Boolean, default: false },
markerSelectionIds: { type: Array, default: () => [] },
```

Add computed:

- `reservedAreaItems` from `reservedItems(workspace.items)`.
- `regionAnchors` from `regionLabelAnchors`.
- `reservedItemByElementId`.

Rendering rules:

- For reserved seats, use item background color and hide seat text.
- Render one `.region-label` per anchor over the canvas.
- Click reserved label emits `marker-select`.
- In marker mode, click or double-click empty seat emits `marker-seat-toggle`.
- Do not allow participant drop on reserved seats.

- [ ] **Step 10: Modify `WorkbenchView.vue` marker mode**

Add state:

```js
const markerSelection = ref(new Set())
const markerDraft = reactive({ id: '', label: '', backgroundColor: '#FEF3C7', textColor: '#172033', bold: true })
const markerEditMode = ref('add')
```

Add functions:

- `toggleMarkerSeat(element)`.
- `selectReservedMarker(item)`.
- `saveReservedAreas()`.
- `deleteReservedMarker()`.
- `reservedAreaPayload()`.

On save, send all existing reserved areas plus current draft to backend:

```js
await meetingApi.saveReservedAreas(workspace.value.plan.id, {
  reservedAreas: reservedAreaPayload(),
})
await store.loadWorkspace()
```

Block saving if label is blank or selection is empty.

- [ ] **Step 11: Run frontend marker tests**

Run:

```powershell
cd frontend
npm.cmd test -- tests/seat-regions.test.js tests/region-marker-contract.test.js
```

Expected: PASS.

- [ ] **Step 12: Commit and push**

Run:

```powershell
git add frontend/src/utils/seatRegions.js frontend/tests/seat-regions.test.js frontend/src/components/RegionMarkerPanel.vue frontend/src/components/VenueCanvas.vue frontend/src/views/WorkbenchView.vue frontend/src/api/meeting.js frontend/tests/region-marker-contract.test.js
git commit -m "新增区域标记前端交互"
git push origin main
```

---

### Task 7: Seat Labels and Color Grouping on Canvas

**Files:**
- Create: `frontend/src/utils/participantColorGroups.js`
- Create: `frontend/tests/participant-color-groups.test.js`
- Modify: `frontend/src/components/VenueCanvas.vue`
- Modify: `frontend/src/views/WorkbenchView.vue`
- Modify: `frontend/tests/participant-component-contract.test.js`
- Modify: `frontend/tests/workbench-layout.test.js`

**Interfaces:**
- Produces:

```js
export const GROUP_COLORS = Object.freeze([...])
export function buildParticipantColorGroups(participants, fieldCode)
```

- `VenueCanvas` props:

```js
seatLabels: { type: Object, default: () => new Map() }
rowLabels: { type: Array, default: () => [] }
participantColorById: { type: Object, default: () => new Map() }
colorGroupField: { type: String, default: '' }
```

- [ ] **Step 1: Write failing color tests**

Create `frontend/tests/participant-color-groups.test.js`:

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import { buildParticipantColorGroups, GROUP_COLORS } from '../src/utils/participantColorGroups.js'

test('按动态字段主值分配浅色且空值不着色', () => {
  const result = buildParticipantColorGroups(
    [
      { id: 'p1', primaryAttributes: { 部门: '部门1' } },
      { id: 'p2', primaryAttributes: { 部门: '部门2' } },
      { id: 'p3', primaryAttributes: {} },
      { id: 'p4', primaryAttributes: { 部门: '部门1' } },
    ],
    '部门',
  )

  assert.equal(result.colorByParticipantId.get('p1'), GROUP_COLORS[0].backgroundColor)
  assert.equal(result.colorByParticipantId.get('p4'), GROUP_COLORS[0].backgroundColor)
  assert.equal(result.colorByParticipantId.get('p2'), GROUP_COLORS[1].backgroundColor)
  assert.equal(result.colorByParticipantId.has('p3'), false)
  assert.deepEqual(result.legend.map((item) => item.value), ['部门1', '部门2'])
})
```

- [ ] **Step 2: Run color tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- tests/participant-color-groups.test.js
```

Expected: FAIL because helper does not exist.

- [ ] **Step 3: Implement color helper**

Create `frontend/src/utils/participantColorGroups.js`:

```js
export const GROUP_COLORS = Object.freeze([
  Object.freeze({ name: '浅黄', backgroundColor: '#FEF3C7', textColor: '#172033' }),
  Object.freeze({ name: '浅蓝', backgroundColor: '#DBEAFE', textColor: '#172033' }),
  Object.freeze({ name: '浅绿', backgroundColor: '#DCFCE7', textColor: '#172033' }),
  Object.freeze({ name: '浅粉', backgroundColor: '#FCE7F3', textColor: '#172033' }),
  Object.freeze({ name: '浅紫', backgroundColor: '#EDE9FE', textColor: '#172033' }),
  Object.freeze({ name: '浅橙', backgroundColor: '#FFEDD5', textColor: '#172033' }),
  Object.freeze({ name: '浅青', backgroundColor: '#CCFBF1', textColor: '#172033' }),
])

function valueOf(participant, fieldCode) {
  const value = participant?.primaryAttributes?.[fieldCode]
  return value == null ? '' : String(value).trim()
}

export function buildParticipantColorGroups(participants, fieldCode) {
  const colorByValue = new Map()
  const colorByParticipantId = new Map()
  const legend = []
  if (!fieldCode) return { colorByParticipantId, legend }
  for (const participant of participants || []) {
    const value = valueOf(participant, fieldCode)
    if (!value) continue
    if (!colorByValue.has(value)) {
      const color = GROUP_COLORS[colorByValue.size % GROUP_COLORS.length]
      colorByValue.set(value, color)
      legend.push({ value, ...color })
    }
    colorByParticipantId.set(participant.id, colorByValue.get(value).backgroundColor)
  }
  return { colorByParticipantId, legend }
}
```

- [ ] **Step 4: Run color tests**

Run:

```powershell
cd frontend
npm.cmd test -- tests/participant-color-groups.test.js
```

Expected: PASS.

- [ ] **Step 5: Write failing canvas/workbench contract tests**

Extend `frontend/tests/participant-component-contract.test.js`:

```js
test('座位画布显示动态座位编号和左右排号', async () => {
  const { readFile } = await import('node:fs/promises')
  const source = await readFile(new URL('../src/components/VenueCanvas.vue', import.meta.url), 'utf8')

  assert.match(source, /seatLabels/)
  assert.match(source, /row-label left/)
  assert.match(source, /row-label right/)
  assert.match(source, /seat-code/)
})
```

Extend `frontend/tests/workbench-layout.test.js`:

```js
test('排座工作台提供颜色分组下拉和图例', async () => {
  const { readFile } = await import('node:fs/promises')
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /colorGroupField/)
  assert.match(source, /颜色分组/)
  assert.match(source, /color-legend/)
})
```

- [ ] **Step 6: Run contract tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- tests/participant-component-contract.test.js tests/workbench-layout.test.js
```

Expected: FAIL because canvas and toolbar do not yet expose labels/grouping.

- [ ] **Step 7: Modify `VenueCanvas.vue`**

Use `computeSeatLabels(workspace.layout.elements)` inside canvas or accept labels from parent. Prefer parent-computed labels so Workbench can reuse them for export parameters.

Rendering changes:

- Empty seat text: `seatLabel(element.id)`.
- Person seat text: first line `seatLabel(element.id)`, second line person name.
- Tooltip includes seat label, employee number, dynamic summary, lock state, color group field/value.
- Region marker seats render no per-seat text.
- Render row labels:

```vue
<span
  v-for="row in rowLabels"
  :key="`left-${row.sourceRow}`"
  class="row-label left"
  :style="{ top: `${(row.sourceRow - 0.5) * unit}px` }"
>{{ row.displayRow }}排</span>
```

Add mirrored right label.

Use `participantColorById.get(person.id)` before `person.displayColor`.

- [ ] **Step 8: Modify `WorkbenchView.vue`**

Add:

```js
const colorGroupField = ref('')
const seatNumbering = computed(() => computeSeatLabels(workspace.value?.layout?.elements || []))
const colorGroups = computed(() => buildParticipantColorGroups(workspace.value?.participants || [], colorGroupField.value))
```

Pass to canvas:

```vue
:seat-labels="seatNumbering.labelsByElementId"
:row-labels="seatNumbering.rows"
:participant-color-by-id="colorGroups.colorByParticipantId"
:color-group-field="colorGroupField"
```

Toolbar:

- Add `el-select` labeled `颜色分组`.
- Options from `groupableFields(workspace.fieldDefinitions)`.
- Do not include name or employee number.
- Add `.color-legend` compact legend with first five values and a popover for the rest.

- [ ] **Step 9: Run tests**

Run:

```powershell
cd frontend
npm.cmd test -- tests/seat-numbering.test.js tests/participant-color-groups.test.js tests/participant-component-contract.test.js tests/workbench-layout.test.js
```

Expected: PASS.

- [ ] **Step 10: Commit and push**

Run:

```powershell
git add frontend/src/utils/participantColorGroups.js frontend/tests/participant-color-groups.test.js frontend/src/components/VenueCanvas.vue frontend/src/views/WorkbenchView.vue frontend/tests/participant-component-contract.test.js frontend/tests/workbench-layout.test.js
git commit -m "新增座位编号和颜色分组展示"
git push origin main
```

---

### Task 8: Excel-only Export Backend and Frontend

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/company/meetinghelper/export/service/ExportService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/export/api/ExportController.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`
- Modify: `frontend/src/api/meeting.js`
- Modify: `frontend/src/stores/workspace.js`
- Modify: `frontend/src/views/WorkbenchView.vue`
- Modify: `frontend/tests/api-path.test.js`
- Create: `frontend/tests/export-contract.test.js`

**Interfaces:**
- Produces endpoints:

```http
GET /meetings/{meetingId}/exports/participants?versionId=&colorGroupField=
GET /meetings/{meetingId}/exports/layout?versionId=&colorGroupField=
```

- Removes endpoint:

```http
GET /meetings/{meetingId}/exports/pdf
```

- Produces API methods:

```js
meetingApi.exportParticipantsExcel(meetingId, { versionId })
meetingApi.exportLayoutExcel(meetingId, { versionId, colorGroupField })
```

- [ ] **Step 1: Write failing backend export tests**

Modify `MeetingHelperIntegrationTests`:

```java
@Test
void draftAndPublishedParticipantExcelExportsIncludeDynamicFieldsAndSeatLabel() throws Exception {
    String meetingId = createSeatingScenario(1, 1);
    WorkspaceResponse workspace = workspaceService.getWorkspace(meetingId);
    byte[] bytes = exportService.exportParticipantsExcel(meetingId, null);

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        XSSFSheet sheet = workbook.getSheet("人员名单");
        assertThat(cellValues(sheet.getRow(0))).contains("工号", "姓名", "座位");
        assertThat(cellValues(sheet.getRow(1)).getLast()).contains("1排1");
    }

    VersionResult version = planVersionService.create(
            workspace.plan().id(),
            new CreateVersionRequest("导出版本-" + UUID.randomUUID(), "", false)
    );
    assertThat(exportService.exportParticipantsExcel(meetingId, version.id())).isNotEmpty();
}

@Test
void layoutExcelExportsReservedAreasAndPdfEndpointIsGone() throws Exception {
    String meetingId = createSeatingScenario(0, 0);
    WorkspaceResponse workspace = workspaceService.getWorkspace(meetingId);
    String seatId = workspace.layout().elements().stream()
            .filter(value -> value.kind().equals("SEAT"))
            .findFirst()
            .orElseThrow()
            .id();
    seatingService.replaceReservedAreas(
            workspace.plan().id(),
            new SaveReservedAreasRequest(List.of(
                    new ReservedAreaInput(null, "嘉宾", "#FEF3C7", "#172033", true, List.of(seatId))
            ))
    );

    byte[] bytes = exportService.exportLayoutExcel(meetingId, null, "");
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        assertThat(workbook.getSheet("排座图")).isNotNull();
        assertThat(workbook.getSheet("座位明细")).isNotNull();
    }

    mockMvc.perform(get("/meetings/{meetingId}/exports/pdf", meetingId).header(USER_HEADER, DEFAULT_USER))
            .andExpect(status().isNotFound());
}
```

Adjust imports after PDF removal.

- [ ] **Step 2: Run backend export tests to verify failure**

Run:

```powershell
cd backend
mvn.cmd test -Dtest=MeetingHelperIntegrationTests#draftAndPublishedParticipantExcelExportsIncludeDynamicFieldsAndSeatLabel,MeetingHelperIntegrationTests#layoutExcelExportsReservedAreasAndPdfEndpointIsGone
```

Expected: FAIL because new export methods do not exist and PDF endpoint still exists.

- [ ] **Step 3: Refactor `ExportService.java`**

Replace current `exportExcel(String meetingId, String versionId)` and `exportPdf` surface with:

```java
public byte[] exportParticipantsExcel(String meetingId, String versionId)
public byte[] exportLayoutExcel(String meetingId, String versionId, String colorGroupField)
```

Add:

```java
private WorkspaceResponse resolveWorkspace(String meetingId, String versionId) {
    meetingAccessService.requireOwnedMeeting(meetingId);
    if (versionId == null || versionId.isBlank()) {
        return workspaceService.getWorkspace(meetingId);
    }
    return versionService.getSnapshotForMeeting(meetingId, versionId);
}
```

Inject `WorkspaceService` and `SeatLabelService`.

Participant Excel:

- Sheet name `人员名单`.
- Headers `工号`, `姓名`, dynamic field labels, `座位`.
- Use `SeatLabelService.labelsByElementId(workspace.layout().elements())` and participant `assignedElementId`.

Layout Excel:

- Sheet `排座图`.
- Sheet `座位明细`.
- Use dynamic seat labels.
- For `RESERVED` item, write label in the first target cell and fill all target cells with marker color. Do not repeat label in every target cell.
- For person item, write `m排n\n姓名`.
- Apply optional color group only to person items with a non-empty value.

Remove:

- PDFBox imports.
- `exportPdf`.
- `drawPdf`.
- `compactText`.
- PDF font loading.

- [ ] **Step 4: Refactor `ExportController.java`**

Replace endpoints with:

```java
@GetMapping("/participants")
public ResponseEntity<byte[]> participants(
        @PathVariable String meetingId,
        @RequestParam(required = false) String versionId
) {
    return download(exportService.exportParticipantsExcel(meetingId, versionId), "meeting-participants.xlsx");
}

@GetMapping("/layout")
public ResponseEntity<byte[]> layout(
        @PathVariable String meetingId,
        @RequestParam(required = false) String versionId,
        @RequestParam(required = false, defaultValue = "") String colorGroupField
) {
    return download(exportService.exportLayoutExcel(meetingId, versionId, colorGroupField), "meeting-layout.xlsx");
}
```

Keep content type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

- [ ] **Step 5: Remove PDFBox dependency**

Modify `backend/pom.xml`:

- Remove `<pdfbox.version>`.
- Remove `org.apache.pdfbox:pdfbox` dependency.

Remove all PDFBox imports from tests.

- [ ] **Step 6: Run backend export tests**

Run:

```powershell
cd backend
mvn.cmd test -Dtest=MeetingHelperIntegrationTests#draftAndPublishedParticipantExcelExportsIncludeDynamicFieldsAndSeatLabel,MeetingHelperIntegrationTests#layoutExcelExportsReservedAreasAndPdfEndpointIsGone
```

Expected: PASS.

- [ ] **Step 7: Write failing frontend export tests**

Create `frontend/tests/export-contract.test.js`:

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('前端只保留人员名单和排座图 Excel 导出', async () => {
  const workbench = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')
  const api = await readFile(new URL('../src/api/meeting.js', import.meta.url), 'utf8')

  assert.match(workbench, /导出人员名单/)
  assert.match(workbench, /导出排座图/)
  assert.doesNotMatch(workbench, /PDF/)
  assert.match(api, /exportParticipantsExcel/)
  assert.match(api, /exportLayoutExcel/)
  assert.doesNotMatch(api, /exports\/pdf/)
})
```

- [ ] **Step 8: Run frontend export tests to verify failure**

Run:

```powershell
cd frontend
npm.cmd test -- tests/export-contract.test.js
```

Expected: FAIL because current UI/API still exposes old export shape.

- [ ] **Step 9: Refactor frontend API and store**

Modify `frontend/src/api/meeting.js`:

```js
async exportParticipantsExcel(meetingId, { versionId } = {}) {
  return raw(http.get(`/meetings/${meetingId}/exports/participants`, {
    responseType: 'arraybuffer',
    params: { versionId },
  }))
},
async exportLayoutExcel(meetingId, { versionId, colorGroupField } = {}) {
  return raw(http.get(`/meetings/${meetingId}/exports/layout`, {
    responseType: 'arraybuffer',
    params: { versionId, colorGroupField },
  }))
}
```

Modify `workspace.js`:

- Replace `exportPlan(type, versionId)` with:
  - `exportParticipants(versionId)`
  - `exportLayout(versionId, colorGroupField)`
- Allow `versionId` to be undefined for draft.
- Filename examples:
  - `${meeting.name}-人员名单-草稿.xlsx`
  - `${meeting.name}-排座图-草稿.xlsx`
  - `${meeting.name}-人员名单-${versionName}.xlsx`

Modify `WorkbenchView.vue`:

- Remove PDF dropdown.
- Add two buttons:
  - `导出人员名单`
  - `导出排座图`
- Before draft export, call `saveDraft(true)` if dirty.

- [ ] **Step 10: Run frontend export tests**

Run:

```powershell
cd frontend
npm.cmd test -- tests/export-contract.test.js tests/api-path.test.js
```

Expected: PASS.

- [ ] **Step 11: Commit and push**

Run:

```powershell
git add backend/pom.xml backend/src/main/java/com/company/meetinghelper/export/service/ExportService.java backend/src/main/java/com/company/meetinghelper/export/api/ExportController.java backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java frontend/src/api/meeting.js frontend/src/stores/workspace.js frontend/src/views/WorkbenchView.vue frontend/tests/api-path.test.js frontend/tests/export-contract.test.js
git commit -m "改为仅支持Excel导出"
git push origin main
```

---

### Task 9: End-to-end Integration and Changelog

**Files:**
- Modify: `CHANGELOG.md`
- Run every test file added in prior tasks without changing test expectations during this final verification task.

**Interfaces:**
- Consumes every feature from Tasks 1 through 8.
- Produces a verified final state on `main`.

- [ ] **Step 1: Run full frontend test suite**

Run:

```powershell
cd frontend
npm.cmd test
```

Expected: all frontend tests pass.

- [ ] **Step 2: Run frontend production build**

Run:

```powershell
cd frontend
npm.cmd run build
```

Expected: build succeeds. Chunk-size warnings are acceptable if no errors occur.

- [ ] **Step 3: Run backend tests**

Run:

```powershell
cd backend
mvn.cmd test
```

Expected: all backend tests pass.

- [ ] **Step 4: Run repository checks**

Run:

```powershell
git diff --check
git status --short --branch
```

Expected: no whitespace errors. Status shows only intended `CHANGELOG.md` change before final commit.

- [ ] **Step 5: Update changelog**

Add entries with the current timestamp in chronological position:

Under `新增`:

```markdown
- `YYYY-MM-DD HH:mm`：会议排座工作台支持草稿布局编辑、区域标记、座位动态编号、人员字段编辑、颜色分组和 Excel 导出。
```

Under `变更`:

```markdown
- `YYYY-MM-DD HH:mm`：导出能力统一为 Excel，移除 PDF 导出入口和后端 PDF 生成逻辑。
```

Under `修复` only if implementation uncovered and fixed an existing defect:

```markdown
- `YYYY-MM-DD HH:mm`：修复会议草稿布局变化后占用关系未同步清理的问题。
```

Keep the existing changelog order test passing.

- [ ] **Step 6: Run changelog order test**

Run:

```powershell
cd frontend
npm.cmd test -- tests/changelog-order.test.js
```

Expected: PASS.

- [ ] **Step 7: Commit and push final verification commit**

Run:

```powershell
git add CHANGELOG.md
git commit -m "更新会议排座增强变更记录"
git push origin main
```

- [ ] **Step 8: Final status check**

Run:

```powershell
git status --short --branch
```

Expected: `## main...origin/main` and no changed files.

---

## Self-Review

Spec coverage:

- Single add can add columns: Task 2 frontend model and Task 3 backend update field registry.
- Draft pending/all participant edit: Tasks 2 and 3.
- Row labels and dynamic `m排n`: Tasks 1 and 7, backend export in Task 8.
- Draft meeting layout editing with protections: Tasks 4 and 5.
- Region marker mode with irregular selection, center labels, and double click: Tasks 4 and 6.
- Color grouping: Task 7, export application in Task 8.
- Excel-only export and PDF removal: Task 8.
- Changelog and final verification: Task 9.

Type consistency:

- Frontend seat labels use `labelsByElementId` and `rows`; backend uses `labelsByElementId` and `rowLabels`.
- Reserved area request names match frontend payload: `label`, `backgroundColor`, `textColor`, `bold`, `targetElementIds`.
- Participant update request names match frontend payload: `name`, `records`, `attributes`.
- Export endpoint names match frontend API: `/exports/participants` and `/exports/layout`.
