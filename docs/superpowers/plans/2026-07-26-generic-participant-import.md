# 通用人员导入与扩展字段 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用一份仅要求工号和姓名的 Excel 模板，自动发现会议扩展字段，正确合并或保留同一人员的多条动态记录，并让工作区、版本、导出和前端人员列表全部由动态字段驱动。

**Architecture:** 会议人员只保存稳定身份和出席状态；会议字段定义保存历次导入表头并集；人员动态记录以 JSON 保存每行扩展数据。导入通过纯解析器和纯合并器完成去重、兼容补全与冲突追加，工作区统一聚合主值和重复值，排座继续通过稳定的人员 ID 关联现有排座项。

**Tech Stack:** Java 21、Spring Boot 4.0.7、Spring Data JPA、PostgreSQL、H2 测试库、Apache POI 5.5.1、Vue 3.5、Element Plus 2.8、Axios 1.7、Node 20 原生测试。

## Global Constraints

- Excel 只强制要求“工号”和“姓名”，其他非空表头全部按文本扩展字段处理。
- 同一会议历次导入字段取并集，已有字段顺序和值不得因后续文件缺列而丢失。
- 完全重复数据跳过；无冲突的新字段补全唯一已有记录；同名字段值冲突时追加动态记录。
- 工号和姓名支持搜索但不提供分组；只有会议扩展字段提供分组。
- 不增加字段类型、字段改名、字段启停、导入批次、共享和多人协同编辑。
- 所有表名保持 `t_` 前缀；`DDL/meeting_helper.sql` 只允许 `CREATE TABLE` 和 `COMMENT`，禁止 `ALTER`。
- DDL 中所有表和字段必须有中文注释。
- Controller、Service、Repository 的 public 方法必须有 Javadoc。
- 前端继续使用 JavaScript，不引入 TypeScript，不提交 `package-lock.json`，Element Plus 保持 `~2.8.0`。
- 所有变更记录使用 `YYYY-MM-DD HH:mm  ` 时间格式；Git 提交信息和推送说明使用中文。
- DDL 改动完成后删除本地 `meeting_helper` 数据库并使用完整 DDL 重建。

---

## 文件结构

### 后端新增

- `participant/entity/MeetingParticipantFieldEntity.java`：会议扩展字段定义。
- `participant/entity/ParticipantRecordEntity.java`：人员动态记录。
- `participant/repository/MeetingParticipantFieldRepository.java`：按会议查询和判重扩展字段。
- `participant/repository/ParticipantRecordRepository.java`：按人员批量查询动态记录。
- `participant/service/ParticipantRecordMerger.java`：纯兼容性判断与合并决策。
- `importing/service/ParticipantWorkbookParser.java`：单一 Excel 模板生成和解析。
- `importing/service/model/ParsedParticipantRow.java`：解析后的原始人员行。
- `common/user/CurrentUserProvider.java`：读取当前请求用户 ID。
- `meeting/service/MeetingAccessService.java`：统一校验会议归属。
- `backend/src/test/java/com/company/meetinghelper/ParticipantRecordMergerTests.java`：动态记录合并单元测试。

### 后端删除

- `award/**`：颁奖专用实体和仓储。
- `importing/service/strategy/**`：通用会议和颁奖会议导入策略。
- `importing/api/dto/response/AwardRow.java`
- `importing/api/dto/response/DuplicateGroup.java`
- `importing/api/dto/response/SheetDescriptor.java`
- 其他只服务于多模板和奖项专用流程的 DTO。

### 前端新增

- `frontend/src/utils/participantFields.js`：扩展字段取值、搜索、分组和卡片摘要纯函数。
- `frontend/tests/participant-fields.test.js`：动态字段前端单元测试。

### 主要修改

- `DDL/meeting_helper.sql`
- `participant/entity/ParticipantEntity.java`
- `participant/service/ParticipantService.java`
- `importing/service/ImportService.java`
- `importing/api/ImportController.java`
- `workspace/api/dto/response/WorkspaceResponse.java`
- `workspace/service/WorkspaceService.java`
- `seating/service/PlanVersionService.java`
- `seating/service/SeatingService.java`
- `export/service/ExportService.java`
- `bootstrap/DemoDataInitializer.java`
- `frontend/src/api/http.js`
- `frontend/src/api/meeting.js`
- `frontend/src/components/ImportDialog.vue`
- `frontend/src/components/AddParticipantDialog.vue`
- `frontend/src/components/ParticipantPanel.vue`
- `frontend/src/components/VenueCanvas.vue`
- `frontend/src/views/WorkbenchView.vue`
- `frontend/src/stores/workspace.js`
- `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`
- `CHANGELOG.md`

---

### Task 1: 建立最小通用人员表结构

