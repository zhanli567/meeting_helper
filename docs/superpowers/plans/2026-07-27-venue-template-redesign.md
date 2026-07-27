# 场馆模板模块重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有代码预置加自定义场馆重构为数据库维护的全局通用场馆模板，并完成通用布局编辑、会议快照、物理删除和无数据库外键改造。

**Architecture:** 场馆模板作为全局聚合根，普通信息存储在 `t_venue_templates`，完整布局存储在 `t_venue_elements`，布局保存采用“乐观锁校验后整批替换”。会议创建时复制场馆元素为 `t_meeting_elements` 独立快照，人员排座继续只绑定会议元素 ID。前端复用场馆列表的管理模式和选择模式，复用布局编辑器完成新建第二步与已有模板布局编辑。

**Tech Stack:** Java 21、Spring Boot 3.3.5、MyBatis-Plus 3.5.10.1、PostgreSQL、JUnit 5、Vue 3.5、Vue Router 4.5、Element Plus 2.8、Axios、Node.js 20 内置测试运行器、Vite 5.4。

## Global Constraints

- 后端使用 Java 21，不使用 `var`，Controller、Service、Repository 的 public 方法必须有 Javadoc。
- 数据库只使用 PostgreSQL，不引入 H2 或测试替代数据库。
- 控制层路径不使用 `/api` 前缀，业务请求只使用 GET 和 POST。
- 前端请求必须经过现有 Aurora 兼容封装，依靠 Cookie 传递登录态，不手工添加用户 ID 请求头。
- 后端统一返回 `{ code, data, msg }`。
- 根目录 `DDL/meeting_helper.sql` 只维护建表结构和表字段注释，不包含 `ALTER` 或初始化数据；所有表名使用 `t_` 前缀。
- 所有业务表取消软删除；数据库不建立外键；后端在事务中维护逻辑引用和子表清理顺序。
- 场馆模板全局可见、所有用户可操作，不按用户 ID 隔离。
- 场馆模板不维护业务版本，不包含代码预置和启动初始化数据。
- 地点是场馆模板唯一展示名称，必填并按去除首尾空格、忽略大小写后的值全局判重。
- 场馆编辑器不提供画布旋转或元素旋转。
- 前端继续使用 JavaScript，不增加 TypeScript、`vue-tsc`、`package-lock.json` 或公司仓库未验证的新依赖。
- 每个任务完成后使用中文提交说明；`CHANGELOG.md` 记录格式为 `2026-07-27 18:37  描述`。

---

## File Structure

### Backend

- `common/entity/AuditedEntity.java`：只维护主键、审计字段和 `rowVersion`。
- `common/repository/AbstractMyBatisRepository.java`：通用保存、查询和物理删除。
- `config/MyBatisPlusConfig.java`：MyBatis-Plus PostgreSQL 分页配置。
- `venue/entity/ElementKind.java`：仅定义 `SEAT` 和 `GENERIC`。
- `venue/entity/VenueTemplateEntity.java`：场馆固定信息、画布尺寸、座位数和聚合版本。
- `venue/entity/VenueElementEntity.java`：场馆元素类型、名称、几何和颜色。
- `venue/api/dto/*`：场馆创建、信息更新、布局更新、分页和详情契约。
- `venue/validation/VenueLayoutValidator.java`：画布边界、重叠、类型和颜色校验。
- `venue/repository/*`、`venue/mapper/*`：分页、判重、乐观锁更新和物理删除。
- `venue/service/VenueService.java`：场馆聚合业务与事务。
- `meeting/entity/MeetingElementEntity.java`：会议布局快照元素。
- `meeting/service/MeetingService.java`：从场馆模板复制会议快照。
- `workspace/*`、`seating/*`、`export/*`：适配精简后的会议元素契约。

### Frontend

- `src/api/venue.js`：场馆查询、创建、信息更新、布局更新和删除接口。
- `src/utils/venueModel.js`：场馆表单标准化、常用元素建议和请求数据构造。
- `src/utils/designerGeometry.js`：元素移动、拉伸、冲突检测、画布缩放边界和批量座位生成。
- `src/views/VenueLibraryView.vue`：管理/选择双模式表格列表。
- `src/views/VenueCreateView.vue`：场馆信息与布局两步创建。
- `src/views/VenueLayoutEditorView.vue`：已有模板全屏布局编辑。
- `src/components/VenueInfoForm.vue`：固定字段信息表单。
- `src/components/VenueDetailDrawer.vue`：信息详情。
- `src/components/VenueInfoDrawer.vue`：信息编辑。
- `src/components/VenueLayoutEditor.vue`：可复用全屏网格编辑器。
- `src/components/VenueElementPicker.vue`：框选后元素类型和名称选择。
- `src/components/VenueElementPanel.vue`：画布右侧属性面板。
- `src/components/VenuePreviewDialog.vue`：只读布局预览。
- `src/components/VenueCanvas.vue`：会议工作区适配新的会议元素结构。

---

### Task 1: 迁移为物理删除和无数据库外键

**Files:**
- Modify: `DDL/meeting_helper.sql`
- Modify: `backend/src/main/java/com/company/meetinghelper/common/entity/AuditedEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/common/repository/AbstractMyBatisRepository.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/mapper/MeetingMapper.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/mapper/ParticipantMapper.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/repository/MeetingRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/repository/MeetingElementRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/repository/ParticipantRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/repository/ParticipantRecordRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/repository/MeetingParticipantFieldRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/repository/SeatingPlanRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/repository/PlanItemRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/repository/PlanItemTargetRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/repository/PlanVersionRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/repository/VenueTemplateRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/repository/VenueElementRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/importing/service/ImportService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingAccessService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/service/ParticipantFieldRegistrationService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/service/ParticipantService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/service/PlanVersionService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/service/SeatingService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/service/VenueService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/workspace/service/WorkspaceService.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/DdlConventionTests.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MyBatisMappingTests.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`

**Interfaces:**
- Consumes: existing `AuditedEntity`, MyBatis-Plus mapper and PostgreSQL test initializer.
- Produces: audit entities without `deleted`; repository `delete` methods always issue physical DELETE; DDL without `deleted` columns or foreign keys.

