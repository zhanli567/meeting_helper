create table if not exists t_venue_templates (
    id varchar(36) primary key,
    name varchar(120) not null,
    description varchar(500),
    grid_rows integer not null,
    grid_columns integer not null,
    cell_size integer not null,
    version_no integer not null,
    preset boolean not null,
    front_direction varchar(20) not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null
);

comment on table t_venue_templates is '场馆布局模板';
comment on column t_venue_templates.id is '场馆模板主键';
comment on column t_venue_templates.name is '场馆模板名称';
comment on column t_venue_templates.description is '场馆模板说明';
comment on column t_venue_templates.grid_rows is '布局网格总行数';
comment on column t_venue_templates.grid_columns is '布局网格总列数';
comment on column t_venue_templates.cell_size is '单个网格的基准像素尺寸';
comment on column t_venue_templates.version_no is '场馆模板版本号';
comment on column t_venue_templates.preset is '是否为系统预置模板';
comment on column t_venue_templates.front_direction is '参会人员面向舞台的方向';
comment on column t_venue_templates.created_by_id is '创建人标识';
comment on column t_venue_templates.created_by_name is '创建人姓名';
comment on column t_venue_templates.created_at is '创建时间';
comment on column t_venue_templates.updated_by_id is '最后更新人标识';
comment on column t_venue_templates.updated_by_name is '最后更新人姓名';
comment on column t_venue_templates.updated_at is '最后更新时间';
comment on column t_venue_templates.row_version is '乐观锁版本号';

create table if not exists t_venue_elements (
    id varchar(36) primary key,
    venue_template_id varchar(36) not null,
    element_type varchar(30) not null,
    code varchar(80),
    label varchar(120),
    grid_row integer not null,
    grid_column integer not null,
    row_span integer not null,
    column_span integer not null,
    rotation integer not null,
    capacity integer not null,
    assignable boolean not null,
    walkable boolean not null,
    group_code varchar(80),
    group_label varchar(80),
    sequence_no integer,
    background_color varchar(20),
    border_color varchar(20),
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null
);

comment on table t_venue_elements is '场馆模板中的布局元素';
comment on column t_venue_elements.id is '场馆元素主键';
comment on column t_venue_elements.venue_template_id is '所属场馆模板标识';
comment on column t_venue_elements.element_type is '元素类型，如座位、舞台、走廊、墙、门或桌子';
comment on column t_venue_elements.code is '元素显示编号';
comment on column t_venue_elements.label is '元素显示名称';
comment on column t_venue_elements.grid_row is '元素起始网格行';
comment on column t_venue_elements.grid_column is '元素起始网格列';
comment on column t_venue_elements.row_span is '元素占用的网格行数';
comment on column t_venue_elements.column_span is '元素占用的网格列数';
comment on column t_venue_elements.rotation is '元素顺时针旋转角度';
comment on column t_venue_elements.capacity is '元素可容纳人数';
comment on column t_venue_elements.assignable is '是否允许安排参会人员';
comment on column t_venue_elements.walkable is '是否可作为通行路径';
comment on column t_venue_elements.group_code is '元素所属区域编码';
comment on column t_venue_elements.group_label is '元素所属区域名称';
comment on column t_venue_elements.sequence_no is '元素在区域内的排序号';
comment on column t_venue_elements.background_color is '元素背景颜色';
comment on column t_venue_elements.border_color is '元素边框颜色';
comment on column t_venue_elements.created_by_id is '创建人标识';
comment on column t_venue_elements.created_by_name is '创建人姓名';
comment on column t_venue_elements.created_at is '创建时间';
comment on column t_venue_elements.updated_by_id is '最后更新人标识';
comment on column t_venue_elements.updated_by_name is '最后更新人姓名';
comment on column t_venue_elements.updated_at is '最后更新时间';
comment on column t_venue_elements.row_version is '乐观锁版本号';

create table if not exists t_meetings (
    id varchar(36) primary key,
    name varchar(160) not null,
    status varchar(30) not null,
    venue_template_id varchar(36),
    layout_name varchar(120) not null,
    grid_rows integer not null,
    grid_columns integer not null,
    cell_size integer not null,
    layout_version integer not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null
);

