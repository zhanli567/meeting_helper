package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.api.AgentSseWriter;
import com.company.meetinghelper.agent.runtime.AgentEvent;
import com.company.meetinghelper.agent.runtime.AgentEventType;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentSseWriterTests {

    private final AgentSseWriter writer = new AgentSseWriter();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WriterProbeController()).build();

    @Test
    void writesRunStartedAsLowercaseSseEventWithAgentEventData() throws Exception {
        assertSseEvent(AgentEventType.RUN_STARTED, "run_started");
    }

    @Test
    void writesAssistantTextAsLowercaseSseEventWithAgentEventData() throws Exception {
        assertSseEvent(AgentEventType.ASSISTANT_TEXT, "assistant_text");
    }

    private void assertSseEvent(AgentEventType type, String eventName) throws Exception {
        String response = mockMvc.perform(get("/probe/{type}", type.name()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("event:" + eventName);
        assertThat(response).contains("\"type\":\"" + type.name() + "\"");
        assertThat(response).contains("\"text\":\"hello\"");
    }

    @org.springframework.web.bind.annotation.RestController
    public final class WriterProbeController {

        @org.springframework.web.bind.annotation.GetMapping(value = "/probe/{type}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter probe(@org.springframework.web.bind.annotation.PathVariable AgentEventType type) throws Exception {
            AgentEvent event = new AgentEvent(
                    "run-1", "conversation-1", "event-1", 1, type,
                    Map.of("text", "hello"), OffsetDateTime.now());
            SseEmitter emitter = new SseEmitter();
            emitter.send(writer.toSseEvent(event));
            emitter.complete();
            return emitter;
        }
    }
}