- [ ] **Step 1: Write failing persistence convention tests**

Add assertions equivalent to:

```java
@Test
void ddlUsesPhysicalDeletionAndContainsNoForeignKeys() throws IOException {
    String ddl = Files.readString(ROOT.resolve("DDL/meeting_helper.sql")).toLowerCase(Locale.ROOT);
    assertFalse(Pattern.compile("\\bdeleted\\b").matcher(ddl).find());
    assertFalse(Pattern.compile("\\bforeign\\s+key\\b").matcher(ddl).find());
    assertFalse(Pattern.compile("\\breferences\\b").matcher(ddl).find());
}

@Test
void auditedEntityDoesNotEnableLogicalDeletion() {
    assertNull(findFieldAnnotation(AuditedEntity.class, "deleted", TableLogic.class));
    assertThrows(NoSuchFieldException.class, () -> AuditedEntity.class.getDeclaredField("deleted"));
}
```

Replace the soft-delete restore integration test with:

```java
@Test
void deletingParticipantPhysicallyRemovesIdentityRecordsAndAssignment() {
    participantService.delete(meetingId, participantId);
    assertTrue(participantRepository.findById(participantId).isEmpty());
    assertTrue(recordRepository.findAllByParticipantIdOrderByRecordOrderAsc(participantId).isEmpty());
    assertTrue(itemRepository.findByPlanIdAndParticipantIdAndItemType(planId, participantId, PlanItemType.PERSON).isEmpty());
}
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=DdlConventionTests,MyBatisMappingTests,MeetingHelperIntegrationTests#deletingParticipantPhysicallyRemovesIdentityRecordsAndAssignment test
```

Expected: FAIL because DDL and entities still contain logical deletion.

- [ ] **Step 3: Remove the logical-delete infrastructure**

Change `AuditedEntity` to:

```java
public abstract class AuditedEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    // existing created/updated audit fields remain
    @TableField("row_version")
    private long rowVersion;
}
```

Change `AbstractMyBatisRepository.save` so an existing entity always calls `mapper.updateById(entity)`. Keep `delete` and `deleteAll` as physical mapper deletes. Remove the `mybatis-plus.global-config.db-config.logic-delete-*` block from `application.yml`.

- [ ] **Step 4: Rename repository queries and remove deleted-record mapper SQL**

Use the following exact repository naming contract:

```text
findAllByCreatedByIdOrderByUpdatedAtDesc
findByIdAndCreatedById
findByIdForUpdate
existsByCreatedByIdAndNameIgnoreCase
findAllByMeetingIdOrderByNameAsc
findByIdAndMeetingId
findByMeetingIdAndEmployeeNoIgnoreCase
countByMeetingId
findAllByParticipantIdInOrderByParticipantIdAscRecordOrderAsc
findAllByParticipantIdOrderByRecordOrderAsc
findAllByMeetingIdOrderBySortOrderAsc
findByMeetingIdAndFieldNameIgnoreCase
findAllByMeetingIdOrderByGridRowAscGridColumnAsc
findByIdAndMeetingId
findAllByPlanIdOrderByCreatedAtAsc
findByPlanIdAndParticipantIdAndItemType
findAllByPlanIdOrderByVersionNoDesc
findFirstByPlanIdOrderByVersionNoDesc
existsByPlanIdAndVersionNameIgnoreCase
findFirstByMeetingIdOrderByCreatedAtAsc
findAllByPlanItemIdIn
findByMeetingElementId
```

Remove `ParticipantMapper.selectIncludingDeletedById`, `selectAllIncludingDeletedByMeetingId` and `updateIncludingDeleted`. Remove `and deleted = false` from `MeetingMapper.selectByIdForUpdate`.

- [ ] **Step 5: Implement explicit participant child cleanup**

In `ParticipantService.delete`, execute in this order inside the existing transaction:

```java
removeAssignment(meetingId, participantId);
recordRepository.deleteAll(recordRepository.findAllByParticipantIdOrderByRecordOrderAsc(participantId));
participantRepository.delete(participant);
```

Update `PlanVersionService` to restore seating and attributes only for participants still present in the current meeting. A published snapshot must not recreate a participant that was physically removed.

- [ ] **Step 6: Remove `deleted` columns and all foreign-key clauses from every DDL table**

Keep primary keys and unique constraints such as one participant per employee number and one active target per meeting element. Remove only:

```sql
deleted boolean not null,
constraint fk_t_venue_element_template foreign key (venue_template_id) references t_venue_templates(id),
```

Use `rg -n "foreign key|references|\\bdeleted\\b" DDL/meeting_helper.sql -i` to enumerate the complete removal set, then remove every matching constraint, column and deleted-column comment.

- [ ] **Step 7: Run the full backend suite**

Run:

```powershell
cd backend
.\mvnw.cmd test
```

Expected: all tests PASS on the dedicated PostgreSQL test database.

- [ ] **Step 8: Commit**

```powershell
git add DDL backend
git commit -m "统一业务数据物理删除与逻辑关联"
```

---

### Task 2: 重构后端通用场馆聚合与会议快照

场馆实体、接口 DTO、会议快照和工作区响应共享同一元素契约，无法在中间状态独立编译。因此本任务分为 2A、2B、2C 三个连续阶段，只在 2C 完成全量测试并提交一次。

#### Phase 2A: 建立通用场馆领域模型和布局校验