comment on table t_meetings is '会议及其场馆布局快照';
comment on column t_meetings.id is '会议主键';
comment on column t_meetings.name is '会议名称';
comment on column t_meetings.status is '会议状态';
comment on column t_meetings.venue_template_id is '创建会议时使用的场馆模板标识';
comment on column t_meetings.layout_name is '会议场馆快照名称';
comment on column t_meetings.grid_rows is '会议布局网格总行数';
comment on column t_meetings.grid_columns is '会议布局网格总列数';
comment on column t_meetings.cell_size is '单个网格的基准像素尺寸';
comment on column t_meetings.layout_version is '会议布局快照版本号';
comment on column t_meetings.created_by_id is '创建人标识';
comment on column t_meetings.created_by_name is '创建人姓名';
comment on column t_meetings.created_at is '创建时间';
comment on column t_meetings.updated_by_id is '最后更新人标识';
comment on column t_meetings.updated_by_name is '最后更新人姓名';
comment on column t_meetings.updated_at is '最后更新时间';
comment on column t_meetings.row_version is '乐观锁版本号';

create table if not exists t_meeting_elements (
    id varchar(36) primary key,
    meeting_id varchar(36) not null,
    source_element_id varchar(36),
    element_type varchar(30) not null,
    code varchar(80),
    label varchar(120),
    grid_row integer not null,
    grid_column integer not null,
    row_span integer not null,
    column_span integer not null,
    rotation integer not null,
    capacity integer not null,
    assignable boolean not null,
    walkable boolean not null,
    group_code varchar(80),
    group_label varchar(80),
    sequence_no integer,
    background_color varchar(20),
    border_color varchar(20),
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null
);

comment on table t_meeting_elements is '会议创建时复制的布局元素快照';
comment on column t_meeting_elements.id is '会议布局元素主键';
comment on column t_meeting_elements.meeting_id is '所属会议标识';
comment on column t_meeting_elements.source_element_id is '来源场馆模板元素标识';
comment on column t_meeting_elements.element_type is '元素类型，如座位、舞台、走廊、墙、门或桌子';
comment on column t_meeting_elements.code is '元素显示编号';
comment on column t_meeting_elements.label is '元素显示名称';
comment on column t_meeting_elements.grid_row is '元素起始网格行';
comment on column t_meeting_elements.grid_column is '元素起始网格列';
comment on column t_meeting_elements.row_span is '元素占用的网格行数';
comment on column t_meeting_elements.column_span is '元素占用的网格列数';
comment on column t_meeting_elements.rotation is '元素顺时针旋转角度';
comment on column t_meeting_elements.capacity is '元素可容纳人数';
comment on column t_meeting_elements.assignable is '是否允许安排参会人员';
comment on column t_meeting_elements.walkable is '是否可作为通行路径';
comment on column t_meeting_elements.group_code is '元素所属区域编码';
comment on column t_meeting_elements.group_label is '元素所属区域名称';
comment on column t_meeting_elements.sequence_no is '元素在区域内的排序号';
comment on column t_meeting_elements.background_color is '元素背景颜色';
comment on column t_meeting_elements.border_color is '元素边框颜色';
comment on column t_meeting_elements.created_by_id is '创建人标识';
comment on column t_meeting_elements.created_by_name is '创建人姓名';
comment on column t_meeting_elements.created_at is '创建时间';
comment on column t_meeting_elements.updated_by_id is '最后更新人标识';
comment on column t_meeting_elements.updated_by_name is '最后更新人姓名';
comment on column t_meeting_elements.updated_at is '最后更新时间';
comment on column t_meeting_elements.row_version is '乐观锁版本号';

create table if not exists t_participants (
    id varchar(36) primary key,
    meeting_id varchar(36) not null,
    employee_no varchar(9) not null,
    name varchar(80) not null,
    attendance_status varchar(30) not null default 'PRESENT',
    locked boolean not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null,
    constraint uk_t_participant_employee unique (meeting_id, employee_no)
);

