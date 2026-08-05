package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.doThrow;

import com.company.meetinghelper.agent.runtime.AgentEvent;
import com.company.meetinghelper.agent.runtime.AgentEventType;
import com.company.meetinghelper.agent.runtime.AgentRuntime;
import com.company.meetinghelper.support.PostgreSqlTestDatabaseInitializer;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "agent.enabled=true",
        "agent.provider=mock"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgreSqlTestDatabaseInitializer.class)
class AgentControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private AgentRuntime agentRuntime;

    @Test
    void agentChatReturnsSseEvents() throws Exception {
        doAnswer(invocation -> {
            Consumer<AgentEvent> eventSink = invocation.getArgument(1);
            eventSink.accept(event(AgentEventType.RUN_STARTED, Map.of("provider", "mock")));
            eventSink.accept(event(AgentEventType.RUN_DONE, Map.of()));
            return null;
        }).when(agentRuntime).run(any(), any());

        String body = """
                {
                  "conversationId": "c1",
                  "meetingId": "missing-meeting",
                  "message": "当前会议概况",
                  "stream": true,
                  "mode": "QUERY"
                }
                """;

        MvcResult result = mockMvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andReturn();

        String response = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("event:run_started");
        assertThat(response).contains("event:run_done");
        assertThat(response).contains("\"runId\"");
    }

    @Test
    void agentChatSendsErrorEventWhenRuntimeFails() throws Exception {
        doThrow(new IllegalStateException("runtime failed")).when(agentRuntime).run(any(), any());

        MvcResult result = mockMvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "conversationId": "c1",
                                  "meetingId": "missing-meeting",
                                  "message": "当前会议概况",
                                  "stream": true,
                                  "mode": "QUERY"
                                }
                                """))
                .andReturn();

        Object asyncResult = result.getAsyncResult(1000);
        String response = result.getResponse().getContentAsString();

        assertThat(response).contains("event:error");
        assertThat(response).contains("AGENT_RUNTIME_ERROR");
        assertThat(asyncResult).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void agentChatInvokesRealRuntimeForGuardrailBlockedQuery() throws Exception {
        MvcResult result = mockMvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "conversationId": "c1",
                                  "meetingId": "missing-meeting",
                                  "message": "保存当前安排",
                                  "stream": true,
                                  "mode": "QUERY"
                                }
                                """))
                .andReturn();

        String response = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("event:guardrail_blocked");
        assertThat(response).contains("event:run_done");
    }

    private AgentEvent event(AgentEventType type, Map<String, Object> payload) {
        return new AgentEvent("run-1", "c1", "event-1", 0, type, payload, OffsetDateTime.now());
    }
}