**Files:**
- Modify: `DDL/meeting_helper.sql`
- Delete: `backend/src/main/java/com/company/meetinghelper/venue/entity/ElementType.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/venue/entity/FrontDirection.java`
- Create: `backend/src/main/java/com/company/meetinghelper/venue/entity/ElementKind.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/entity/VenueTemplateEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/entity/VenueElementEntity.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/venue/preset/PresetVenueCatalog.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/venue/preset/PresetVenueDefinition.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/venue/preset/PresetVenueStore.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/venue/api/dto/ElementInput.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/venue/api/dto/request/CreateVenueRequest.java`
- Create: `backend/src/main/java/com/company/meetinghelper/venue/api/dto/request/UpdateVenueInfoRequest.java`
- Create: `backend/src/main/java/com/company/meetinghelper/venue/api/dto/request/UpdateVenueLayoutRequest.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/venue/api/dto/response/VenueDetail.java`
- Create: `backend/src/main/java/com/company/meetinghelper/venue/api/dto/response/VenueLayout.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/venue/api/dto/response/VenueSummary.java`
- Create: `backend/src/main/java/com/company/meetinghelper/venue/api/dto/response/VenuePage.java`
- Create: `backend/src/main/java/com/company/meetinghelper/venue/validation/VenueLayoutValidator.java`
- Create: `backend/src/test/java/com/company/meetinghelper/VenueLayoutValidatorTests.java`

**Interfaces:**
- Consumes: physical-delete persistence baseline from Task 1.
- Produces: `ElementKind`, venue request/response records and `VenueLayoutValidator.validate(int, int, List<ElementInput>)`.

- [ ] **Step 1: Write failing layout-validator tests**

Cover the complete geometry contract:

```java
@Test
void acceptsMultiCellSeatAndCountsItAsOneSeat() {
    List<ElementInput> elements = List.of(
            new ElementInput("SEAT", "双连座", 2, 3, 1, 2, "#ffffff", "#8fb4e8")
    );
    assertEquals(1, validator.validate(5, 5, elements).seatCount());
}

@Test
void rejectsOverlapAndOutOfBounds() {
    List<ElementInput> overlap = List.of(
            new ElementInput("GENERIC", "舞台", 1, 1, 2, 3, "#dbeafe", "#93c5fd"),
            new ElementInput("SEAT", "座位", 2, 3, 1, 1, "#ffffff", "#8fb4e8")
    );
    ApiException exception = assertThrows(ApiException.class, () -> validator.validate(5, 5, overlap));
    assertEquals("元素“座位”与其他元素发生重叠", exception.getMessage());
}
```

Also test minimum `5×5`, blank names, invalid kind and invalid `#RRGGBB` colors.

- [ ] **Step 2: Run the validator test and verify failure**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=VenueLayoutValidatorTests test
```

Expected: compilation FAIL because the new model does not exist.

- [ ] **Step 3: Implement the exact element and template model**

Create:

```java
public enum ElementKind {
    SEAT,
    GENERIC
}
```

`ElementInput` must be:

```java
public record ElementInput(
        @NotBlank String kind,
        @NotBlank @Size(max = 80) String name,
        @Min(1) int row,
        @Min(1) int column,
        @Min(1) int rowSpan,
        @Min(1) int columnSpan,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String fillColor,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String borderColor
) {
}
```

Use these exact request contracts:

```java
public record CreateVenueRequest(
        @NotBlank @Size(max = 200) String location,
        @Size(max = 120) String campus,
        @Size(max = 80) String mainScreenResolution,
        @Size(max = 80) String stageDimensions,
        @PositiveOrZero Integer manualCapacity,
        @Size(max = 500) String contactInfo,
        @Size(max = 1000) String bookingUrl,
        @Size(max = 2000) String meetingRoomFunctions,
        @Size(max = 2000) String servicesProvided,
        @Size(max = 2000) String description,
        @Size(max = 2000) String remarks,
        @Min(5) int gridRows,
        @Min(5) int gridColumns,
        @NotNull List<@Valid ElementInput> elements
) {
}

public record UpdateVenueInfoRequest(
        @NotBlank @Size(max = 200) String location,
        @Size(max = 120) String campus,
        @Size(max = 80) String mainScreenResolution,
        @Size(max = 80) String stageDimensions,
        @PositiveOrZero Integer manualCapacity,
        @Size(max = 500) String contactInfo,
        @Size(max = 1000) String bookingUrl,
        @Size(max = 2000) String meetingRoomFunctions,
        @Size(max = 2000) String servicesProvided,
        @Size(max = 2000) String description,
        @Size(max = 2000) String remarks,
        @PositiveOrZero long rowVersion
) {
}

public record UpdateVenueLayoutRequest(
        @Min(5) int gridRows,
        @Min(5) int gridColumns,
        @NotNull List<@Valid ElementInput> elements,
        @PositiveOrZero long rowVersion
) {
}
```

Use these exact response contracts:

```java
public record VenueSummary(
        String id,
        String location,
        String campus,
        Integer manualCapacity,
        int seatCount,
        boolean usable,
        String updatedByName,
        OffsetDateTime updatedAt,
        long rowVersion
) {
}

public record VenuePage(
        List<VenueSummary> records,
        long total,
        int pageNum,
        int pageSize
) {
}

public record VenueDetail(
        String id,
        String location,
        String campus,
        String mainScreenResolution,
        String stageDimensions,
        Integer manualCapacity,
        int seatCount,
        String contactInfo,
        String bookingUrl,
        String meetingRoomFunctions,
        String servicesProvided,
        String description,
        String remarks,
        int gridRows,
        int gridColumns,
        String createdByName,
        OffsetDateTime createdAt,
        String updatedByName,
        OffsetDateTime updatedAt,
        long rowVersion
) {
}