**Files:**
- Modify: `DDL/meeting_helper.sql`
- Create: `backend/src/main/java/com/company/meetinghelper/participant/entity/MeetingParticipantFieldEntity.java`
- Create: `backend/src/main/java/com/company/meetinghelper/participant/entity/ParticipantRecordEntity.java`
- Create: `backend/src/main/java/com/company/meetinghelper/participant/repository/MeetingParticipantFieldRepository.java`
- Create: `backend/src/main/java/com/company/meetinghelper/participant/repository/ParticipantRecordRepository.java`
- Test: `backend/src/test/java/com/company/meetinghelper/DdlConventionTests.java`

**Interfaces:**
- Produces: `MeetingParticipantFieldRepository.findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(String)`
- Produces: `MeetingParticipantFieldRepository.findByMeetingIdAndFieldNameIgnoreCaseAndDeletedFalse(String, String)`
- Produces: `ParticipantRecordRepository.findAllByParticipantIdInAndDeletedFalseOrderByParticipantIdAscRecordOrderAsc(Collection<String>)`
- Produces: `ParticipantRecordRepository.findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(String)`

- [ ] **Step 1: 扩充 DDL 规范测试**

在 `DdlConventionTests` 增加断言：

```java
assertThat(sql).contains("create table if not exists t_meeting_participant_fields");
assertThat(sql).contains("create table if not exists t_participant_records");
assertThat(sql).contains("comment on table t_meeting_participant_fields");
assertThat(sql).contains("comment on table t_participant_records");
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn.cmd -Dtest=DdlConventionTests test
```

Expected: FAIL，提示缺少两张通用表。

- [ ] **Step 3: 增加通用人员字段和记录 DDL**

在暂时保留旧人员字段和颁奖表的前提下新增：

```sql
create table if not exists t_meeting_participant_fields (
    id varchar(36) primary key,
    meeting_id varchar(36) not null,
    field_name varchar(120) not null,
    sort_order integer not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_t_participant_field_meeting foreign key (meeting_id) references t_meetings(id),
    constraint uk_t_participant_field_name unique (meeting_id, field_name)
);

create table if not exists t_participant_records (
    id varchar(36) primary key,
    participant_id varchar(36) not null,
    record_order integer not null,
    attributes_json text not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_t_participant_record_person foreign key (participant_id) references t_participants(id),
    constraint uk_t_participant_record_order unique (participant_id, record_order)
);
```

为新表全部字段补充 `COMMENT`。旧人员业务字段和 `t_award_records` 暂时保留，等所有调用方迁移完成后在 Task 10 一次清理，确保本任务结束时后端仍可编译。

- [ ] **Step 4: 创建实体和仓储**

实体只包含最小字段：

```java
@Entity
@Table(name = "t_meeting_participant_fields")
public class MeetingParticipantFieldEntity extends AuditedEntity {
    private String meetingId;
    private String fieldName;
    private int sortOrder;
}

@Entity
@Table(name = "t_participant_records")
public class ParticipantRecordEntity extends AuditedEntity {
    private String participantId;
    private int recordOrder;

    @Column(columnDefinition = "text")
    private String attributesJson;
}
```

- [ ] **Step 5: 运行 DDL 和后端编译**

Run:

```powershell
mvn.cmd -Dtest=DdlConventionTests test
mvn.cmd -DskipTests compile
```

Expected: DDL 测试通过，后端编译成功。

- [ ] **Step 6: 提交**

```powershell
git add DDL backend/src/main/java/com/company/meetinghelper/participant backend/src/test/java/com/company/meetinghelper/DdlConventionTests.java
git commit -m "建立通用人员扩展字段数据模型"
```

---

### Task 2: 实现动态记录兼容合并算法

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/participant/service/ParticipantRecordMerger.java`
- Create: `backend/src/test/java/com/company/meetinghelper/ParticipantRecordMergerTests.java`

**Interfaces:**
- Produces: `ParticipantRecordMerger.decide(Map<String,String> incoming, List<RecordValue> existing)`
- Produces: `MergeDecision(Action action, String targetRecordId, Map<String,String> mergedAttributes)`
- Action values: `SKIP`, `MERGE`, `APPEND`

- [ ] **Step 1: 写失败测试覆盖四条合并规则**

```java
@Test
void enrichesTheOnlyCompatibleRecord() {
    var existing = List.of(new RecordValue("r1", 1, Map.of("字段1", "值1")));
    var result = merger.decide(
            Map.of("字段1", "值1", "字段2", "值2"),
            existing
    );
    assertThat(result.action()).isEqualTo(Action.MERGE);
    assertThat(result.targetRecordId()).isEqualTo("r1");
    assertThat(result.mergedAttributes()).containsEntry("字段2", "值2");
}

