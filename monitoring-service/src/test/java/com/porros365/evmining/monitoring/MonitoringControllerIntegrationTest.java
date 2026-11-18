package com.porros365.evmining.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.messaging.MonitoringLogEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:monitoringtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class MonitoringControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPersistAndRetrieveDistributedExecutionLogsAndHealth() throws Exception {
        UUID executionId = UUID.randomUUID();
        UUID stepExecutionId = UUID.randomUUID();

        MonitoringLogEventMessage event = new MonitoringLogEventMessage(
                UUID.randomUUID(),
                executionId,
                stepExecutionId,
                3,
                "data-mining-service",
                "INFO",
                "STEP_3_DEVIG_COMPLETED",
                "De-Vigging Multiplicativo completado con cuota justa 1.95",
                Map.of("fairOdds", 1.95, "trueProbability", 0.513),
                Instant.now()
        );

        mockMvc.perform(post("/api/monitoring/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventCode", is("STEP_3_DEVIG_COMPLETED")))
                .andExpect(jsonPath("$.serviceName", is("data-mining-service")));

        mockMvc.perform(get("/api/executions/{executionId}/logs", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stepNumber", is(3)))
                .andExpect(jsonPath("$[0].eventCode", is("STEP_3_DEVIG_COMPLETED")));

        mockMvc.perform(get("/api/monitoring/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.monitoredMicroservices", hasSize(7)));
    }
}
