# 公司技术栈与接口规范对齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变场馆、通用人员、人工排座和版本管理业务语义的前提下，将系统完整迁移到公司采用的 Spring Boot 3.3.5、MyBatis-Plus、PostgreSQL、Aurora 和 GET/POST 接口规范。

**Architecture:** 后端按业务域保留 Controller、Service、Repository 分层，并在每个持久化域增加 MyBatis-Plus Mapper；Repository 负责组合 Mapper 查询条件，Service 只处理业务与事务。迁移采用 PostgreSQL 回归测试先行、按领域逐层替换、最后移除 JPA/H2 的顺序，前端则通过 Aurora 适配层和统一响应解包层隔离公司内网实现与外网 Axios 回退实现。

**Tech Stack:** Java 21、Spring Boot 3.3.5、MyBatis-Plus 3.5.10.1、PostgreSQL、JUnit 5、MockMvc、Vue 3 JavaScript、Aurora 兼容层、Axios 回退、Node Test Runner、Vite。

## Global Constraints

- Java 源码和测试源码中的局部变量全部使用显式类型，不使用 `var`。
- 后端只使用 PostgreSQL；业务库为 `meeting_helper`，自动化测试库为 `meeting_helper_test`。
- 测试初始化只能清理 `meeting_helper_test`，并且必须在清理前校验当前数据库名称。
- 数据库结构唯一来源为根目录 `DDL/meeting_helper.sql`；该文件只允许出现 `CREATE TABLE`、`COMMENT ON TABLE`、`COMMENT ON COLUMN`。
- 所有数据库表名继续使用 `t_` 前缀，不在本次技术迁移中新增业务表或修改现有业务字段。
- Controller、Service、Repository 的 public 方法必须保留或补充 Javadoc。
- 前端不发送 `X-User-Id`，后端不解析 Cookie、不新增身份拦截器；公司框架接入后负责写入 `CurrentUserHolder`。
- `CurrentUserHolder` 没有用户时，用户 ID 和用户名均使用空字符串，当前演示阶段共享同一个匿名空间。
- JSON 接口统一返回 `{code,data,msg}`；Excel 模板、Excel 导出、PDF 导出继续返回原始二进制流。
- 前后端 HTTP 请求只允许 GET 和 POST，不允许 PUT、PATCH、DELETE。
- 前端业务组件只调用领域 API，不得直接访问 Aurora 或 Axios。
- 删除 `DemoDataInitializer` 以及测试中对自动演示会议的依赖；保留代码化 `PresetVenueCatalog`。
- 不提交 `frontend/package-lock.json`、构建产物、测试报告或临时数据库文件。
- 每项功能变更在 `CHANGELOG.md` 中使用 `YYYY-MM-DD HH:mm  ` 格式记录，Git 提交信息使用中文。

---

### Task 1: 建立 PostgreSQL 专用自动化测试基线

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/test/resources/application.yml`
- Create: `backend/src/test/java/com/company/meetinghelper/support/PostgreSqlTestDatabaseInitializer.java`
- Create: `backend/src/test/java/com/company/meetinghelper/PostgreSqlOnlyTests.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperApplicationTests.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: 根目录 `DDL/meeting_helper.sql`；本机 PostgreSQL 管理连接 `jdbc:postgresql://localhost:5432/postgres`。
- Produces: `PostgreSqlTestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>`；所有 Spring 集成测试通过 `@ContextConfiguration(initializers = PostgreSqlTestDatabaseInitializer.class)` 使用 `meeting_helper_test`。

- [ ] **Step 1: 写 PostgreSQL 专用配置的失败测试**

在 `PostgreSqlOnlyTests` 中读取 POM 和测试 YAML，断言不存在 H2，测试 URL 精确指向 `meeting_helper_test`：

```java
@Test
void automatedTestsUsePostgreSqlOnly() throws IOException {
    String pom = Files.readString(Path.of("pom.xml"));
    String yaml = Files.readString(Path.of("src/test/resources/application.yml"));

    assertThat(pom).doesNotContain("com.h2database");
    assertThat(yaml).contains("jdbc:postgresql://localhost:5432/meeting_helper_test");
    assertThat(yaml).doesNotContain("jdbc:h2:");
}
```

- [ ] **Step 2: 运行测试并确认当前 H2 配置导致失败**

Run: `cd backend && mvn -Dtest=PostgreSqlOnlyTests test`

Expected: FAIL，输出包含 `com.h2database` 或 `jdbc:h2:mem:meeting-helper-test`。

- [ ] **Step 3: 实现测试库安全初始化器**

`PostgreSqlTestDatabaseInitializer` 必须：

