package com.porros365.evmining.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.domain.ExecutionStatus;
import com.porros365.evmining.common.entity.AlgorithmEntity;
import com.porros365.evmining.common.entity.AlgorithmVersionEntity;
import com.porros365.evmining.common.entity.ConfigurationEntity;
import com.porros365.evmining.common.entity.DatasetEntity;
import com.porros365.evmining.common.entity.DatasetVersionEntity;
import com.porros365.evmining.common.entity.ExecutionEntity;
import com.porros365.evmining.common.entity.WorkflowEntity;
import com.porros365.evmining.common.repository.AlgorithmRepository;
import com.porros365.evmining.common.repository.AlgorithmVersionRepository;
import com.porros365.evmining.common.repository.ConfigurationRepository;
import com.porros365.evmining.common.repository.DatasetRepository;
import com.porros365.evmining.common.repository.DatasetVersionRepository;
import com.porros365.evmining.common.repository.ExecutionRepository;
import com.porros365.evmining.common.repository.WorkflowRepository;
import com.porros365.evmining.orchestrator.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:securitytestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class SecurityJwtAndConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetVersionRepository datasetVersionRepository;

    @Autowired
    private AlgorithmRepository algorithmRepository;

    @Autowired
    private AlgorithmVersionRepository algorithmVersionRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Test
    void shouldAuthenticateWithJwtEnforceRbacAndRejectInvalidTokens() throws Exception {
        // 1. Invalid login credentials must return 401 Unauthorized
        LoginRequest badLogin = new LoginRequest("admin", "WrongPassword123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("INVALID_CREDENTIALS")));

        // 2. Valid Admin login returns signed JWT token with ROLE_ADMIN
        LoginRequest adminLogin = new LoginRequest("admin", "Porros365!2026");
        MvcResult adminRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.role", is("ROLE_ADMIN")))
                .andReturn();

        String adminToken = objectMapper.readValue(adminRes.getResponse().getContentAsString(), Map.class)
                .get("accessToken").toString();

        // 3. Valid Viewer login returns signed JWT token with ROLE_VIEWER
        LoginRequest viewerLogin = new LoginRequest("viewer", "Viewer!2026");
        MvcResult viewerRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewerLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ROLE_VIEWER")))
                .andReturn();

        String viewerToken = objectMapper.readValue(viewerRes.getResponse().getContentAsString(), Map.class)
                .get("accessToken").toString();

        // 4. Accessing /api/admin/security-audit with ROLE_ADMIN token succeeds (200 OK)
        mockMvc.perform(get("/api/admin/security-audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticatedUser", is("admin")))
                .andExpect(jsonPath("$.rbacProtected", is(true)));

        // 5. Accessing /api/admin/security-audit with ROLE_VIEWER token is denied (403 Forbidden)
        mockMvc.perform(get("/api/admin/security-audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // 6. Accessing with tampered/invalid JWT token returns 401 Unauthorized
        mockMvc.perform(get("/api/admin/security-audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.tampered.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("INVALID_JWT_TOKEN")));
    }

    @Test
    void shouldProtectConcurrentExecutionUpdatesWithOptimisticLocking() {
        DatasetEntity ds = new DatasetEntity();
        ds.setId(UUID.randomUUID());
        ds.setName("Concurrency Dataset");
        ds.setSourceType("SEED");
        datasetRepository.save(ds);

        DatasetVersionEntity dv = new DatasetVersionEntity();
        dv.setId(UUID.randomUUID());
        dv.setDatasetId(ds.getId());
        dv.setVersionNumber(1);
        dv.setStageType("RAW_IMPORT");
        dv.setRecordCount(1);
        dv.setChecksumSha256("sha");
        dv.setRecordsPayload("[]");
        dv.setSummaryStats("{}");
        datasetVersionRepository.save(dv);

        AlgorithmEntity alg = new AlgorithmEntity();
        alg.setId(UUID.randomUUID());
        alg.setCode("ALGO_CONC_" + UUID.randomUUID().toString().substring(0, 6));
        alg.setName("Alg");
        alg.setCategory("CAT");
        alg.setDescription("Desc");
        algorithmRepository.save(alg);

        AlgorithmVersionEntity algVer = new AlgorithmVersionEntity();
        algVer.setId(UUID.randomUUID());
        algVer.setAlgorithmId(alg.getId());
        algVer.setVersion("1.0.0");
        algVer.setImplementationClass("Cls");
        algVer.setCodeCommitHash("v0.7.0");
        algVer.setParameterSchema("{}");
        algVer.setDefaultParameters("{}");
        algorithmVersionRepository.save(algVer);

        WorkflowEntity wf = new WorkflowEntity();
        wf.setId(UUID.randomUUID());
        wf.setCode("WF_CONC_" + UUID.randomUUID().toString().substring(0, 6));
        wf.setName("WF");
        wf.setMethodologyVersion("1.0.0");
        wf.setDescription("Desc");
        wf.setSourceThreadUrl("url");
        workflowRepository.save(wf);

        ConfigurationEntity cfg = new ConfigurationEntity();
        cfg.setId(UUID.randomUUID());
        cfg.setName("Cfg");
        cfg.setMethodologyVersion("1.0.0");
        cfg.setGlobalParameters("{}");
        cfg.setStepOverrides("{}");
        cfg.setConfigHash("hash");
        configurationRepository.save(cfg);

        ExecutionEntity exec = new ExecutionEntity();
        exec.setId(UUID.randomUUID());
        exec.setWorkflowId(wf.getId());
        exec.setInitialDatasetVersionId(dv.getId());
        exec.setCurrentDatasetVersionId(dv.getId());
        exec.setConfigurationId(cfg.getId());
        exec.setMethodologyVersion("1.0.0");
        exec.setCodeVersion("v0.7.0");
        exec.setExecutionMode("FULL_PIPELINE");
        executionRepository.saveAndFlush(exec);

        // Two concurrent threads load the same ExecutionEntity snapshot (version = 0)
        ExecutionEntity tx1Copy = executionRepository.findById(exec.getId()).orElseThrow();
        ExecutionEntity tx2Copy = executionRepository.findById(exec.getId()).orElseThrow();

        tx1Copy.setStatus(ExecutionStatus.RUNNING);
        executionRepository.saveAndFlush(tx1Copy);

        // Second concurrent update with stale version must throw OptimisticLockingFailureException
        tx2Copy.setStatus(ExecutionStatus.CANCELLED);
        assertThrows(OptimisticLockingFailureException.class, () -> executionRepository.saveAndFlush(tx2Copy));
    }
}
