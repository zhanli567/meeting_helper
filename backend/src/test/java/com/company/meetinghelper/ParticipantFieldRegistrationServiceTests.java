package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import com.company.meetinghelper.participant.repository.MeetingParticipantFieldRepository;
import com.company.meetinghelper.participant.service.ParticipantFieldRegistrationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ParticipantFieldRegistrationServiceTests {

    @Test
    void registerFieldsRejectsNewFieldWhenMeetingAlreadyHasFifteenFields() {
        MeetingRepository meetingRepository = Mockito.mock(MeetingRepository.class);
        MeetingParticipantFieldRepository fieldRepository =
                Mockito.mock(MeetingParticipantFieldRepository.class);
        ParticipantFieldRegistrationService service =
                new ParticipantFieldRegistrationService(meetingRepository, fieldRepository);

        when(meetingRepository.findByIdForUpdate("meeting-1"))
                .thenReturn(Optional.of(new MeetingEntity()));
        when(fieldRepository.findAllByMeetingIdOrderBySortOrderAsc("meeting-1"))
                .thenReturn(existingFields(15));

        assertThatThrownBy(() -> service.registerFields("meeting-1", List.of("字段16")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("最多支持 15 个扩展字段");
        verify(fieldRepository, never()).save(any(MeetingParticipantFieldEntity.class));
    }

    private List<MeetingParticipantFieldEntity> existingFields(int count) {
        ArrayList<MeetingParticipantFieldEntity> fields = new ArrayList<MeetingParticipantFieldEntity>();
        for (int index = 1; index <= count; index++) {
            MeetingParticipantFieldEntity field = new MeetingParticipantFieldEntity();
            field.setMeetingId("meeting-1");
            field.setFieldName("字段" + index);
            field.setSortOrder(index);
            fields.add(field);
        }
        return fields;
    }
}