1. 先连接 `postgres` 管理库，使用参数化查询判断 `meeting_helper_test` 是否存在，不存在时执行 `create database meeting_helper_test`。
2. 连接 `meeting_helper_test` 后执行 `select current_database()`，结果不是 `meeting_helper_test` 时立即抛出 `IllegalStateException`。
3. 仅在校验成功后执行 `drop schema public cascade` 和 `create schema public`。
4. 使用 Spring `ScriptUtils.executeSqlScript` 执行 `../../DDL/meeting_helper.sql`。
5. 将测试数据源 URL、用户名、密码写入 `TestPropertyValues`，供 Spring 上下文使用。

初始化器的公开入口固定为：

```java
public final class PostgreSqlTestDatabaseInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // 创建并验证专用测试库，重建 public schema，执行唯一 DDL。
    }
}
```

- [ ] **Step 4: 调整依赖和测试 YAML**

将 Spring Boot Parent 改为 `3.3.5`，Web Starter 改为 `spring-boot-starter-web`，暂时保留 JPA 以便后续按域迁移；删除 H2，PostgreSQL JDBC 改为测试也可用的默认 compile/runtime 依赖。测试 YAML 只包含：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/meeting_helper_test
    username: postgres
    password: 123456
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
```

- [ ] **Step 5: 将 Spring 集成测试接入初始化器并运行 PostgreSQL 基线**

Run: `cd backend && mvn test`

Expected: 现有后端测试全部通过；日志中的 JDBC URL 指向 `meeting_helper_test`，没有 H2 驱动。

- [ ] **Step 6: 记录并提交 PostgreSQL 测试基线**

```powershell
git add backend/pom.xml backend/src/test/resources/application.yml backend/src/test/java CHANGELOG.md
git commit -m "建立PostgreSQL自动化测试基线"
```

---

### Task 2: 增加 MyBatis-Plus 基础设施和实体映射

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application.yml`
- Modify: `backend/src/main/java/com/company/meetinghelper/MeetingHelperApplication.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/common/entity/AuditedEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/entity/VenueTemplateEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/entity/VenueElementEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/entity/MeetingEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/entity/MeetingElementEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/entity/ParticipantEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/entity/MeetingParticipantFieldEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/entity/ParticipantRecordEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/entity/SeatingPlanEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/entity/PlanItemEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/entity/PlanItemTargetEntity.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/entity/PlanVersionEntity.java`
- Create: `backend/src/test/java/com/company/meetinghelper/MyBatisMappingTests.java`

**Interfaces:**
- Consumes: 11 张 `t_` 表及其下划线列名。
- Produces: 每个实体通过 `@TableName`、`@TableId`、`@TableField`、`@TableLogic` 显式映射；应用扫描 `com.company.meetinghelper.**.mapper`。

- [ ] **Step 1: 写实体与表名映射失败测试**

测试反射读取 11 个实体的 `@TableName`，并校验 `AuditedEntity.deleted` 存在 `@TableLogic`：

```java
@Test
void everyPersistentEntityHasMyBatisTableMapping() {
    assertThat(VenueTemplateEntity.class.getAnnotation(TableName.class).value())
            .isEqualTo("t_venue_templates");
    assertThat(ReflectionUtils.findField(AuditedEntity.class, "deleted")
            .getAnnotation(TableLogic.class)).isNotNull();
}
```

- [ ] **Step 2: 运行映射测试并确认缺少 MyBatis 注解**

Run: `cd backend && mvn -Dtest=MyBatisMappingTests test`

Expected: FAIL，原因是 `TableName`/`TableLogic` 类型或注解不存在。

- [ ] **Step 3: 引入 MyBatis-Plus 并配置扫描**

在 POM 中加入：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.10.1</version>
</dependency>
```

在应用入口增加 `@MapperScan("com.company.meetinghelper.**.mapper")`，在两个 YAML 中加入：

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: true
      logic-not-delete-value: false
```

- [ ] **Step 4: 为全部实体增加 MyBatis 映射**

过渡阶段保留 JPA 注解，直至 Mapper/Repository 全部迁移。基础字段使用以下固定映射：

```java
@TableId(value = "id", type = IdType.INPUT)
private String id;

@TableField("created_by_id")
private String createdById;

@TableLogic
@TableField("deleted")
private boolean deleted;
```

枚举字段增加 `@EnumValue` 或使用 `EnumTypeHandler`，JSON 字符串字段继续以 `String` 保存，不改变 DDL。

- [ ] **Step 5: 运行映射测试和全部回归**

Run: `cd backend && mvn test`

Expected: `MyBatisMappingTests` 和既有测试全部 PASS，JPA 过渡运行仍可用。

- [ ] **Step 6: 提交 MyBatis-Plus 基础设施**

```powershell
git add backend
git commit -m "增加MyBatisPlus实体映射基础"
```

---

