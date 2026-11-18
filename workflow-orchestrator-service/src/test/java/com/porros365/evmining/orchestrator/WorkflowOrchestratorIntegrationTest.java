package com.porros365.evmining.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.domain.ExecutionStatus;
import com.porros365.evmining.common.domain.MethodologyStepCode;
import com.porros365.evmining.common.entity.AlgorithmEntity;
import com.porros365.evmining.common.entity.AlgorithmVersionEntity;
import com.porros365.evmining.common.entity.DatasetEntity;
import com.porros365.evmining.common.entity.DatasetVersionEntity;
import com.porros365.evmining.common.entity.WorkflowEntity;
import com.porros365.evmining.common.entity.WorkflowStepEntity;
import com.porros365.evmining.common.messaging.StepExecutionEventMessage;
import com.porros365.evmining.common.repository.AlgorithmRepository;
import com.porros365.evmining.common.repository.AlgorithmVersionRepository;
import com.porros365.evmining.common.repository.DatasetRepository;
import com.porros365.evmining.common.repository.DatasetVersionRepository;
import com.porros365.evmining.common.repository.WorkflowRepository;
import com.porros365.evmining.common.repository.WorkflowStepRepository;
import com.porros365.evmining.orchestrator.dto.CreateExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:orchestratortestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class WorkflowOrchestratorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowStepRepository workflowStepRepository;

    @Autowired
    private AlgorithmRepository algorithmRepository;

    @Autowired
    private AlgorithmVersionRepository algorithmVersionRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetVersionRepository datasetVersionRepository;

    private UUID workflowId;
    private UUID initialDatasetVersionId;

    @BeforeEach
    void setUp() {
        workflowStepRepository.deleteAll();
        workflowRepository.deleteAll();
        algorithmVersionRepository.deleteAll();
        algorithmRepository.deleteAll();
        datasetVersionRepository.deleteAll();
        datasetRepository.deleteAll();

        AlgorithmEntity alg = new AlgorithmEntity();
        alg.setId(UUID.randomUUID());
        alg.setCode("ALGO_ORCH_TEST");
        alg.setName("Test Algorithm");
        alg.setCategory("MINING");
        alg.setDescription("Desc");
        algorithmRepository.save(alg);

        AlgorithmVersionEntity algVer = new AlgorithmVersionEntity();
        algVer.setId(UUID.randomUUID());
        algVer.setAlgorithmId(alg.getId());
        algVer.setVersion("1.0.0");
        algVer.setImplementationClass("Impl");
        algVer.setCodeCommitHash("v0.5.0");
        algVer.setParameterSchema("{}");
        algVer.setDefaultParameters("{\"devigMethod\":\"MULTIPLICATIVE\"}");
        algorithmVersionRepository.save(algVer);

        WorkflowEntity wf = new WorkflowEntity();
        wf.setId(UUID.randomUUID());
        wf.setCode("PORROS365_EV_CLV_V1");
        wf.setName("Metodología EV+ y CLV (@Porros365)");
        wf.setMethodologyVersion("1.0.0");
        wf.setDescription("6 steps workflow");
        wf.setSourceThreadUrl("https://x.com/Porros365/status/2122822935002132523");
        workflowRepository.save(wf);
        this.workflowId = wf.getId();

        for (MethodologyStepCode stepCode : MethodologyStepCode.values()) {
            WorkflowStepEntity ws = new WorkflowStepEntity();
            ws.setId(UUID.randomUUID());
            ws.setWorkflowId(wf.getId());
            ws.setStepNumber(stepCode.getStepNumber());
            ws.setStepCode(stepCode.name());
            ws.setName(stepCode.getDisplayName());
            ws.setDescription("Description " + stepCode.getStepNumber());
            ws.setObjective("Objective " + stepCode.getStepNumber());
            ws.setTargetService(stepCode.getTargetService());
            ws.setDefaultAlgorithmId(alg.getId());
            ws.setDependsOnStepNumber(stepCode.getDependsOnStepNumber());
            ws.setInputContract("{}");
            ws.setOutputContract("{}");
            ws.setDefaultParameters("{\"minEvThreshold\":0.02}");
            ws.setValidationCriteria("[\"Valid odds\"]");
            ws.setPossibleErrors("[\"INVALID_ODDS\"]");
            ws.setExpectedResults("{\"status\":\"OK\"}");
            workflowStepRepository.save(ws);
        }

        DatasetEntity ds = new DatasetEntity();
        ds.setId(UUID.randomUUID());
        ds.setName("Porros365 Dataset");
        ds.setSourceType("SEED");
        datasetRepository.save(ds);

        DatasetVersionEntity dv = new DatasetVersionEntity();
        dv.setId(UUID.randomUUID());
        dv.setDatasetId(ds.getId());
        dv.setVersionNumber(1);
        dv.setStageType("RAW_IMPORT");
        dv.setRecordCount(10);
        dv.setChecksumSha256("sha");
        dv.setRecordsPayload("[]");
        dv.setSummaryStats("{}");
        datasetVersionRepository.save(dv);
        this.initialDatasetVersionId = dv.getId();
    }

    @Test
    void shouldEnforceStepDependenciesAllowIndependentModeAndOrchestrateFullPipelineRetryAndCancel() throws Exception {
        // 1. Verify workflow steps sheets endpoint returns all 6 technical sheets
        mockMvc.perform(get("/api/workflows/{id}/steps", workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].stepCode", is("ODDS_INGESTION_AND_CONSOLIDATION")))
                .andExpect(jsonPath("$[5].stepCode", is("CLV_AND_PERFORMANCE_AUDIT")));

        // 2. Create execution
        CreateExecutionRequest createReq = new CreateExecutionRequest(
                workflowId,
                "PORROS365_EV_CLV_V1",
                initialDatasetVersionId,
                "Orchestrator Test Execution",
                42L,
                Map.of("devigMethod", "MULTIPLICATIVE", "minEvThreshold", 0.02),
                Map.of()
        );

        MvcResult createdRes = mockMvc.perform(post("/api/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.steps", hasSize(6)))
                .andReturn();

        Map<?, ?> execMap = objectMapper.readValue(createdRes.getResponse().getContentAsString(), Map.class);
        UUID executionId = UUID.fromString(execMap.get("id").toString());

        // 3. Attempting to run Step 3 before Step 2 is COMPLETED must return 409 Conflict (DEPENDENCY_NOT_COMPLETED)
        mockMvc.perform(post("/api/executions/{id}/steps/3/run", executionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("DEPENDENCY_NOT_COMPLETED")));

        // 4. Running Step 3 with ?independentMode=true must be accepted (202 Accepted)
        mockMvc.perform(post("/api/executions/{id}/steps/3/run", executionId)
                        .param("independentMode", "true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("RUNNING")))
                .andExpect(jsonPath("$.independentMode", is(true)));

        // 5. Simulate Step 3 failing and then retry it (attemptNumber increments to 2)
        StepExecutionEventMessage failEvent = new StepExecutionEventMessage(
                executionId, null, 3, "SHARP_DEVIGGING_AND_FAIR_ODDS",
                ExecutionStatus.FAILED, null, null, 20L, Map.of(),
                "DEVIG_ERROR", "Simulated failure", Instant.now()
        );
        mockMvc.perform(post("/api/executions/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failEvent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")));

        mockMvc.perform(post("/api/executions/{id}/steps/3/retry", executionId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("RUNNING")))
                .andExpect(jsonPath("$.attemptNumber", is(2)));

        // 6. Start Full Pipeline execution (Step 1..6) and advance sequentially through all 6 completion events
        mockMvc.perform(post("/api/executions/{id}/run", executionId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionMode", is("FULL_PIPELINE")))
                .andExpect(jsonPath("$.steps[0].status", is("RUNNING")));

        for (int s = 1; s <= 6; s++) {
            MethodologyStepCode code = MethodologyStepCode.fromStepNumber(s);
            StepExecutionEventMessage completedEvent = new StepExecutionEventMessage(
                    executionId, null, s, code.name(),
                    ExecutionStatus.COMPLETED, initialDatasetVersionId, UUID.randomUUID(),
                    35L, Map.of("stepCompleted", s), null, null, Instant.now()
            );
            mockMvc.perform(post("/api/executions/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(completedEvent)))
                    .andExpect(status().isOk());
        }

        // Verify all 6 steps and the parent execution are COMPLETED
        mockMvc.perform(get("/api/executions/{id}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.durationMs", notNullValue()))
                .andExpect(jsonPath("$.steps[5].status", is("COMPLETED")));

        // 7. Verify cancel endpoint
        MvcResult createdRes2 = mockMvc.perform(post("/api/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID execId2 = UUID.fromString(objectMapper.readValue(createdRes2.getResponse().getContentAsString(), Map.class).get("id").toString());

        mockMvc.perform(post("/api/executions/{id}/cancel", execId2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }
}