public record VenueLayout(
        String id,
        String location,
        Integer manualCapacity,
        int gridRows,
        int gridColumns,
        int seatCount,
        long rowVersion,
        List<ElementInput> elements
) {
}
```

`VenueTemplateEntity` fields must match the approved fixed-column model: `location`, `locationKey`, `campus`, screen/stage/capacity/contact/URL/functions/services/description/remarks, `seatCount`, `gridRows`, `gridColumns`. `VenueElementEntity` contains only template ID, kind, name, row, column, spans and colors.

- [ ] **Step 4: Replace the two venue DDL tables**

Define `t_venue_templates` with `location_key varchar(200) not null unique`, default-sized grid fields, `manual_capacity`, redundant `seat_count`, all fixed information columns, audit fields and `row_version`.

Define `t_venue_elements` with:

```sql
venue_template_id varchar(36) not null,
element_kind varchar(20) not null,
element_name varchar(80) not null,
start_row integer not null,
start_column integer not null,
row_span integer not null,
column_span integer not null,
fill_color varchar(20) not null,
border_color varchar(20) not null
```

Do not include version, preset, front direction, rotation, capacity, assignable, walkable, code, group or foreign-key columns. Update all table and field comments.

- [ ] **Step 5: Implement `VenueLayoutValidator`**

The validator must:

```java
public ValidationResult validate(int gridRows, int gridColumns, List<ElementInput> elements)
```

Normalize each element name and color, reject invalid bounds, and detect overlap by storing keys in a `HashSet<String>` using `row + ":" + column`. Return an immutable normalized element list and the number of `SEAT` elements.

- [ ] **Step 6: Continue directly to Phase 2B**

Do not commit the intermediate model because `VenueService` and `MeetingService` still reference the legacy DTO fields. Continue immediately with the repository and service replacement below.

#### Phase 2B: 实现场馆分页、保存、并发控制和删除接口

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/config/MyBatisPlusConfig.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/mapper/VenueTemplateMapper.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/repository/VenueTemplateRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/repository/VenueElementRepository.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/repository/MeetingRepository.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/venue/service/VenueService.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/venue/api/VenueController.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`

**Interfaces:**
- Consumes: venue entities, DTOs and validator from Task 2.
- Produces: approved GET/POST venue API and optimistic-lock behavior.

- [ ] **Step 1: Write failing venue API integration tests**

Add tests that create 12 templates and verify:

```java
mockMvc.perform(get("/venues")
        .param("keyword", "R10")
        .param("pageNum", "1")
        .param("pageSize", "10"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.records.length()").value(10))
    .andExpect(jsonPath("$.data.total").value(12));
```

Add cases for:

- location trimmed/case-insensitive duplicate returns 409;
- zero-seat venue saves but `usable` is false;
- stale `rowVersion` info/layout update returns 409;
- deleting a template removes template elements and sets matching meeting `venue_template_id` to null;
- no API response contains `preset`, `versionNo`, `frontDirection` or `rotation`.

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=MeetingHelperIntegrationTests#venueTemplatesArePagedAndGloballySearchable+venueTemplateRejectsStaleUpdates+deletingVenueKeepsMeetingSnapshot test
```

Expected: FAIL because the current list is unpaged and has preset behavior.

- [ ] **Step 3: Configure MyBatis-Plus PostgreSQL pagination**

Create:

```java
@Configuration
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
```

- [ ] **Step 4: Implement repositories**

`VenueTemplateRepository` must expose:

```java
public Page<VenueTemplateEntity> findPage(String keyword, String campus, int pageNum, int pageSize)
public boolean existsByLocationKey(String locationKey)
public boolean existsByLocationKeyAndIdNot(String locationKey, String id)
public VenueTemplateEntity updateInfoWithVersion(VenueTemplateEntity template, long expectedVersion)
public VenueTemplateEntity updateLayoutWithVersion(VenueTemplateEntity template, long expectedVersion)
```

Use mapper UPDATE statements with `where id = #{id} and row_version = #{expectedVersion}` and `row_version = row_version + 1`. If the update count is zero, throw the approved 409 conflict.

`VenueElementRepository` must expose `findAllByVenueTemplateIdOrderByStartRowAscStartColumnAsc` and `deleteAllByVenueTemplateId`.

`MeetingRepository` must expose:

```java
public void clearVenueTemplateId(String venueTemplateId)
```

implemented as `update t_meetings set venue_template_id = null where venue_template_id = #{venueTemplateId}`.

- [ ] **Step 5: Implement venue service transactions**

Public service contract:

```java
public VenuePage list(String keyword, String campus, int pageNum, int pageSize)
public VenueDetail get(String id)
public VenueLayout getLayout(String id)
public VenueDetail create(CreateVenueRequest request)
public VenueDetail updateInfo(String id, UpdateVenueInfoRequest request)
public VenueLayout updateLayout(String id, UpdateVenueLayoutRequest request)
public void delete(String id)
```

Normalize location as:

```java
String displayLocation = request.location().trim();
String locationKey = displayLocation.toLowerCase(Locale.ROOT);
```

Layout update must first win the template version update, then physically delete old elements and batch insert normalized elements in the same transaction.

- [ ] **Step 6: Implement controller routes**

Use:

```java
@GetMapping
public VenuePage list(@RequestParam(defaultValue = "") String keyword,
                      @RequestParam(defaultValue = "") String campus,
                      @RequestParam(defaultValue = "1") @Min(1) int pageNum,
                      @RequestParam(defaultValue = "10") @Min(1) int pageSize)

@GetMapping("/{id}")
public VenueDetail get(@PathVariable String id)

@GetMapping("/{id}/layout")
public VenueLayout getLayout(@PathVariable String id)

@PostMapping("/create")
public VenueDetail create(@Valid @RequestBody CreateVenueRequest request)

@PostMapping("/{id}/info/update")
public VenueDetail updateInfo(
        @PathVariable String id,
        @Valid @RequestBody UpdateVenueInfoRequest request
)

@PostMapping("/{id}/layout/update")
public VenueLayout updateLayout(
        @PathVariable String id,
        @Valid @RequestBody UpdateVenueLayoutRequest request
)

@PostMapping("/{id}/delete")
public void delete(@PathVariable String id)
```

Every public Controller, Service and Repository method must include Chinese Javadoc with `@param` and `@return` where applicable.

- [ ] **Step 7: Continue directly to Phase 2C**

Do not compile or commit yet because meeting/workspace code still consumes the legacy element fields. Continue with the snapshot adaptation below.

#### Phase 2C: 适配会议布局快照、排座和导出