### Task 3: 迁移场馆与会议 Mapper/Repository

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/venue/mapper/VenueTemplateMapper.java`
- Create: `backend/src/main/java/com/company/meetinghelper/venue/mapper/VenueElementMapper.java`
- Create: `backend/src/main/java/com/company/meetinghelper/meeting/mapper/MeetingMapper.java`
- Create: `backend/src/main/java/com/company/meetinghelper/meeting/mapper/MeetingElementMapper.java`
- Create: `backend/src/main/resources/mapper/meeting/MeetingMapper.xml`
- Replace: `backend/src/main/java/com/company/meetinghelper/venue/repository/VenueTemplateRepository.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/venue/repository/VenueElementRepository.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/meeting/repository/MeetingRepository.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/meeting/repository/MeetingElementRepository.java`
- Create: `backend/src/test/java/com/company/meetinghelper/repository/VenueMeetingRepositoryTests.java`

**Interfaces:**
- Consumes: `BaseMapper<T>`；`MeetingMapper.selectByIdForUpdate(@Param("meetingId") String meetingId)`。
- Produces: 保持现有 Service 调用签名的具体 `@Repository` 类，包括 `save`、`saveAll`、`findById` 和既有领域查询方法。

- [ ] **Step 1: 写场馆与会议 Repository 的 PostgreSQL 失败测试**

覆盖：自定义场馆名称忽略大小写判重、元素按行列排序、匿名用户会议隔离、会议行锁查询：

```java
@Test
void meetingRepositoryLocksAndReturnsOnlyActiveMeeting() {
    MeetingEntity meeting = meetingRepository.save(meeting("会议A", ""));
    Optional<MeetingEntity> locked =
            meetingRepository.findByIdAndDeletedFalseForUpdate(meeting.getId());
    assertThat(locked).isPresent();
}
```

- [ ] **Step 2: 先创建四个 Mapper**

每个 Mapper 只继承 `BaseMapper<Entity>`。`MeetingMapper` 额外声明：

```java
Optional<MeetingEntity> selectByIdForUpdate(@Param("meetingId") String meetingId);
```

对应 XML 使用真实 PostgreSQL 行锁：

```xml
<select id="selectByIdForUpdate"
        resultType="com.company.meetinghelper.meeting.entity.MeetingEntity">
    select *
    from t_meetings
    where id = #{meetingId}
      and deleted = false
    for update
</select>
```

- [ ] **Step 3: 将四个 JPA Repository 替换为具体类**

Repository 使用 `LambdaQueryWrapper` 表达排序、大小写判重和逻辑删除条件。公开方法保持现有名称，示例：

```java
public Optional<MeetingEntity> findByIdAndDeletedFalseForUpdate(String meetingId) {
    return meetingMapper.selectByIdForUpdate(meetingId);
}

public boolean existsByCreatedByIdAndNameIgnoreCaseAndDeletedFalse(
        String createdById, String name) {
    Long count = meetingMapper.selectCount(Wrappers.<MeetingEntity>lambdaQuery()
            .eq(MeetingEntity::getCreatedById, createdById)
            .apply("lower(name) = lower({0})", name)
            .eq(MeetingEntity::isDeleted, false));
    return count > 0;
}
```

`saveAll` 使用 `MybatisBatch` 或逐条 `insert/updateById` 的明确批处理方法；不得把 Mapper 暴露给 Service。

- [ ] **Step 4: 运行 Repository 测试和场馆/会议集成测试**

Run: `cd backend && mvn -Dtest=VenueMeetingRepositoryTests,MeetingHelperIntegrationTests test`

Expected: 场馆创建、更新、软删除、会议创建、会议工作区和并发锁测试 PASS。

- [ ] **Step 5: 提交场馆和会议持久层迁移**

```powershell
git add backend
git commit -m "迁移场馆与会议MyBatis持久层"
```

---

### Task 4: 迁移通用人员 Mapper/Repository

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/participant/mapper/ParticipantMapper.java`
- Create: `backend/src/main/java/com/company/meetinghelper/participant/mapper/MeetingParticipantFieldMapper.java`
- Create: `backend/src/main/java/com/company/meetinghelper/participant/mapper/ParticipantRecordMapper.java`
- Create: `backend/src/main/resources/mapper/participant/ParticipantMapper.xml`
- Replace: `backend/src/main/java/com/company/meetinghelper/participant/repository/ParticipantRepository.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/participant/repository/MeetingParticipantFieldRepository.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/participant/repository/ParticipantRecordRepository.java`
- Create: `backend/src/test/java/com/company/meetinghelper/repository/ParticipantRepositoryTests.java`

**Interfaces:**
- Consumes: MyBatis 逻辑删除；`ParticipantMapper.selectAllByMeetingIdIncludingDeleted(@Param("meetingId") String meetingId)`。
- Produces: 会议人员、会议动态字段、人员多条动态记录的原有 Repository 接口行为。

- [ ] **Step 1: 写包含已删除人员和多记录排序的失败测试**

测试同一工号软删除后仍可通过 including-deleted 查询恢复，普通查询不返回已删除人员，动态记录按 `recordOrder` 排序：