comment on table t_participants is '会议参会人员';
comment on column t_participants.id is '参会人员主键';
comment on column t_participants.meeting_id is '所属会议标识';
comment on column t_participants.employee_no is '公司工号，格式为8位数字或1个小写字母加8位数字';
comment on column t_participants.name is '参会人员姓名';
comment on column t_participants.attendance_status is '出席状态：PRESENT正常出席，TEMPORARILY_ABSENT临时不出席';
comment on column t_participants.locked is '人员是否锁定不可移动';
comment on column t_participants.created_by_id is '创建人标识';
comment on column t_participants.created_by_name is '创建人姓名';
comment on column t_participants.created_at is '创建时间';
comment on column t_participants.updated_by_id is '最后更新人标识';
comment on column t_participants.updated_by_name is '最后更新人姓名';
comment on column t_participants.updated_at is '最后更新时间';
comment on column t_participants.row_version is '乐观锁版本号';

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
    row_version bigint not null,
    constraint uk_t_participant_field_name unique (meeting_id, field_name)
);

comment on table t_meeting_participant_fields is '会议自定义人员字段定义';
comment on column t_meeting_participant_fields.id is '会议人员字段主键';
comment on column t_meeting_participant_fields.meeting_id is '所属会议标识';
comment on column t_meeting_participant_fields.field_name is '自定义字段名称';
comment on column t_meeting_participant_fields.sort_order is '字段展示排序号';
comment on column t_meeting_participant_fields.created_by_id is '创建人标识';
comment on column t_meeting_participant_fields.created_by_name is '创建人姓名';
comment on column t_meeting_participant_fields.created_at is '创建时间';
comment on column t_meeting_participant_fields.updated_by_id is '最后更新人标识';
comment on column t_meeting_participant_fields.updated_by_name is '最后更新人姓名';
comment on column t_meeting_participant_fields.updated_at is '最后更新时间';
comment on column t_meeting_participant_fields.row_version is '乐观锁版本号';

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
    row_version bigint not null,
    constraint uk_t_participant_record_order unique (participant_id, record_order)
);

comment on table t_participant_records is '参会人员通用扩展记录';
comment on column t_participant_records.id is '人员记录主键';
comment on column t_participant_records.participant_id is '关联参会人员标识';
comment on column t_participant_records.record_order is '人员记录排序号';
comment on column t_participant_records.attributes_json is '人员扩展属性 JSON';
comment on column t_participant_records.created_by_id is '创建人标识';
comment on column t_participant_records.created_by_name is '创建人姓名';
comment on column t_participant_records.created_at is '创建时间';
comment on column t_participant_records.updated_by_id is '最后更新人标识';
comment on column t_participant_records.updated_by_name is '最后更新人姓名';
comment on column t_participant_records.updated_at is '最后更新时间';
comment on column t_participant_records.row_version is '乐观锁版本号';

create table if not exists t_seating_plans (
    id varchar(36) primary key,
    meeting_id varchar(36) not null,
    name varchar(120) not null,
    status varchar(30) not null,
    current_version_no integer not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null
);

comment on table t_seating_plans is '会议当前可编辑的排座方案';
comment on column t_seating_plans.id is '排座方案主键';
comment on column t_seating_plans.meeting_id is '所属会议标识';
comment on column t_seating_plans.name is '排座方案名称';
comment on column t_seating_plans.status is '排座方案状态';
comment on column t_seating_plans.current_version_no is '当前已发布版本号';
comment on column t_seating_plans.created_by_id is '创建人标识';
comment on column t_seating_plans.created_by_name is '创建人姓名';
comment on column t_seating_plans.created_at is '创建时间';
comment on column t_seating_plans.updated_by_id is '最后更新人标识';
comment on column t_seating_plans.updated_by_name is '最后更新人姓名';
comment on column t_seating_plans.updated_at is '最后更新时间';
comment on column t_seating_plans.row_version is '乐观锁版本号';