**Files:**
- Modify: `DDL/meeting_helper.sql`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/entity/MeetingEntity.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/meeting/entity/MeetingElementEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/api/MeetingController.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/workspace/api/dto/response/WorkspaceResponse.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/workspace/service/WorkspaceService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/service/SeatingService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/export/service/ExportService.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`

**Interfaces:**
- Consumes: `VenueService.getLayout(String id)` and `ElementKind`.
- Produces: independent meeting snapshots with `{kind,name,row,column,rowSpan,columnSpan,fillColor,borderColor}`.

- [ ] **Step 1: Write failing meeting-snapshot and seating tests**

Add:

```java
@Test
void meetingCopiesVenueLayoutAndIgnoresLaterTemplateChanges() {
    VenueDetail venue = createVenueWithElements(List.of(
            seat("双连座", 2, 3, 1, 2),
            generic("主屏幕布", 1, 1, 1, 8)
    ));
    MeetingSummary meeting = meetingService.create(new CreateMeetingRequest("评审会", venue.id()));
    updateVenueLayout(venue.id(), List.of(generic("已修改", 1, 1, 1, 1)));

    WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
    assertEquals(2, workspace.layout().elements().size());
    assertEquals("双连座", workspace.layout().elements().get(0).name());
}

@Test
void genericElementCannotReceiveParticipantAndMultiCellSeatCanReceiveOnlyOne() {
    assertThrows(ApiException.class, () -> seatingService.assign(planId, assignment(participantId, genericId)));
    seatingService.assign(planId, assignment(participantId, multiCellSeatId));
    assertThrows(ApiException.class, () -> seatingService.assign(planId, assignment(otherParticipantId, multiCellSeatId)));
}
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=MeetingHelperIntegrationTests#meetingCopiesVenueLayoutAndIgnoresLaterTemplateChanges+genericElementCannotReceiveParticipantAndMultiCellSeatCanReceiveOnlyOne test
```

Expected: FAIL because the meeting snapshot still uses legacy element fields.

- [ ] **Step 3: Simplify meeting element persistence and workspace response**

`MeetingElementEntity` must contain:

```java
private String meetingId;
private String sourceElementId;
private ElementKind elementKind;
private String elementName;
private int startRow;
private int startColumn;
private int rowSpan;
private int columnSpan;
private String fillColor;
private String borderColor;
```

`WorkspaceResponse.ElementView` must expose the same values as:

```java
public record ElementView(
        String id,
        String kind,
        String name,
        int row,
        int column,
        int rowSpan,
        int columnSpan,
        String fillColor,
        String borderColor
) {
}
```

Remove venue-originated `cellSize` from template and meeting persistence. The frontend uses the shared display unit constant of 44 pixels.

- [ ] **Step 4: Copy snapshots and enforce seat kind**

In `MeetingService.create`, reject `venue.seatCount() == 0`, set `layoutName` to the venue location and copy all venue element fields. Store `sourceElementId` for traceability.

Expose meeting creation as:

```java
@PostMapping("/create-from-venue")
public MeetingSummary createFromVenue(@Valid @RequestBody CreateMeetingRequest request) {
    return meetingService.create(request);
}
```

Remove the previous `POST /meetings` creation mapping; meeting list remains `GET /meetings`.

In `SeatingService`, replace assignable/capacity checks with:

```java
if (targetElement.getElementKind() != ElementKind.SEAT) {
    throw new ApiException(HttpStatus.CONFLICT, "目标元素不是可排座座位");
}
```

Keep the unique `meeting_element_id` constraint in `t_plan_item_targets`.

- [ ] **Step 5: Adapt exports**

Use `elementName` for labels, `fillColor` for background and `ElementKind.SEAT` to decide whether a participant can be rendered. Remove rotation and legacy type-specific rendering branches that no longer have data.

- [ ] **Step 6: Run backend regression**

Run:

```powershell
cd backend
.\mvnw.cmd test
```

Expected: all tests PASS, including assignment swap, published version and Excel/PDF export tests.

- [ ] **Step 7: Commit the complete backend venue task**

```powershell
git add DDL backend
git commit -m "重构通用场馆模板与会议布局快照"
```

---

### Task 3: 建立前端场馆 API 和纯函数模型

**Files:**
- Create: `frontend/src/api/venue.js`
- Modify: `frontend/src/api/meeting.js`
- Create: `frontend/src/utils/venueModel.js`
- Modify: `frontend/src/utils/designerGeometry.js`
- Modify: `frontend/src/utils/venueCanvasMetrics.js`
- Create: `frontend/tests/venue-model.test.js`
- Modify: `frontend/tests/designer-geometry.test.js`
- Modify: `frontend/tests/venue-canvas-metrics.test.js`
- Modify: `frontend/tests/import-contract.test.js`

**Interfaces:**
- Consumes: venue HTTP contract from Task 3.
- Produces: `venueApi`, common venue form helpers and geometry helpers used by all venue UI tasks.

- [ ] **Step 1: Write failing API/model tests**

Test:

```js
test('场馆元素只生成通用字段', () => {
  assert.deepEqual(toElementPayload({
    kind: 'SEAT',
    name: '领导席',
    row: 2,
    column: 3,
    rowSpan: 1,
    columnSpan: 2,
    fillColor: '#ffffff',
    borderColor: '#8fb4e8',
    rotation: 90,
    code: 'A01',
  }), {
    kind: 'SEAT',
    name: '领导席',
    row: 2,
    column: 3,
    rowSpan: 1,
    columnSpan: 2,
    fillColor: '#ffffff',
    borderColor: '#8fb4e8',
  })
})