```java
@Test
void includingDeletedQuerySupportsVersionRestore() {
    ParticipantEntity participant = participantRepository.save(participant("a12345678"));
    participant.setDeleted(true);
    participantRepository.save(participant);

    assertThat(participantRepository
            .findAllByMeetingIdAndDeletedFalseOrderByNameAsc(participant.getMeetingId()))
            .isEmpty();
    assertThat(participantRepository
            .findAllByMeetingIdOrderByDeletedAscNameAsc(participant.getMeetingId()))
            .extracting(ParticipantEntity::getEmployeeNo)
            .containsExactly("a12345678");
}
```

- [ ] **Step 2: 创建三个人员 Mapper 和包含逻辑删除记录的 XML 查询**

`ParticipantMapper.xml` 的 including-deleted 查询不能依赖自动逻辑删除：

```xml
<select id="selectAllByMeetingIdIncludingDeleted"
        resultType="com.company.meetinghelper.participant.entity.ParticipantEntity">
    select *
    from t_participants
    where meeting_id = #{meetingId}
    order by deleted asc, name asc
</select>
```

- [ ] **Step 3: 替换三个具体 Repository**

保留以下公开方法和返回类型：

```java
List<ParticipantEntity> findAllByMeetingIdAndDeletedFalseOrderByNameAsc(String meetingId);
List<ParticipantEntity> findAllByMeetingIdOrderByDeletedAscNameAsc(String meetingId);
Optional<ParticipantEntity> findByIdAndMeetingIdAndDeletedFalse(String id, String meetingId);
Optional<ParticipantEntity> findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(
        String meetingId, String employeeNo);
long countByMeetingIdAndDeletedFalse(String meetingId);
```

字段定义按 `sortOrder`、人员记录按 `participantId, recordOrder` 排序；大小写工号比较统一使用 PostgreSQL `lower(employee_no) = lower(?)`。

- [ ] **Step 4: 运行人员、导入、版本恢复回归**

Run: `cd backend && mvn -Dtest=ParticipantRepositoryTests,ParticipantWorkbookParserTests,ParticipantRecordMergerTests,MeetingHelperIntegrationTests test`

Expected: 单人新增、Excel 导入、字段并集、记录合并、临时不出席、软删除恢复和并发工号校验全部 PASS。

- [ ] **Step 5: 提交通用人员持久层迁移**

```powershell
git add backend
git commit -m "迁移通用人员MyBatis持久层"
```

---

### Task 5: 迁移排座与版本 Mapper/Repository

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/seating/mapper/SeatingPlanMapper.java`
- Create: `backend/src/main/java/com/company/meetinghelper/seating/mapper/PlanItemMapper.java`
- Create: `backend/src/main/java/com/company/meetinghelper/seating/mapper/PlanItemTargetMapper.java`
- Create: `backend/src/main/java/com/company/meetinghelper/seating/mapper/PlanVersionMapper.java`
- Create: `backend/src/main/resources/mapper/seating/SeatingPlanMapper.xml`
- Replace: `backend/src/main/java/com/company/meetinghelper/seating/repository/SeatingPlanRepository.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/seating/repository/PlanItemRepository.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/seating/repository/PlanItemTargetRepository.java`
- Replace: `backend/src/main/java/com/company/meetinghelper/seating/repository/PlanVersionRepository.java`
- Create: `backend/src/test/java/com/company/meetinghelper/repository/SeatingRepositoryTests.java`

**Interfaces:**
- Consumes: `SeatingPlanMapper.selectOwnedById(@Param("planId") String planId, @Param("ownerId") String ownerId)`。
- Produces: 人员—排座明细—会议元素目标的现有绑定模型和版本快照查询行为。

- [ ] **Step 1: 写所有权、唯一座位和版本名称的失败测试**

测试匿名空间拥有方案、一个座位只能绑定一个有效目标、版本名称忽略大小写判重：

```java
@Test
void planRepositoryResolvesOwnershipThroughMeeting() {
    Optional<SeatingPlanEntity> owned =
            seatingPlanRepository.findOwnedById(planId, "");
    assertThat(owned).isPresent();
}
```

- [ ] **Step 2: 创建四个 Mapper 和排座方案所有权 XML**

```xml
<select id="selectOwnedById"
        resultType="com.company.meetinghelper.seating.entity.SeatingPlanEntity">
    select plan.*
    from t_seating_plans plan
    join t_meetings meeting on meeting.id = plan.meeting_id
    where plan.id = #{planId}
      and plan.deleted = false
      and meeting.created_by_id = #{ownerId}
      and meeting.deleted = false
