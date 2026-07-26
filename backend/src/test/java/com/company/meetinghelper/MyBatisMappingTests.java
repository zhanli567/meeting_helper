package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import com.company.meetinghelper.seating.entity.PlanVersionEntity;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.venue.entity.VenueElementEntity;
import com.company.meetinghelper.venue.entity.VenueTemplateEntity;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

class MyBatisMappingTests {

    @Test
    void persistentEntitiesMapToExistingPrefixedTables() {
        Map<Class<?>, String> expectedTables = Map.ofEntries(
                Map.entry(VenueTemplateEntity.class, "t_venue_templates"),
                Map.entry(VenueElementEntity.class, "t_venue_elements"),
                Map.entry(MeetingEntity.class, "t_meetings"),
                Map.entry(MeetingElementEntity.class, "t_meeting_elements"),
                Map.entry(ParticipantEntity.class, "t_participants"),
                Map.entry(MeetingParticipantFieldEntity.class, "t_meeting_participant_fields"),
                Map.entry(ParticipantRecordEntity.class, "t_participant_records"),
                Map.entry(SeatingPlanEntity.class, "t_seating_plans"),
                Map.entry(PlanItemEntity.class, "t_plan_items"),
                Map.entry(PlanItemTargetEntity.class, "t_plan_item_targets"),
                Map.entry(PlanVersionEntity.class, "t_plan_versions")
        );

        expectedTables.forEach((entityType, tableName) -> {
            TableName mapping = entityType.getAnnotation(TableName.class);
            assertThat(mapping)
                    .as("%s must declare @TableName", entityType.getSimpleName())
                    .isNotNull();
            assertThat(mapping.value()).isEqualTo(tableName);
        });
    }

    @Test
    void auditedEntityUsesMyBatisLogicalDeletion() {
        Field deleted = ReflectionUtils.findField(AuditedEntity.class, "deleted");

        assertThat(deleted).isNotNull();
        assertThat(deleted.getAnnotation(TableLogic.class)).isNotNull();
    }
}