test('多格座位支持逐格生成和合并生成', () => {
  assert.equal(createSeatElements({ row: 2, column: 3, rowSpan: 2, columnSpan: 3 }, 'merge').length, 1)
  assert.equal(createSeatElements({ row: 2, column: 3, rowSpan: 2, columnSpan: 3 }, 'cells').length, 6)
})
```

Also test `canvasResizeConflict`, `rectsOverlap`, default `20×30`, minimum `5×5`, blank optional fields normalized to `null`, and common element suggestions.

- [ ] **Step 2: Run frontend tests and verify failure**

Run:

```powershell
cd frontend
npm test
```

Expected: FAIL because `venueModel.js` and the new geometry helpers do not exist.

- [ ] **Step 3: Implement `venueApi`**

Expose:

```js
export const venueApi = {
  list: (params) => unwrap(http.get('/venues', { params })),
  detail: (id) => unwrap(http.get(`/venues/${id}`)),
  layout: (id) => unwrap(http.get(`/venues/${id}/layout`)),
  create: (data) => unwrap(http.post('/venues/create', data)),
  updateInfo: (id, data) => unwrap(http.post(`/venues/${id}/info/update`, data)),
  updateLayout: (id, data) => unwrap(http.post(`/venues/${id}/layout/update`, data)),
  remove: (id) => unwrap(http.post(`/venues/${id}/delete`)),
}
```

Remove venue methods from `meetingApi`; keep meeting creation there.

- [ ] **Step 4: Implement venue model helpers**

Export:

```js
export const DEFAULT_CANVAS = Object.freeze({ rows: 20, columns: 30 })
export const MIN_CANVAS_SIZE = 5
export const ELEMENT_KINDS = Object.freeze({ SEAT: 'SEAT', GENERIC: 'GENERIC' })
const genericNames = [
  '门', '智慧屏', '投影', '操控间', '显示器', '入口', '中控室', '后门', '柱子',
  '电梯', '墙', '伴手礼', '奖杯放置', '荣誉墙', '装饰道具', '主展板', '楼梯口',
  '音箱', '前门', '桌子', '主屏幕布', '辅助屏幕布', '提词屏', '舞台', '走廊', '讲台',
]
export const COMMON_ELEMENT_SUGGESTIONS = Object.freeze([
  Object.freeze({
    name: '座位',
    kind: 'SEAT',
    fillColor: '#ffffff',
    borderColor: '#8fb4e8',
  }),
  ...genericNames.map((name) => Object.freeze({
    name,
    kind: 'GENERIC',
    fillColor: '#dbeafe',
    borderColor: '#93c5fd',
  })),
])

const blankToNull = (value) => {
  const normalized = String(value ?? '').trim()
  return normalized || null
}

export function normalizeVenueInfo(form) {
  return {
    location: String(form.location ?? '').trim(),
    campus: blankToNull(form.campus),
    mainScreenResolution: blankToNull(form.mainScreenResolution),
    stageDimensions: blankToNull(form.stageDimensions),
    manualCapacity:
      form.manualCapacity === '' || form.manualCapacity == null
        ? null
        : Number(form.manualCapacity),
    contactInfo: blankToNull(form.contactInfo),
    bookingUrl: blankToNull(form.bookingUrl),
    meetingRoomFunctions: blankToNull(form.meetingRoomFunctions),
    servicesProvided: blankToNull(form.servicesProvided),
    description: blankToNull(form.description),
    remarks: blankToNull(form.remarks),
  }
}

export function toElementPayload(element) {
  return {
    kind: element.kind,
    name: String(element.name).trim(),
    row: element.row,
    column: element.column,
    rowSpan: element.rowSpan,
    columnSpan: element.columnSpan,
    fillColor: element.fillColor,
    borderColor: element.borderColor,
  }
}

export function toCreateVenuePayload(info, layout) {
  return {
    ...normalizeVenueInfo(info),
    gridRows: layout.gridRows,
    gridColumns: layout.gridColumns,
    elements: layout.elements.map(toElementPayload),
  }
}
```

The common suggestion objects contain only `name`, `kind`, `fillColor` and `borderColor`; they never create backend element enums.

- [ ] **Step 5: Implement geometry helpers**

Add:

```js
export function rectsOverlap(left, right) {
  return !(
    left.row + left.rowSpan <= right.row ||
    right.row + right.rowSpan <= left.row ||
    left.column + left.columnSpan <= right.column ||
    right.column + right.columnSpan <= left.column
  )
}

export function canPlaceRect(elements, candidate, ignoredId) {
  return elements
    .filter((element) => element.id !== ignoredId)
    .every((element) => !rectsOverlap(element, candidate))
}

export function canvasResizeConflict(elements, rows, columns) {
  return elements.filter(
    (element) =>
      element.row + element.rowSpan - 1 > rows ||
      element.column + element.columnSpan - 1 > columns,
  )
}

export function createSeatElements(rect, mode, defaults = {}) {
  const base = {
    kind: 'SEAT',
    name: defaults.name || '座位',
    fillColor: defaults.fillColor || '#ffffff',
    borderColor: defaults.borderColor || '#8fb4e8',
  }
  if (mode === 'merge') return [{ ...base, ...rect }]
  const seats = []
  for (let row = rect.row; row < rect.row + rect.rowSpan; row += 1) {
    for (let column = rect.column; column < rect.column + rect.columnSpan; column += 1) {
      seats.push({ ...base, row, column, rowSpan: 1, columnSpan: 1 })
    }
  }
  return seats
}
```

Use 1-based row/column values and preserve existing `moveRect` and `resizeRect` behavior.

- [ ] **Step 6: Run frontend tests**

Run:

```powershell
cd frontend
npm test
```

Expected: all tests PASS.

- [ ] **Step 7: Commit**

```powershell
git add frontend
git commit -m "建立前端场馆数据与画布几何模型"
```

---

### Task 4: 重构场馆列表、首页入口和信息管理

**Files:**
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/views/HomeView.vue`
- Replace: `frontend/src/views/VenueLibraryView.vue`
- Create: `frontend/src/views/VenueCreateView.vue`
- Create: `frontend/src/components/VenueInfoForm.vue`
- Create: `frontend/src/components/VenueDetailDrawer.vue`
- Create: `frontend/src/components/VenueInfoDrawer.vue`
- Create: `frontend/tests/venue-library-contract.test.js`

**Interfaces:**
- Consumes: `venueApi` and model helpers from Task 5.
- Produces: `/venues` management mode, `/venues/select` selection mode and the first step of venue creation.

- [ ] **Step 1: Write failing view contract tests**

Verify source contracts:

```js
test('场馆列表使用表格、分页和双模式', async () => {
  const source = await readSource('src/views/VenueLibraryView.vue')
  assert.match(source, /<el-table/)
  assert.match(source, /<el-pagination/)
  assert.match(source, /route\.name === 'venue-select'/)
  assert.match(source, /按园区分组/)
  assert.doesNotMatch(source, /系统预置|versionNo|preset/)
})

test('首页主入口和管理入口语义分离', async () => {
  const source = await readSource('src/views/HomeView.vue')
  assert.match(source, /开始排座/)
  assert.match(source, /router\.push\('\\/venues\\/select'\)/)
  assert.match(source, /场馆模板/)
  assert.match(source, /router\.push\('\\/venues'\)/)
})
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
cd frontend
npm test
```