</select>
```

- [ ] **Step 3: 替换四个具体 Repository**

保留：

```java
Optional<SeatingPlanEntity> findOwnedById(String planId, String ownerId);
Optional<SeatingPlanEntity> findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(String meetingId);
List<PlanItemEntity> findAllByPlanIdAndDeletedFalseOrderByCreatedAtAsc(String planId);
Optional<PlanItemEntity> findByPlanIdAndParticipantIdAndItemTypeAndDeletedFalse(
        String planId, String participantId, PlanItemType itemType);
List<PlanItemTargetEntity> findAllByPlanItemIdInAndDeletedFalse(Collection<String> planItemIds);
Optional<PlanItemTargetEntity> findByMeetingElementIdAndDeletedFalse(String meetingElementId);
void deleteAllByPlanItemId(String planItemId);
List<PlanVersionEntity> findAllByPlanIdAndDeletedFalseOrderByVersionNoDesc(String planId);
Optional<PlanVersionEntity> findFirstByPlanIdAndDeletedFalseOrderByVersionNoDesc(String planId);
boolean existsByPlanIdAndVersionNameIgnoreCaseAndDeletedFalse(String planId, String versionName);
```

交换座位和批量保存继续由 `SeatingService` 在同一事务中先释放目标关系、flush 对应 Mapper 操作后再插入新关系，避免 `un_t_target_element` 中间态冲突。

- [ ] **Step 4: 运行排座与版本真实 PostgreSQL 回归**

Run: `cd backend && mvn -Dtest=SeatingRepositoryTests,MeetingHelperIntegrationTests test`

Expected: 拖入座位、交换、移回待排、完整保存、锁定、发布、重名校验、版本恢复和并发约束全部 PASS。

- [ ] **Step 5: 提交排座和版本持久层迁移**

```powershell
git add backend
git commit -m "迁移排座与版本MyBatis持久层"
```

---

### Task 6: 完成 JPA/H2 清理、审计填充与 Holder 身份接入

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application.yml`
- Replace: `backend/src/main/java/com/company/meetinghelper/common/entity/AuditedEntity.java`
- Create: `backend/src/main/java/com/company/meetinghelper/common/security/CurrentUser.java`
- Create: `backend/src/main/java/com/company/meetinghelper/common/context/CurrentUserHolder.java`
- Create: `backend/src/main/java/com/company/meetinghelper/common/mybatis/AuditMetaObjectHandler.java`
- Delete: `backend/src/main/java/com/company/meetinghelper/common/user/CurrentUserProvider.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingAccessService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/service/MeetingService.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/service/VenueService.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`
- Create: `backend/src/test/java/com/company/meetinghelper/CurrentUserHolderTests.java`

**Interfaces:**
- Produces: `CurrentUser(String userId, String displayName, Set<String> memberSpaceId)`；`CurrentUserHolder.set/get/clear`；`AuditMetaObjectHandler` 自动填充 ID、创建/更新用户与时间。

- [ ] **Step 1: 写 Holder 空值与审计字段失败测试**

```java
@Test
void missingFrameworkUserFallsBackToAnonymousSpace() {
    CurrentUserHolder.clear();
    assertThat(CurrentUserHolder.userId()).isEmpty();
    assertThat(CurrentUserHolder.displayName()).isEmpty();
}
```

插入实体后同时断言 ID 为 UUID、创建和更新用户均为空字符串、时间非空。

- [ ] **Step 2: 实现公司结构一致的 Holder**

```java
public record CurrentUser(String userId, String displayName, Set<String> memberSpaceId) {
}

public final class CurrentUserHolder {
    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    public static void set(CurrentUser currentUser) { CURRENT_USER.set(currentUser); }
    public static CurrentUser get() { return CURRENT_USER.get(); }
    public static String userId() {
        CurrentUser user = get();
        return user == null || user.userId() == null ? "" : user.userId();
    }
    public static String displayName() {
        CurrentUser user = get();
        return user == null || user.displayName() == null ? "" : user.displayName();
    }
    public static void clear() { CURRENT_USER.remove(); }
}
```

- [ ] **Step 3: 使用 MetaObjectHandler 填充审计字段**

`AuditMetaObjectHandler` 在 insert 时填充 UUID、`createdById`、`createdByName`、`createdAt`、`updatedById`、`updatedByName`、`updatedAt`、`deleted=false`、`rowVersion=0`；update 时更新用户和时间。`rowVersion` 作为普通字段保留，不启用乐观锁插件。

- [ ] **Step 4: 删除 JPA/H2 依赖和全部 JPA 注解**

从 POM 删除 `spring-boot-starter-data-jpa`、JPA 测试 Starter 和 H2；删除所有 `jakarta.persistence`、`org.springframework.data.jpa` 引用及 YAML 的 `spring.jpa`。所有实体只保留 MyBatis-Plus 映射。

- [ ] **Step 5: 将业务身份读取改为 Holder**

删除 `CurrentUserProvider` 注入。需要用户范围的 Service/AccessService 直接调用：

```java
String userId = CurrentUserHolder.userId();
String displayName = CurrentUserHolder.displayName();
```

