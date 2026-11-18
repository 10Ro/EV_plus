package com.porros365.evmining.gateway;

import com.porros365.evmining.gateway.config.GatewaySecurityAndCorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiGatewayApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeActuatorHealthAndPropagateCorrelationIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(GatewaySecurityAndCorsConfig.CORRELATION_HEADER, "test-corr-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(GatewaySecurityAndCorsConfig.CORRELATION_HEADER, "test-corr-123"));
    }
}