create table if not exists t_plan_items (
    id varchar(36) primary key,
    plan_id varchar(36) not null,
    item_type varchar(30) not null,
    participant_id varchar(36),
    label varchar(120),
    locked boolean not null,
    background_color varchar(20),
    text_color varchar(20),
    bold boolean not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null
);

comment on table t_plan_items is '排座方案中的人员、设备、预留或禁用占用项';
comment on column t_plan_items.id is '排座占用项主键';
comment on column t_plan_items.plan_id is '所属排座方案标识';
comment on column t_plan_items.item_type is '占用项类型，如人员、设备、预留或禁用';
comment on column t_plan_items.participant_id is '关联参会人员标识，非人员占用时为空';
comment on column t_plan_items.label is '占用项显示名称';
comment on column t_plan_items.locked is '占用项是否锁定不可移动';
comment on column t_plan_items.background_color is '占用项背景颜色';
comment on column t_plan_items.text_color is '占用项文字颜色';
comment on column t_plan_items.bold is '占用项文字是否加粗';
comment on column t_plan_items.created_by_id is '创建人标识';
comment on column t_plan_items.created_by_name is '创建人姓名';
comment on column t_plan_items.created_at is '创建时间';
comment on column t_plan_items.updated_by_id is '最后更新人标识';
comment on column t_plan_items.updated_by_name is '最后更新人姓名';
comment on column t_plan_items.updated_at is '最后更新时间';
comment on column t_plan_items.row_version is '乐观锁版本号';

create table if not exists t_plan_item_targets (
    id varchar(36) primary key,
    plan_item_id varchar(36) not null,
    meeting_element_id varchar(36) not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null,
    constraint uk_t_target_element unique (meeting_element_id)
);

comment on table t_plan_item_targets is '排座占用项与会议布局元素的占用关系';
comment on column t_plan_item_targets.id is '占用目标关系主键';
comment on column t_plan_item_targets.plan_item_id is '排座占用项标识';
comment on column t_plan_item_targets.meeting_element_id is '被占用的会议布局元素标识';
comment on column t_plan_item_targets.created_by_id is '创建人标识';
comment on column t_plan_item_targets.created_by_name is '创建人姓名';
comment on column t_plan_item_targets.created_at is '创建时间';
comment on column t_plan_item_targets.updated_by_id is '最后更新人标识';
comment on column t_plan_item_targets.updated_by_name is '最后更新人姓名';
comment on column t_plan_item_targets.updated_at is '最后更新时间';
comment on column t_plan_item_targets.row_version is '乐观锁版本号';

create table if not exists t_plan_versions (
    id varchar(36) primary key,
    plan_id varchar(36) not null,
    version_no integer not null,
    version_name varchar(120) not null,
    change_note varchar(500),
    automatic boolean not null,
    snapshot_json text not null,
    assigned_count integer not null,
    unassigned_count integer not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null,
    constraint uk_t_plan_version unique (plan_id, version_no)
);

comment on table t_plan_versions is '排座方案发布后的不可变版本快照';
comment on column t_plan_versions.id is '排座版本主键';
comment on column t_plan_versions.plan_id is '所属排座方案标识';
comment on column t_plan_versions.version_no is '版本序号';
comment on column t_plan_versions.version_name is '版本名称';
comment on column t_plan_versions.change_note is '版本变更说明';
comment on column t_plan_versions.automatic is '是否由系统自动生成';
comment on column t_plan_versions.snapshot_json is '发布时完整工作区快照 JSON';
comment on column t_plan_versions.assigned_count is '发布时已排座人数';
comment on column t_plan_versions.unassigned_count is '发布时待排座人数';
comment on column t_plan_versions.created_by_id is '创建人标识';
comment on column t_plan_versions.created_by_name is '创建人姓名';
comment on column t_plan_versions.created_at is '创建时间';
comment on column t_plan_versions.updated_by_id is '最后更新人标识';
comment on column t_plan_versions.updated_by_name is '最后更新人姓名';
comment on column t_plan_versions.updated_at is '最后更新时间';
comment on column t_plan_versions.row_version is '乐观锁版本号';