测试 `@BeforeEach` 设置用户、`@AfterEach` 清理；匿名行为用例显式 `clear()`。

- [ ] **Step 6: 运行 Holder、审计和全部后端测试**

Run: `cd backend && mvn test`

Expected: 全部 PASS；依赖树和源码不再包含 H2/JPA。

- [ ] **Step 7: 提交 ORM 清理和用户上下文迁移**

```powershell
git add backend
git commit -m "完成MyBatis迁移并接入用户上下文"
```

---

### Task 7: 删除演示数据初始化并固定空库启动行为

**Files:**
- Delete: `backend/src/main/java/com/company/meetinghelper/bootstrap/DemoDataInitializer.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperApplicationTests.java`
- Modify: `backend/src/test/java/com/company/meetinghelper/MeetingHelperIntegrationTests.java`
- Create: `backend/src/test/java/com/company/meetinghelper/NoDemoDataInitializationTests.java`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: 代码化 `PresetVenueCatalog` 和 `PresetVenueStore`。
- Produces: 空业务库启动后 `/meetings` 为空，但 `/venues` 仍包含系统预置场馆能力。

- [ ] **Step 1: 写无演示业务数据失败测试**

```java
@Test
void startupDoesNotInsertDemoBusinessRows() {
    assertThat(meetingRepository.findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(""))
            .isEmpty();
    assertThat(participantRepository.count()).isZero();
    assertThat(presetVenueStore.findAll()).isNotEmpty();
}
```

- [ ] **Step 2: 运行测试并确认初始化器写入数据**

Run: `cd backend && mvn -Dtest=NoDemoDataInitializationTests test`

Expected: FAIL，会议或人员表存在初始化记录。

- [ ] **Step 3: 删除初始化器并改造依赖演示 ID 的测试**

集成测试中的每个场景通过测试辅助方法显式创建场馆、会议、人员和方案，不依赖固定名称、固定 ID 或应用启动数据。保留预置场馆目录测试。

- [ ] **Step 4: 运行完整后端回归**

Run: `cd backend && mvn test`

Expected: 全部 PASS；测试启动日志没有 DemoDataInitializer。

- [ ] **Step 5: 提交初始化数据清理**

```powershell
git add backend CHANGELOG.md
git commit -m "删除演示业务数据初始化"
```

---

### Task 8: 统一 JSON 响应与 GET/POST 控制层契约

**Files:**
- Create: `backend/src/main/java/com/company/meetinghelper/common/api/ApiResponse.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/common/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/venue/api/VenueController.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/meeting/api/MeetingController.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/participant/api/ParticipantController.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/importing/api/ImportController.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/seating/api/SeatingController.java`
- Modify: `backend/src/main/java/com/company/meetinghelper/export/api/ExportController.java`
- Create: `backend/src/test/java/com/company/meetinghelper/ApiContractTests.java`
- Create: `backend/src/test/java/com/company/meetinghelper/HttpMethodConventionTests.java`

**Interfaces:**
- Produces: `ApiResponse.success(T data)`、`ApiResponse.success()`、`ApiResponse.failure(int code, String msg)`；二进制 Controller 保持 `ResponseEntity<byte[]>`。

- [ ] **Step 1: 写统一响应和 HTTP 方法失败测试**

MockMvc 断言：

```java
mockMvc.perform(get("/meetings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.msg").value("success"))
        .andExpect(jsonPath("$.data").isArray());
```

源码约束测试扫描 Controller，不允许 `@PutMapping`、`@PatchMapping`、`@DeleteMapping`。

- [ ] **Step 2: 运行契约测试并确认旧响应/旧方法失败**

Run: `cd backend && mvn -Dtest=ApiContractTests,HttpMethodConventionTests test`

Expected: FAIL，列表直接返回数组，且存在 PUT/DELETE 映射。

- [ ] **Step 3: 实现统一响应**

```java
public record ApiResponse<T>(int code, T data, String msg) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, data, "success");
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(0, null, "success");
    }

    public static ApiResponse<Void> failure(int code, String msg) {
        return new ApiResponse<>(code, null, msg);
    }
}
```

全局异常处理保留正确 HTTP 状态，同时用状态码作为失败 `code`，Validation 错误的 `msg` 返回首个可展示校验信息。

- [ ] **Step 4: 将 JSON Controller 显式包装 ApiResponse**

Controller 返回类型使用 `ApiResponse<List<VenueSummary>>`、`ApiResponse<WorkspaceResponse>` 等明确泛型；无数据写操作返回 `ApiResponse<Void>`。Excel/PDF 方法不包装。

- [ ] **Step 5: 将所有写接口改为 POST 动作路径**

固定路由：