Expected: FAIL because the current library uses cards and one route mode.

- [ ] **Step 3: Add route structure**

Configure:

```js
{ path: '/venues', name: 'venue-manage', component: () => import('@/views/VenueLibraryView.vue') }
{ path: '/venues/select', name: 'venue-select', component: () => import('@/views/VenueLibraryView.vue') }
{ path: '/venues/new', name: 'venue-new', component: () => import('@/views/VenueCreateView.vue') }
{ path: '/venues/:venueId/layout/edit', name: 'venue-layout-edit', component: () => import('@/views/VenueLayoutEditorView.vue') }
```

- [ ] **Step 4: Build venue information components**

`VenueInfoForm` contains fixed fields in three groups and emits a normalized form. Only location is required. Room functions, services, description and remarks use textarea inputs.

`VenueDetailDrawer` is read-only. `VenueInfoDrawer` submits:

```js
await venueApi.updateInfo(venue.id, {
  ...normalizeVenueInfo(form),
  rowVersion: venue.rowVersion,
})
```

On success, replace detail and refresh the current list page.

- [ ] **Step 5: Replace cards with a paginated table**

The table columns are location, campus, manual capacity, seat count, updater, update time and actions. Search is debounced by 300 ms. The page size options are `[10, 20, 50]`.

When grouping is enabled, insert a non-selectable group header before the first record of each campus on the current page; blank campus displays “未填写园区”.

Disable “使用模板” when `seatCount === 0` and show “布局未完成”.

- [ ] **Step 6: Implement create step one**

`VenueCreateView` holds:

```js
const step = ref('info')
const info = reactive(emptyVenueInfo())
const layout = reactive({ gridRows: 20, gridColumns: 30, elements: [] })
```

Continue from information to layout without a backend write. Back retains state. Register `onBeforeRouteLeave` and browser `beforeunload` while unsaved.

- [ ] **Step 7: Run frontend tests and build**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: tests and Vite build PASS.

- [ ] **Step 8: Commit**

```powershell
git add frontend
git commit -m "重构场馆模板列表与信息管理流程"
```

---

### Task 5: 实现可复用场馆布局编辑器

**Files:**
- Create: `frontend/src/views/VenueLayoutEditorView.vue`
- Create: `frontend/src/components/VenueLayoutEditor.vue`
- Create: `frontend/src/components/VenueElementPicker.vue`
- Create: `frontend/src/components/VenueElementPanel.vue`
- Modify: `frontend/src/views/VenueCreateView.vue`
- Delete: `frontend/src/views/VenueDesignerView.vue`
- Modify: `frontend/tests/designer-geometry.test.js`
- Create: `frontend/tests/venue-designer-contract.test.js`

**Interfaces:**
- Consumes: geometry/model utilities and `venueApi`.
- Produces: new-template layout step and existing-template full-screen layout editor.

- [ ] **Step 1: Write failing editor contract tests**

Verify:

```js
test('编辑器支持画布拉伸、属性侧栏和无旋转交互', async () => {
  const source = await readSource('src/components/VenueLayoutEditor.vue')
  assert.match(source, /canvas-resize-east/)
  assert.match(source, /canvas-resize-south/)
  assert.match(source, /canvas-resize-corner/)
  assert.match(source, /<VenueElementPanel/)
  assert.match(source, /undo/)
  assert.match(source, /redo/)
  assert.doesNotMatch(source, /rotate|旋转/)
})

test('多格座位要求选择逐格或合并', async () => {
  const source = await readSource('src/components/VenueElementPicker.vue')
  assert.match(source, /逐格生成座位/)
  assert.match(source, /合并为一个座位/)
})
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
cd frontend
npm test
```

Expected: FAIL because the new editor components do not exist.

- [ ] **Step 3: Implement canvas viewport and history**

Use:

- mouse wheel zoom, range 25%–250%;
- right mouse button pan;
- left mouse selection, element movement and resize;
- `structuredClone` snapshots for a maximum of 50 undo states;
- initial fit and center after mount;
- no page-level scrollbar.

Elements render with a shared 44-pixel base cell and a 4-pixel visual inset for seats so adjacent seats remain distinguishable.

- [ ] **Step 4: Implement drag-to-resize canvas**

Add three hit areas with classes required by the contract test. Convert pointer movement to rows/columns. Before shrinking call:

```js
const conflicts = canvasResizeConflict(elements.value, proposedRows, proposedColumns)
if (conflicts.length) {
  conflictElementIds.value = conflicts.map((element) => element.id)
  return
}
```

Never delete or move elements automatically.

- [ ] **Step 5: Implement element picker and side panel**

After selection, keep a striped selection preview visible and place the picker beside it without covering it. Selecting a common item creates a `GENERIC` element except “座位”, which creates `SEAT`.

For multi-cell seats, require the user to choose `cells` or `merge`. The element property panel edits only kind, name, fill color and border color. Clicking outside closes uncommitted picker/panel state.

- [ ] **Step 6: Implement element move, resize and conflicts**

Show the eight resize handles. During drag/resize call `canPlaceRect`; conflicting candidates render red and are not committed. Double-click deletes after the current undo snapshot is recorded.

- [ ] **Step 7: Wire create and edit saves**

New-template final save:

```js
await venueApi.create(toCreateVenuePayload(info, layout))
```

Existing layout save:

```js
await venueApi.updateLayout(route.params.venueId, {
  gridRows,
  gridColumns,
  elements: elements.map(toElementPayload),
  rowVersion,
})
```

Handle 409 by preserving local layout and showing the server conflict message.

- [ ] **Step 8: Run tests and build**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: all tests and build PASS.

- [ ] **Step 9: Commit**