@Test
void skipsIncomingDataAlreadyContainedByARecord() {
    var existing = List.of(new RecordValue(
            "r1", 1, Map.of("字段1", "值1", "字段2", "值2")));
    assertThat(merger.decide(Map.of("字段1", "值1"), existing).action())
            .isEqualTo(Action.SKIP);
}

@Test
void appendsWhenSharedFieldsConflict() {
    var existing = List.of(new RecordValue(
            "r1", 1, Map.of("批次", "第二批", "奖项", "优秀项目奖")));
    assertThat(merger.decide(
            Map.of("批次", "第三批", "奖项", "创新奖"), existing).action())
            .isEqualTo(Action.APPEND);
}

@Test
void appendsWhenSeveralRecordsAreCompatibleButTargetIsAmbiguous() {
    var existing = List.of(
            new RecordValue("r1", 1, Map.of("批次", "第二批")),
            new RecordValue("r2", 2, Map.of("批次", "第三批"))
    );
    assertThat(merger.decide(Map.of("部门", "研发部"), existing).action())
            .isEqualTo(Action.APPEND);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn.cmd -Dtest=ParticipantRecordMergerTests test
```

Expected: FAIL，类和类型尚不存在。

- [ ] **Step 3: 实现纯合并器**

核心判断：

```java
boolean containsAll(Map<String, String> existing, Map<String, String> incoming) {
    return incoming.entrySet().stream()
            .allMatch(entry -> Objects.equals(existing.get(entry.getKey()), entry.getValue()));
}

boolean compatible(Map<String, String> existing, Map<String, String> incoming) {
    return incoming.entrySet().stream()
            .filter(entry -> existing.containsKey(entry.getKey()))
            .allMatch(entry -> Objects.equals(existing.get(entry.getKey()), entry.getValue()));
}
```

执行顺序：

1. 去除键和值首尾空格并删除空值；
2. 任一已有记录包含全部新值时返回 `SKIP`；
3. 只有一个兼容记录时返回 `MERGE`；
4. 没有兼容记录或兼容记录超过一个时返回 `APPEND`。

- [ ] **Step 4: 运行测试**

Run:

```powershell
mvn.cmd -Dtest=ParticipantRecordMergerTests test
```

Expected: 4 项合并测试全部通过。

- [ ] **Step 5: 提交**

```powershell
git add backend/src/main/java/com/company/meetinghelper/participant/service/ParticipantRecordMerger.java backend/src/test/java/com/company/meetinghelper/ParticipantRecordMergerTests.java
git commit -m "实现人员动态记录兼容合并算法"
```

---

### Task 3: 用单一解析器替换场景导入策略

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/importing/service/ParticipantWorkbookParser.java`
- Create: `backend/src/main/java/com/company/meetinghelper/importing/service/model/ParsedParticipantRow.java`
- Create: `backend/src/main/java/com/company/meetinghelper/importing/service/model/ParsedParticipantWorkbook.java`
- Create: `backend/src/test/java/com/company/meetinghelper/ParticipantWorkbookParserTests.java`

**Interfaces:**
- Produces: `ParticipantWorkbookParser.createTemplate()`
- Produces: `ParticipantWorkbookParser.parse(XSSFWorkbook)`
- Produces: `ParsedParticipantWorkbook(fieldNames, rows, totalRows, ignoredDuplicateRows, errors)`
- Consumes: employee number regex `^(?:[0-9]{8}|[a-z][0-9]{8})$`

- [ ] **Step 1: 写模板和解析失败测试**

测试创建只有两个默认表头的模板，并解析动态列：

```java
assertThat(row.getCell(0).getStringCellValue()).isEqualTo("工号");
assertThat(row.getCell(1).getStringCellValue()).isEqualTo("姓名");

var parsed = parser.parse(workbookWith(
        List.of("工号", "姓名", "字段1", "字段2"),
        List.of(
                List.of("a12345678", "张三", "值1", ""),
                List.of("a12345678", "张三", "值1", "值2")
        )
));
assertThat(parsed.fieldNames()).containsExactly("字段1", "字段2");
assertThat(parsed.rows()).hasSize(2);
```

补充测试：

- 缺少工号或姓名；
- 重复表头；
- 完全重复行自动去重；
- 同工号不同姓名返回阻断错误；
- 英文表头忽略大小写判重；
- 空扩展单元格不进入属性 Map。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn.cmd -Dtest=ParticipantWorkbookParserTests test
```

Expected: 新模板和解析断言失败。

- [ ] **Step 3: 实现单一解析器**

解析结果：

```java
public record ParsedParticipantRow(
        int sourceRow,
        String employeeNo,
        String name,
        Map<String, String> attributes
) {
}

public record ParsedParticipantWorkbook(
        List<String> fieldNames,
        List<ParsedParticipantRow> rows,
        int totalRows,
        int ignoredDuplicateRows,
        List<String> errors
) {
}
```

完全重复键使用规范化后的：

```java
employeeNo + "\u001F" + name + "\u001F" +
fieldNames.stream()
        .map(field -> field + "=" + attributes.getOrDefault(field, ""))
        .collect(joining("\u001E"))
```

- [ ] **Step 4: 保留旧策略并运行新解析器测试**

Run:

```powershell
mvn.cmd -Dtest=ParticipantWorkbookParserTests test
mvn.cmd -DskipTests compile
```

Expected: 新解析器测试通过，后端编译成功；旧解析模型和多模板策略暂时保留，Task 4 切换导入服务后再统一删除。

- [ ] **Step 5: 提交**

```powershell
git add backend/src/main/java/com/company/meetinghelper/importing/service/ParticipantWorkbookParser.java backend/src/main/java/com/company/meetinghelper/importing/service/model/ParsedParticipantRow.java backend/src/main/java/com/company/meetinghelper/importing/service/model/ParsedParticipantWorkbook.java backend/src/test/java/com/company/meetinghelper/ParticipantWorkbookParserTests.java
git commit -m "统一人员Excel模板与动态列解析"
```

---

### Task 4: 重构导入预览和提交事务

**Files:**
- Modify: `backend/src/main/java/com/company/meetinghelper/importing/service/ImportService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/importing/repository/ImportPreviewStore.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/importing/api/ImportController.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/importing/api/dto/response/ImportPreview.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/importing/api/dto/response/CommitResult.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/importing/api/dto/response/ParticipantRow.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/service/model/ParsedWorkbook.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/service/strategy/WorkbookImportStrategy.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/service/strategy/AbstractParticipantImportStrategy.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/service/strategy/GeneralMeetingImportStrategy.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/service/strategy/AwardCeremonyImportStrategy.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/api/dto/request/CommitRequest.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/api/dto/response/AwardRow.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/api/dto/response/DuplicateGroup.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/api/dto/response/SheetDescriptor.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/importing/api/dto/response/TemplateDescriptor.java`
- Test: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`

**Interfaces:**
- Produces: `GET /imports/template`
- Produces: `POST /meetings/{meetingId}/imports/preview` multipart `file`
- Produces: `POST /meetings/{meetingId}/imports/{token}/commit`
- Consumes: repositories and merger from Tasks 1-2

- [ ] **Step 1: 写导入闭环失败测试**

覆盖用户给出的两次导入：

```java
previewAndCommit("工号,姓名,字段1", "a12345678,张三,值1");
previewAndCommit("工号,姓名,字段1,字段2", "a12345678,张三,值1,值2");

var participant = participantRepository
        .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
        .orElseThrow();
var records = recordRepository
        .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(participant.getId());

assertThat(records).hasSize(1);
assertThat(readAttributes(records.getFirst()))
        .containsEntry("字段1", "值1")
        .containsEntry("字段2", "值2");
```

再覆盖：

- 第二批和第三批冲突记录保留两条；
- 完全相同记录不重复保存；
- 字段列表取并集且顺序稳定；
- 同工号不同姓名阻止提交；
- 导入已有已排人员后人员 ID 不变。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn.cmd -Dtest=MeetingHelperIntegrationTests test
```

Expected: 旧导入接口和旧追加逻辑无法满足断言。

- [ ] **Step 3: 重构预览 DTO**

返回最小可展示信息：

```java
public record ImportPreview(
        String token,
        int totalRows,
        int validRows,
        int ignoredDuplicateRows,
        int participantCount,
        int recordCount,
        List<String> newFields,
        List<String> existingFields,
        List<ParticipantRow> rows,
        List<String> errors
) {
}
```

`ParticipantRow` 包含 `sourceRow`、`employeeNo`、`name`、`attributes` 和预期动作文本。

- [ ] **Step 4: 实现字段注册和记录合并事务**

提交顺序：

1. 重新校验预览令牌和会议；
2. 按首次发现顺序新增字段，`sortOrder = max + 1`；
3. 按工号复用或创建人员；
4. 同工号不同姓名抛出 409；
5. 查询人员已有记录并调用 `ParticipantRecordMerger`；
6. `SKIP` 不写库；
7. `MERGE` 更新目标记录 JSON 且保持 `recordOrder`；
8. `APPEND` 使用最大 `recordOrder + 1`；
9. 返回新增人员、合并记录、追加记录、跳过记录统计。

- [ ] **Step 5: 简化控制层路径**

控制器不再接收 `templateCode` 和重复行选择参数：

```java
@GetMapping("/imports/template")
public ResponseEntity<byte[]> template()

@PostMapping("/meetings/{meetingId}/imports/preview")
public ImportPreview preview(@PathVariable String meetingId, @RequestPart MultipartFile file)

@PostMapping("/meetings/{meetingId}/imports/{token}/commit")
public CommitResult commit(@PathVariable String meetingId, @PathVariable String token)
```

- [ ] **Step 6: 运行导入测试**

Run:

```powershell
mvn.cmd -Dtest=MeetingHelperIntegrationTests test
```

Expected: 通用导入、兼容合并、冲突追加和字段并集测试通过。

- [ ] **Step 7: 提交**

```powershell
git add backend/src/main/java/com/company/meetinghelper/importing backend/src/main/java/com/company/meetinghelper/participant backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java
git commit -m "重构通用人员导入预览与提交"
```

---

### Task 5: 将工作区和单人新增改为动态字段驱动

**Files:**
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/api/dto/request/CreateParticipantRequest.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/service/ParticipantService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/workspace/api/dto/response/WorkspaceResponse.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/workspace/service/WorkspaceService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/service/SeatingService.java`
- Test: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`

**Interfaces:**
- Consumes: field and record repositories from Task 1
- Produces: `ParticipantView.primaryAttributes`
- Produces: `ParticipantView.attributeValues`
- Produces: `ParticipantView.records`
- Preserves: `participantId` used by `PlanItemEntity`

- [ ] **Step 1: 写工作区聚合失败测试**

```java
var person = workspace.participants().stream()
        .filter(value -> value.employeeNo().equals("a12345678"))
        .findFirst().orElseThrow();

assertThat(person.primaryAttributes())
        .containsEntry("批次", "第二批");
assertThat(person.attributeValues().get("批次"))
        .containsExactly("第二批", "第三批");
assertThat(person.records()).hasSize(2);
assertThat(workspace.fieldDefinitions().stream().map(FieldDefinitionView::label))
        .containsExactly("批次", "奖项名称");
```

再断言字段定义中工号和姓名 `filterable=false`，扩展字段 `filterable=true`。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn.cmd -Dtest=MeetingHelperIntegrationTests test
```

Expected: 工作区仍返回职级、部门、人员类型、奖项专用字段。

- [ ] **Step 3: 调整创建人员请求**

请求收敛为：

```java
public record CreateParticipantRequest(
        @NotBlank @Pattern(...) String employeeNo,
        @NotBlank String name,
        Map<String, String> attributes,
        String targetElementId
) {
}
```

创建时注册未知扩展字段，并在存在非空属性时创建 `recordOrder=1` 的动态记录。指定 `targetElementId` 时继续使用新人员 ID 直接落座。

- [ ] **Step 4: 调整工作区 DTO**

人员视图改为：

```java
public record ParticipantView(
        String id,
        String employeeNo,
        String name,
        Map<String, String> primaryAttributes,
        Map<String, List<String>> attributeValues,
        List<ParticipantRecordView> records,
        String attendanceStatus,
        boolean locked,
        String assignedElementId
) {
}
```

动态记录视图：

```java
public record ParticipantRecordView(
        String id,
        int recordOrder,
        Map<String, String> attributes
) {
}
```

删除奖项、主批次、重复批次、职级、部门、人员类型和标签专用字段。

- [ ] **Step 5: 实现主值聚合**

按 `recordOrder` 遍历每条记录和字段：

```java
values.computeIfAbsent(fieldName, ignored -> new ArrayList<>());
if (!values.get(fieldName).contains(value)) {
    values.get(fieldName).add(value);
}
```

每个字段列表的第一个值写入 `primaryAttributes`。字段定义只为扩展字段返回 `filterable=true`，不创建姓名和工号分组选项。

- [ ] **Step 6: 清理排座服务的人员冗余锁引用**

人员锁定只读取 `PlanItemEntity.locked`；删除 `participant.isLocked()` 判断。排座项仍使用原 `participantId`，不改变 `PlanItemTargetEntity` 绑定。

- [ ] **Step 7: 运行测试**

Run:

```powershell
mvn.cmd -Dtest=MeetingHelperIntegrationTests test
```

Expected: 动态聚合、单人新增、空座新增和原座位保持测试通过。

- [ ] **Step 8: 提交**

```powershell
git add backend/src/main/java/com/company/meetinghelper/participant backend/src/main/java/com/company/meetinghelper/workspace backend/src/main/java/com/company/meetinghelper/seating backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java
git commit -m "改造工作区动态人员字段聚合"
```

---

### Task 6: 让版本快照和导出支持动态记录

**Files:**
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/service/PlanVersionService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/export/service/ExportService.java`
- Test: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`

**Interfaces:**
- Consumes: Task 5 的 `WorkspaceResponse`
- Produces: 动态字段 Excel 表头和一人多行导出
- Preserves: 已发布版本只读和草稿覆盖能力

- [ ] **Step 1: 写版本和导出失败测试**

断言发布版本快照中的人员含两条动态记录，发布后修改草稿不影响旧快照。

导出断言：

```java
assertThat(headerValues).containsExactly("工号", "姓名", "批次", "奖项名称");
assertThat(rowsFor("a12345678")).hasSize(2);
assertThat(rowsFor("a12345678").get(0)).contains("第二批", "优秀项目奖");
assertThat(rowsFor("a12345678").get(1)).contains("第三批", "创新奖");
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn.cmd -Dtest=MeetingHelperIntegrationTests test
```

Expected: 导出仍使用固定职级、部门和奖项工作表。

- [ ] **Step 3: 修改人员 Excel 导出**

列顺序：

```text
工号、姓名、workspace.fieldDefinitions 顺序
```

有动态记录时一条记录导出一行；没有动态记录时导出一行工号和姓名。移除专用获奖名单工作表。

- [ ] **Step 4: 修改场馆和 PDF 摘要**

座位继续以姓名为主要内容；附加信息取字段顺序中第一个非空主值，不再读取人员类型和奖项。

- [ ] **Step 5: 调整版本覆盖草稿**

发布快照天然包含字段定义和动态记录。覆盖草稿时：

- 按工号匹配当前人员并保留其人员 ID；
- 对快照中存在的人员恢复姓名、出席状态和动态记录；
- 恢复字段定义顺序；
- 当前草稿中后来新增的人员继续保留；
- 排座关系继续按人员 ID 和座位元素 ID 恢复。

- [ ] **Step 6: 运行测试**

Run:

```powershell
mvn.cmd -Dtest=MeetingHelperIntegrationTests test
```

Expected: 版本不可变、覆盖草稿、动态导出和导出后重新导入测试通过。

- [ ] **Step 7: 提交**

```powershell
git add backend/src/main/java/com/company/meetinghelper/seating backend/src/main/java/com/company/meetinghelper/export backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java
git commit -m "支持动态人员记录版本与导出"
```

---

### Task 7: 实现后端会议用户空间隔离

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/common/user/CurrentUserProvider.java`
- Create: `backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingAccessService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/repository/MeetingRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/workspace/service/WorkspaceService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/service/ParticipantService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/importing/service/ImportService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/service/SeatingService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/service/PlanVersionService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/export/service/ExportService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/common/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`

**Interfaces:**
- Consumes: request header `X-User-Id`
- Produces: `CurrentUserProvider.requireUserId()`
- Produces: `MeetingAccessService.requireOwnedMeeting(String meetingId)`
- Produces: `MeetingAccessService.requireOwnedPlan(String planId)`

- [ ] **Step 1: 写空间隔离失败测试**

创建分别属于 `user-a` 和 `user-b` 的会议，然后断言：

```java
mockMvc.perform(get("/meetings").header("X-User-Id", "user-a"))
        .andExpect(jsonPath("$[*].name", everyItem(not("用户B会议"))));

mockMvc.perform(get("/meetings/{id}/workspace", userBMeetingId)
        .header("X-User-Id", "user-a"))
        .andExpect(status().isNotFound());
```

覆盖人员新增、导入、排座、版本、导出均不能跨用户访问。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn.cmd -Dtest=MeetingHelperIntegrationTests test
```

Expected: 当前后端返回全部会议且可通过 ID 访问其他用户会议。

- [ ] **Step 3: 实现当前用户提供器**

```java
@Component
public class CurrentUserProvider {
    private static final String HEADER = "X-User-Id";
    private final HttpServletRequest request;

    public String requireUserId() {
        var userId = request.getHeader(HEADER);
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "缺少当前用户信息");
        }
        return userId.trim();
    }
}
```

本地前端始终发送 `demo-secretary`，不在后端静默回退。

- [ ] **Step 4: 实现会议归属校验**

会议列表使用：

```java
findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(userId)
```

创建会议时显式设置 `createdById/Name`。所有会议子资源先解析到会议，再校验 `meeting.createdById == currentUserId`；不属于当前用户统一返回 404，避免泄露资源存在性。

- [ ] **Step 5: 保留乐观锁但不实现协同界面**

不向前端暴露 `rowVersion`。在全局异常处理器中将 `ObjectOptimisticLockingFailureException` 转换为 409：

```java
detail.setTitle("数据已更新");
detail.setDetail("数据已发生变化，请刷新后重试");
```

- [ ] **Step 6: 运行隔离测试**

Run:

```powershell
mvn.cmd -Dtest=MeetingHelperIntegrationTests test
```

Expected: 当前用户只能访问自己的会议；缺少用户头返回 401；跨用户访问返回 404。

- [ ] **Step 7: 提交**

```powershell
git add backend/src/main/java backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java
git commit -m "实现会议用户空间隔离"
```

---

### Task 8: 改造前端请求和单一导入流程

**Files:**
- Modify: `frontend/src/api/http.js`
- Modify: `frontend/src/api/meeting.js`
- Modify: `frontend/src/components/ImportDialog.vue`
- Test: `frontend/tests/api-path.test.js`
- Create: `frontend/tests/import-contract.test.js`

**Interfaces:**
- Consumes: Task 4 的单一导入 API
- Consumes: `currentUser.id`
- Produces: 所有请求头 `X-User-Id`

- [ ] **Step 1: 写 API 契约失败测试**

```js
assert.equal(importContract.templatePath, '/imports/template')
assert.equal(importContract.previewPath('m1'), '/meetings/m1/imports/preview')
assert.equal(importContract.commitPath('m1', 't1'), '/meetings/m1/imports/t1/commit')
```

断言请求拦截器设置：

```js
config.headers['X-User-Id'] = currentUser.id
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
npm.cmd test
```

Expected: 当前 API 仍要求模板编码，且未发送用户头。

- [ ] **Step 3: 修改 Axios 请求头和导入 API**

在 `http.js` 请求拦截器中追加用户 ID；`meeting.js` 删除模板列表和模板编码参数，只保留单一模板、预览和提交方法。

- [ ] **Step 4: 简化导入弹窗**

弹窗只包含：

- 下载人员模板；
- 选择 Excel；
- 上传并预览；
- 展示总行数、去重行数、人员数、记录数；
- 展示新增字段和已有字段；
- 展示阻断错误；
- 确认导入。

删除模板卡片、通用会议/颁奖会议选择、重复工号候选行单选逻辑。

- [ ] **Step 5: 运行前端测试和构建**

Run:

```powershell
npm.cmd test
npm.cmd run build
```

Expected: API 契约测试通过，生产构建成功。

- [ ] **Step 6: 提交**

```powershell
git add frontend/src/api frontend/src/components/ImportDialog.vue frontend/tests
git commit -m "统一前端人员导入流程"
```

---

### Task 9: 改造前端动态人员展示和新增

**Files:**
- Create: `frontend/src/utils/participantFields.js`
- Create: `frontend/tests/participant-fields.test.js`
- Modify: `frontend/src/components/AddParticipantDialog.vue`
- Modify: `frontend/src/components/ParticipantPanel.vue`
- Modify: `frontend/src/components/VenueCanvas.vue`
- Modify: `frontend/src/views/WorkbenchView.vue`
- Modify: `frontend/src/stores/workspace.js`

**Interfaces:**
- Produces: `primaryFieldValue(participant, fieldName)`
- Produces: `matchesParticipant(participant, keyword)`
- Produces: `groupParticipants(participants, fieldName)`
- Produces: `participantSummary(participant, fieldDefinitions, limit)`

- [ ] **Step 1: 写动态字段纯函数失败测试**

```js
test('搜索匹配任意动态记录值', () => {
  assert.equal(matchesParticipant(person, '创新奖'), true)
})

test('分组使用扩展字段主值且空值进入未填写', () => {
  const groups = groupParticipants([personWithoutGroup, person], '批次')
  assert.deepEqual(groups.map((group) => group.label), ['未填写', '第二批'])
})

test('分组选项不包含工号和姓名', () => {
  assert.deepEqual(groupableFields(fieldDefinitions).map((field) => field.label), ['批次', '奖项'])
})
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
npm.cmd test
```

Expected: `participantFields.js` 尚不存在。

- [ ] **Step 3: 实现动态字段纯函数**

搜索文本由以下内容拼接：

```js
[
  participant.employeeNo,
  participant.name,
  ...Object.values(participant.attributeValues).flat(),
]
```

分组使用 `participant.primaryAttributes[fieldName] || '未填写'`，卡片摘要按字段定义顺序取前两个非空主值。

- [ ] **Step 4: 改造单人新增**

弹窗固定输入工号和姓名，然后遍历 `fieldDefinitions` 生成扩展字段输入框：

```vue
<el-form-item
  v-for="field in fieldDefinitions"
  :key="field.code"
  :label="field.label"
>
  <el-input v-model="form.attributes[field.code]" />
</el-form-item>
```

删除职级、部门、人员类型和标签的写死控件。

- [ ] **Step 5: 改造人员列表**

- 分组选项只使用扩展字段；
- 搜索使用纯函数匹配全部扩展值；
- 卡片展示姓名、工号和最多两个动态摘要；
- 有多条记录时显示“共 N 条记录”；
- 保持分页、临时不出席、移出会议、拖回待排和只读模式。

- [ ] **Step 6: 改造座位内容**

座位显示姓名和首个非空动态字段摘要，删除 `participantType`、`primaryBatchName` 和 `awards[0]` 引用。人员拖放继续只携带 `participant.id`。

- [ ] **Step 7: 运行测试和构建**

Run:

```powershell
npm.cmd test
npm.cmd run build
```

Expected: 动态字段测试通过，生产构建成功。

- [ ] **Step 8: 提交**

```powershell
git add frontend/src frontend/tests
git commit -m "改造动态人员搜索分组与展示"
```

---

### Task 10: 更新演示数据、清理旧代码并重建 PostgreSQL

**Files:**
- Modify: `backend/src/main/java/com/company/meetinghelper/bootstrap/DemoDataInitializer.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/entity/ParticipantEntity.java`
- Modify: `DDL/meeting_helper.sql`
- Delete: `backend/src/main/java/com/company/meetinghelper/award/entity/AwardRecordEntity.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/award/repository/AwardRecordRepository.java`
- Modify: `CHANGELOG.md`
- Test: all backend and frontend tests

**Interfaces:**
- Consumes: all previous tasks
- Produces: 可直接运行的全新 PostgreSQL 数据库和演示会议

- [ ] **Step 1: 改造演示数据**

预置颁奖会议使用通用字段：

```text
部门、人员类型、职级、批次、奖项名称
```

每个人通过 `ParticipantRecordEntity` 保存一条或多条动态记录；至少包含一名第二批和第三批均有记录的人员，用于页面回归。

- [ ] **Step 2: 清理旧引用**

从 `ParticipantEntity` 删除已由动态记录替代的 `levelValue`、`department`、`participantType`、`tags`、`customAttributesJson` 等固定业务字段；从 DDL 删除对应列以及 `t_award_records` 建表和注释；删除颁奖专用实体、仓储。完成后运行：

Run:

```powershell
rg -n "AwardRecord|awardRepository|AWARD_CEREMONY|GENERAL_V1|participantType|primaryBatchName|repeatedBatches" backend/src/main/java frontend/src
```

Expected: 不再存在颁奖专用实体、模板策略和前端写死人员字段；如匹配到变更记录或设计文档，不删除文档历史。

- [ ] **Step 3: 运行后端全量测试**

Run:

```powershell
mvn.cmd test
```

Expected: `Tests run` 全部通过，`Failures: 0, Errors: 0`。

- [ ] **Step 4: 运行前端全量测试和构建**

Run:

```powershell
npm.cmd test
npm.cmd run build
```

Expected: 所有 Node 测试通过，Vite 构建成功。

- [ ] **Step 5: 重建本地 PostgreSQL**

先确认目标数据库名称严格为 `meeting_helper`，再执行：

```powershell
dropdb.exe -h localhost -p 5432 -U postgres --if-exists meeting_helper
createdb.exe -h localhost -p 5432 -U postgres -E UTF8 meeting_helper
psql.exe -h localhost -p 5432 -U postgres -d meeting_helper -v ON_ERROR_STOP=1 -f DDL/meeting_helper.sql
```

禁止对其他数据库执行删除。随后查询：

```sql
select table_name
from information_schema.tables
where table_schema = 'public'
order by table_name;
```

Expected: 存在 `t_meeting_participant_fields` 和 `t_participant_records`，不存在 `t_award_records`。

- [ ] **Step 6: 启动并做页面回归**

验证：

- 首页只显示当前用户会议；
- 单一模板下载只含工号和姓名；
- 两次导入 `{字段1}` 与 `{字段1,字段2}` 后人员只有一条动态记录；
- 第二批和第三批数据保留两条动态记录；
- 分组只有扩展字段；
- 已排人员补充字段后座位不变；
- 发布、只读查看和导出正常。

- [ ] **Step 7: 更新变更记录**

在 `CHANGELOG.md` 使用当前北京时间记录：

- 通用人员字段与多记录模型；
- 单一 Excel 模板和兼容合并算法；
- 动态搜索、分组、展示和导出；
- 用户会议空间隔离；
- 移除颁奖专用数据模型；
- PostgreSQL 数据库已使用完整 DDL 重建。

- [ ] **Step 8: 最终检查**

Run:

```powershell
git diff --check
git status --short
git diff --name-only -- DDL
```

确认未提交 `frontend/package-lock.json`、`frontend/dist`、`backend/target` 或数据库备份。

- [ ] **Step 9: 提交并推送**

```powershell
git add CHANGELOG.md backend frontend DDL
git commit -m "完成通用人员导入与动态字段改造"
git push origin main
```