```text
POST /venues/{id}/update
POST /venues/{id}/delete
POST /plans/{planId}/assignments/save
POST /plans/{planId}/participants/{participantId}/assignment/delete
POST /plans/{planId}/participants/{participantId}/lock
POST /meetings/{meetingId}/participants/{participantId}/attendance
POST /meetings/{meetingId}/participants/{participantId}/delete
```

其他原有 GET/POST 路径保持不变，控制层开头不增加 `/api`。

- [ ] **Step 6: 运行接口和完整后端回归**

Run: `cd backend && mvn test`

Expected: MockMvc 契约、GET/POST 约束和全部业务集成测试 PASS。

- [ ] **Step 7: 提交后端接口契约迁移**

```powershell
git add backend
git commit -m "统一后端响应与GETPOST接口"
```

---

### Task 9: 接入 Aurora 前端请求封装

**Files:**
- Create: `frontend/src/api/aurora.js`
- Replace: `frontend/src/api/http.js`
- Modify: `frontend/src/api/meeting.js`
- Modify: `frontend/src/auth/session.js`
- Modify: `frontend/tests/import-contract.test.js`
- Create: `frontend/tests/http-contract.test.js`
- Create: `frontend/tests/meeting-api-methods.test.js`

**Interfaces:**
- Consumes: 公司环境 `globalThis.Aurora.service.network`；外网环境 Axios。
- Produces: `http.get(path, config)`、`http.post(path, data, config)`、`unwrap(request)`、`requestBinary(request)`；`meetingApi` 保持供 Store/组件调用的业务方法名。

- [ ] **Step 1: 写 Aurora 优先、无用户请求头和仅 GET/POST 的失败测试**

源码与行为测试断言：

```javascript
test('请求层只暴露GET和POST且不追加用户请求头', async () => {
  assert.equal(typeof http.get, 'function')
  assert.equal(typeof http.post, 'function')
  assert.equal(http.put, undefined)
  assert.equal(http.delete, undefined)
})
```

另断言 `unwrap(Promise.resolve({ data: { code: 0, data: { id: '1' }, msg: 'success' } }))` 只返回 `{id:'1'}`，非零 code 抛出 `msg`。

- [ ] **Step 2: 运行前端契约测试并确认旧 Axios 层失败**

Run: `cd frontend && npm test -- --test-name-pattern="请求层|Aurora|GET和POST"`

Expected: FAIL，旧 `http` 暴露 `put/delete` 且请求拦截器写入 `X-User-Id`。

- [ ] **Step 3: 增加 Aurora 适配器**

```javascript
import axios from 'axios'

const fallbackNetwork = axios.create({ withCredentials: true })
const fallbackAurora = { service: { network: fallbackNetwork } }

export default globalThis.Aurora || fallbackAurora
```

- [ ] **Step 4: 实现统一 HTTP 和响应解包**

`http.js` 只导出：

```javascript
export const http = {
  get(path, config) {
    return Aurora.service.network.get(apiPath(path), config)
  },
  post(path, data, config) {
    return Aurora.service.network.post(apiPath(path), data, config)
  },
}

export async function unwrap(request) {
  const response = await request
  if (response.data?.code === 0) return response.data.data
  throw new Error(response.data?.msg || '操作失败，请稍后重试')
}
```

保留 `downloadBlob`；二进制请求用 `requestBinary()` 直接返回 `response.data`，不进入 JSON 解包。

- [ ] **Step 5: 修改领域 API 路径和方法**

`meeting.js` 的更新、删除、完整保存、锁定和出席操作全部调用 Task 8 的 POST 动作路径。领域 API 使用 `unwrap(http.get(...))` 或 `unwrap(http.post(...))`，组件调用签名不变。

- [ ] **Step 6: 运行前端全部测试和生产构建**

Run: `cd frontend && npm test`

Expected: 全部前端测试 PASS。

Run: `cd frontend && npm run build`

Expected: Vite 构建成功；没有 `package-lock.json` 变更被纳入 Git。

- [ ] **Step 7: 提交 Aurora 请求层迁移**

```powershell
git add frontend/src frontend/tests
git commit -m "迁移前端Aurora请求封装"
```

---

### Task 10: 清除 `var` 并建立长期架构约束

**Files:**
- Modify: `backend/src/main/java/**/*.java`
- Modify: `backend/src/test/java/**/*.java`
- Create: `backend/src/test/java/com/company/meetinghelper/ArchitectureConventionTests.java`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Produces: 自动扫描显式类型、JPA/H2、Mapper 覆盖、Controller 方法和 public Javadoc 的架构测试。

- [ ] **Step 1: 写架构约束失败测试**

测试读取 `src/main/java` 和 `src/test/java`：

```java
@Test
void javaSourcesUseExplicitLocalTypes() throws IOException {
    List<Path> offenders = javaFiles()
            .filter(path -> read(path).matches("(?s).*\\bvar\\s+[A-Za-z_$].*"))
            .toList();
    assertThat(offenders).isEmpty();
}
```

