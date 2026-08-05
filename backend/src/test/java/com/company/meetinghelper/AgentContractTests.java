package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.api.dto.AgentChatRequest;
import com.company.meetinghelper.agent.api.dto.AgentMode;
import com.company.meetinghelper.agent.config.AgentProperties;
import com.company.meetinghelper.agent.runtime.AgentEvent;
import com.company.meetinghelper.agent.runtime.AgentEventType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = MeetingHelperApplication.class)
class AgentContractTests {

    @Autowired
    private AgentProperties boundProperties;

    @Test
    void agentPropertiesUseSafeDefaults() {
        AgentProperties properties = new AgentProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getProvider()).isEqualTo("mock");
        assertThat(properties.getMaxToolSteps()).isEqualTo(8);
        assertThat(properties.isAllowFrontendDraftTools()).isFalse();
        assertThat(properties.isAllowCommitTools()).isFalse();
    }

    @Test
    void agentEventHasStableEnvelope() {
        AgentEvent event = new AgentEvent(
                "run-1",
                "conversation-1",
                "event-1",
                1,
                AgentEventType.RUN_STARTED,
                Map.of("mode", AgentMode.QUERY.name()),
                OffsetDateTime.parse("2026-08-05T10:00:00+08:00")
        );

        assertThat(event.runId()).isEqualTo("run-1");
        assertThat(event.type()).isEqualTo(AgentEventType.RUN_STARTED);
        assertThat(event.payload()).containsEntry("mode", "QUERY");
    }

    @Test
    void agentModeContractOnlySupportsQueryForMvp() {
        assertThat(List.of(AgentMode.values())).containsExactly(AgentMode.QUERY);
    }

    @Test
    void agentChatRequestExposesAllFields() {
        AgentChatRequest request = new AgentChatRequest(
                "conversation-1",
                "meeting-1",
                "revision-1",
                "查询空闲座位",
                true,
                AgentMode.QUERY
        );

        assertThat(request.conversationId()).isEqualTo("conversation-1");
        assertThat(request.meetingId()).isEqualTo("meeting-1");
        assertThat(request.workspaceRevision()).isEqualTo("revision-1");
        assertThat(request.message()).isEqualTo("查询空闲座位");
        assertThat(request.stream()).isTrue();
        assertThat(request.mode()).isEqualTo(AgentMode.QUERY);
    }

    @Test
    void agentEventTypesHaveStableOrder() {
        assertThat(List.of(AgentEventType.values())).containsExactly(
                AgentEventType.RUN_STARTED,
                AgentEventType.ASSISTANT_TEXT,
                AgentEventType.TOOL_CALL,
                AgentEventType.TOOL_RESULT,
                AgentEventType.GUARDRAIL_BLOCKED,
                AgentEventType.ERROR,
                AgentEventType.RUN_DONE
        );
    }

    @Test
    void agentPropertiesBindFromApplicationConfiguration() {
        assertThat(boundProperties.isEnabled()).isFalse();
        assertThat(boundProperties.getProvider()).isEqualTo("mock");
        assertThat(boundProperties.getMaxToolSteps()).isEqualTo(8);
        assertThat(boundProperties.getMaxModelRetriesPerStep()).isEqualTo(1);
        assertThat(boundProperties.getMaxToolRetriesPerStep()).isEqualTo(1);
        assertThat(boundProperties.getMaxDurationSeconds()).isEqualTo(60);
        assertThat(boundProperties.isAllowFrontendDraftTools()).isFalse();
        assertThat(boundProperties.isAllowCommitTools()).isFalse();
    }

    @Nested
    @SpringBootTest(
            classes = MeetingHelperApplication.class,
            properties = "spring.config.location=classpath:/application.yml"
    )
    class MainApplicationConfigurationTests {

        @Autowired
        private AgentProperties mainProperties;

        @Test
        void agentPropertiesBindFromMainApplicationConfiguration() {
            assertThat(mainProperties.isEnabled()).isFalse();
            assertThat(mainProperties.getProvider()).isEqualTo("mock");
            assertThat(mainProperties.getMaxToolSteps()).isEqualTo(8);
            assertThat(mainProperties.getMaxModelRetriesPerStep()).isEqualTo(1);
            assertThat(mainProperties.getMaxToolRetriesPerStep()).isEqualTo(1);
            assertThat(mainProperties.getMaxDurationSeconds()).isEqualTo(60);
            assertThat(mainProperties.isAllowFrontendDraftTools()).isFalse();
            assertThat(mainProperties.isAllowCommitTools()).isFalse();
        }
    }
}
