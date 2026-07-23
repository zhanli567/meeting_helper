create table venue_templates (
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
    deleted boolean not null,
    row_version bigint not null
);

create table venue_elements (
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
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_venue_element_template foreign key (venue_template_id) references venue_templates(id)
);

create table meetings (
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
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_meeting_template foreign key (venue_template_id) references venue_templates(id)
);

create table meeting_elements (
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
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_meeting_element_meeting foreign key (meeting_id) references meetings(id)
);

create table participants (
    id varchar(36) primary key,
    meeting_id varchar(36) not null,
    employee_no varchar(9) not null,
    name varchar(80) not null,
    level_value integer,
    department varchar(160),
    participant_type varchar(80),
    tags varchar(500),
    custom_attributes_json clob,
    locked boolean not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_participant_meeting foreign key (meeting_id) references meetings(id),
    constraint uk_participant_employee unique (meeting_id, employee_no)
);

create table award_records (
    id varchar(36) primary key,
    participant_id varchar(36) not null,
    batch_order integer not null,
    batch_name varchar(80) not null,
    award_name varchar(200) not null,
    award_level varchar(80),
    project_name varchar(240),
    team_size integer,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_award_participant foreign key (participant_id) references participants(id)
);

create table seating_plans (
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
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_plan_meeting foreign key (meeting_id) references meetings(id)
);

create table plan_items (
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
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_item_plan foreign key (plan_id) references seating_plans(id),
    constraint fk_item_participant foreign key (participant_id) references participants(id)
);

create table plan_item_targets (
    id varchar(36) primary key,
    plan_item_id varchar(36) not null,
    meeting_element_id varchar(36) not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_target_item foreign key (plan_item_id) references plan_items(id),
    constraint fk_target_element foreign key (meeting_element_id) references meeting_elements(id),
    constraint uk_target_element unique (meeting_element_id)
);

create table plan_versions (
    id varchar(36) primary key,
    plan_id varchar(36) not null,
    version_no integer not null,
    version_name varchar(120) not null,
    change_note varchar(500),
    automatic boolean not null,
    snapshot_json clob not null,
    assigned_count integer not null,
    unassigned_count integer not null,
    created_by_id varchar(64) not null,
    created_by_name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_by_id varchar(64) not null,
    updated_by_name varchar(80) not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null,
    row_version bigint not null,
    constraint fk_version_plan foreign key (plan_id) references seating_plans(id),
    constraint uk_plan_version unique (plan_id, version_no)
);

create index idx_element_template on venue_elements(venue_template_id);
create index idx_meeting_element_meeting on meeting_elements(meeting_id);
create index idx_participant_meeting on participants(meeting_id);
create index idx_award_participant on award_records(participant_id);
create index idx_plan_meeting on seating_plans(meeting_id);
create index idx_item_plan on plan_items(plan_id);
create index idx_target_item on plan_item_targets(plan_item_id);
create index idx_version_plan on plan_versions(plan_id);