同一测试类还要断言：

- POM/YAML/Java 不包含 `com.h2database`、`jdbc:h2:`、`jakarta.persistence`、`org.springframework.data.jpa`。
- 11 个 `@TableName` 实体各有一个继承 `BaseMapper` 的 Mapper。
- Controller 不包含 PUT/PATCH/DELETE Mapping。
- Controller、Service、Repository 的 public 业务方法前存在 Javadoc。

- [ ] **Step 2: 运行架构测试并记录所有显式类型违规**

Run: `cd backend && mvn -Dtest=ArchitectureConventionTests test`

Expected: FAIL，并列出当前残留 `var` 或规范违规的文件。

- [ ] **Step 3: 将 main/test 的每个 var 替换为实际类型**

按编译器推断结果使用 `String`、`List<ParticipantEntity>`、`Optional<MeetingEntity>`、`Map<String, String>`、`XSSFWorkbook`、`MockHttpServletResponse` 等具体类型；不得使用 `Object` 或原始集合类型绕过约束。

- [ ] **Step 4: 补齐公开方法 Javadoc**

格式统一：

```java
/**
 * 根据会议ID查询当前用户可访问的工作区。
 *
 * @param meetingId 会议ID
 * @return 会议工作区完整数据
 */
public WorkspaceResponse getWorkspace(String meetingId) {
    // existing business logic
}
```

构造器、简单实体 getter/setter 和 Mapper 继承方法不纳入 public 业务方法 Javadoc 检查。

- [ ] **Step 5: 运行架构测试、编译和全部回归**

Run: `cd backend && mvn test`

Expected: 全部 PASS；`rg -n "\bvar\s+[A-Za-z_$]" backend/src` 无结果。

- [ ] **Step 6: 提交显式类型和架构门禁**

```powershell
git add backend CHANGELOG.md
git commit -m "统一Java显式类型并增加架构校验"
```

---

### Task 11: 全量验证、数据库核验与主分支交付

**Files:**
- Modify: `CHANGELOG.md`
- Verify only: `DDL/meeting_helper.sql`

**Interfaces:**
- Consumes: Tasks 1–10 的完整实现。
- Produces: PostgreSQL 业务库可启动、测试库全回归、前端可构建、main 可推送的交付状态。

- [ ] **Step 1: 检查 DDL 是否发生业务结构变化**

Run: `git diff 483916d -- DDL/meeting_helper.sql`

Expected: 本次仅技术栈迁移时无差异。若确有为 MyBatis 映射修复所必需的 DDL 变化，则先确认 SQL 文件仍只包含完整建表和注释语句，再按用户已授权流程分别重建 `meeting_helper_test` 和 `meeting_helper`。

- [ ] **Step 2: 验证两个 PostgreSQL 数据库及表结构**

分别连接两个数据库执行：

```sql
select current_database();
select count(*)
from information_schema.tables
where table_schema = 'public'
  and table_name like 't\_%' escape '\';
```

Expected: 数据库名分别为 `meeting_helper`、`meeting_helper_test`；11 张 `t_` 业务表存在。

- [ ] **Step 3: 运行后端全量测试**

Run: `cd backend && mvn clean test`

Expected: 全部测试 PASS；测试连接仅指向 `meeting_helper_test`。

- [ ] **Step 4: 运行前端全量测试与构建**

Run: `cd frontend && npm test`

Expected: 全部测试 PASS。

Run: `cd frontend && npm run build`

Expected: 构建成功。

- [ ] **Step 5: 启动前后端进行页面冒烟验证**

验证首页空会议列表、场馆库预置模板、创建会议、进入草稿工作区、单人新增、拖动排座、保存、发布、查看已发布版本和导出。浏览器网络面板中 JSON 响应均为 `{code,data,msg}`，业务请求只有 GET/POST，没有 `X-User-Id`。

- [ ] **Step 6: 执行最终静态检查**

Run: `git diff --check`

Expected: 无空白错误。

Run: `rg -n "com\\.h2database|jdbc:h2:|jakarta\\.persistence|org\\.springframework\\.data\\.jpa|@PutMapping|@PatchMapping|@DeleteMapping|X-User-Id|\\bvar\\s+[A-Za-z_$]" backend frontend`

Expected: 无结果。

- [ ] **Step 7: 补充最终变更时间并提交**

`CHANGELOG.md` 使用执行时的真实北京时间，记录 PostgreSQL/MyBatis-Plus、统一响应、GET/POST、Aurora、Holder、初始化数据清理和回归结果。

```powershell
git add .
git commit -m "完成公司技术栈迁移与回归验证"
```

- [ ] **Step 8: 合并、清理并推送**

确认功能分支全部测试通过后合并回 `main`，删除本地和远程多余功能分支，推送 `main`。最终 `git status --short --branch` 必须显示 `main` 与 `origin/main` 同步且工作区干净。