```powershell
git add frontend
git commit -m "实现通用场馆模板布局编辑器"
```

---

### Task 6: 适配预览、会议工作区和创建会议链路

**Files:**
- Modify: `frontend/src/components/VenuePreviewDialog.vue`
- Modify: `frontend/src/components/VenueCanvas.vue`
- Modify: `frontend/src/views/WorkbenchView.vue`
- Modify: `frontend/src/utils/venueCanvasMetrics.js`
- Modify: `frontend/tests/venue-canvas-metrics.test.js`
- Modify: `frontend/tests/participant-component-contract.test.js`
- Modify: `frontend/tests/nexus-ui-style.test.js`

**Interfaces:**
- Consumes: simplified workspace element response from Task 4 and venue list selection from Task 6.
- Produces: readable venue preview and unchanged manual seating behavior.

- [ ] **Step 1: Write failing preview/workbench tests**

Add:

```js
test('预览和工作区读取通用元素字段且不渲染旋转', async () => {
  const preview = await readSource('src/components/VenuePreviewDialog.vue')
  const canvas = await readSource('src/components/VenueCanvas.vue')
  assert.match(preview, /element\.name/)
  assert.match(preview, /element\.fillColor/)
  assert.match(canvas, /element\.kind === 'SEAT'/)
  assert.doesNotMatch(preview, /element\.rotation/)
  assert.doesNotMatch(canvas, /element\.assignable/)
})
```

- [ ] **Step 2: Run frontend tests and verify failure**

Run:

```powershell
cd frontend
npm test
```

Expected: FAIL because components still use legacy `type`, `label`, `backgroundColor`, `assignable` and `rotation`.

- [ ] **Step 3: Adapt venue preview**

Load `venueApi.layout(id)`, title with the location from the layout response, render `name`, `fillColor` and `borderColor`, and derive seat styling from `kind === 'SEAT'`. Keep wheel zoom and pointer pan. Do not render type-specific enums or rotation transforms.

- [ ] **Step 4: Adapt meeting canvas**

Use:

```js
const isSeat = (element) => element.kind === 'SEAT'
```

Only seat elements accept participant drops and show participant information. Generic elements display their configured names. Keep existing double-click unassign, seat swap, pending-list drop and draft/read-only controls.

- [ ] **Step 5: Verify create-meeting selection**

From `/venues/select`, clicking “使用模板” opens the meeting-name dialog. On success use the existing meeting API, remember the meeting ID and navigate to `/workbench/{meetingId}`. Zero-seat rows keep the button disabled.

Update `meetingApi.createMeeting` to POST `/meetings/create-from-venue`.

- [ ] **Step 6: Run frontend regression**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: all tests and build PASS.

- [ ] **Step 7: Commit**

```powershell
git add frontend
git commit -m "适配场馆预览与会议排座画布"
```

---

### Task 7: 完整验证、重建本地数据库和记录变更

**Files:**
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: complete backend and frontend implementation from Tasks 1–6.
- Produces: clean PostgreSQL schema, verified build and timestamped change record.

- [ ] **Step 1: Run static contract scans**

Run:

```powershell
rg -n "PresetVenue|preset|FrontDirection|front_direction|rotation|@TableLogic|DeletedFalse|setDeleted|isDeleted" backend/src/main frontend/src DDL
rg -n "foreign key|references|\\bdeleted\\b" DDL/meeting_helper.sql -i
rg -n "@(PutMapping|DeleteMapping|PatchMapping)|RequestMapping\\(\"/api" backend/src/main/java
```

Expected: no forbidden production-code or DDL matches. Seating plan `versionNo` and published meeting versions are valid and must not be removed.

- [ ] **Step 2: Run backend verification**

Run:

```powershell
cd backend
.\mvnw.cmd clean test
.\mvnw.cmd -DskipTests package
```

Expected: both commands exit 0.

- [ ] **Step 3: Run frontend verification**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: both commands exit 0 and no `package-lock.json` is created or staged.

- [ ] **Step 4: Rebuild the local PostgreSQL database from DDL**

This is intentionally destructive for the local demonstration database:

```powershell
$env:PGPASSWORD = '123456'
psql -U postgres -d postgres -c "drop database if exists meeting_helper with (force)"
psql -U postgres -d postgres -c "create database meeting_helper"
psql -U postgres -d meeting_helper -f DDL/meeting_helper.sql
Remove-Item Env:PGPASSWORD
```

If the configured local password is `12345678`, retry only the password value. Do not insert initialization data.

- [ ] **Step 5: Start both applications and perform browser smoke checks**

Verify:

1. home opens without page-level scrolling;
2. `/venues` is initially empty after database rebuild;
3. create a venue with information and a layout;
4. preview it, edit information and edit layout;
5. search and paginate the list;
6. use the template to create a meeting;
7. add a participant and assign to a multi-cell seat;
8. modify and delete the original template;
9. reopen the meeting and confirm its snapshot still renders and seats remain usable.

- [ ] **Step 6: Add timestamped changelog entries**

Read the completion timestamp first:

```powershell
$changeTime = Get-Date -Format 'yyyy-MM-dd HH:mm'
$changeTime
```

Use that exact printed value for all three new `[未发布]` entries:

```text
重构场馆模板为全局数据库资源，新增固定场馆信息、分页搜索、园区分组和信息管理。
重建通用场馆布局编辑器，支持画布拖拽调整、多格座位、元素移动缩放和布局快照。
全部业务表切换为物理删除并移除数据库外键，使用 PostgreSQL DDL 重建本地数据库。
```

- [ ] **Step 7: Commit verification fixes and changelog**

```powershell
git add CHANGELOG.md DDL backend frontend
git commit -m "完成场馆模板重构验证与数据库重建"
```

- [ ] **Step 8: Review branch history and merge**

Confirm every task commit is Chinese, the worktree is clean, merge the feature branch into `main`, delete the merged feature branch and push `main`.

```powershell
git status --short
git log --oneline --decorate -12
git switch main
git merge --no-ff codex/venue-template-redesign -m "合并场馆模板模块重构"
git branch -d codex/venue-template-redesign
git push origin main
```
