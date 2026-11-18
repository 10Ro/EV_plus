package com.porros365.evmining.mining;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import com.porros365.evmining.common.entity.AlgorithmEntity;
import com.porros365.evmining.common.entity.AlgorithmVersionEntity;
import com.porros365.evmining.common.entity.DatasetEntity;
import com.porros365.evmining.common.entity.DatasetVersionEntity;
import com.porros365.evmining.common.entity.ExecutionEntity;
import com.porros365.evmining.common.entity.StepExecutionEntity;
import com.porros365.evmining.common.entity.WorkflowEntity;
import com.porros365.evmining.common.entity.WorkflowStepEntity;
import com.porros365.evmining.common.entity.ConfigurationEntity;
import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.common.repository.AlgorithmRepository;
import com.porros365.evmining.common.repository.AlgorithmVersionRepository;
import com.porros365.evmining.common.repository.ConfigurationRepository;
import com.porros365.evmining.common.repository.DatasetRepository;
import com.porros365.evmining.common.repository.DatasetVersionRepository;
import com.porros365.evmining.common.repository.ExecutionRepository;
import com.porros365.evmining.common.repository.MiningResultRepository;
import com.porros365.evmining.common.repository.StepExecutionRepository;
import com.porros365.evmining.common.repository.WorkflowRepository;
import com.porros365.evmining.common.repository.WorkflowStepRepository;
import com.porros365.evmining.common.util.HashUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:miningtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class DataMiningControllerIntegrationTest {

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
    private WorkflowStepRepository workflowStepRepository;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private StepExecutionRepository stepExecutionRepository;

    @Autowired
    private MiningResultRepository miningResultRepository;

    @Test
    void shouldExecuteSteps3To6SequentiallyAndPersistMiningResults() throws Exception {
        DatasetEntity ds = new DatasetEntity();
        ds.setId(UUID.randomUUID());
        ds.setName("Mining Integration Test Dataset");
        ds.setSourceType("SEED");
        datasetRepository.save(ds);

        List<MarketOddsRecordDto> records = List.of(
                new MarketOddsRecordDto(
                        "MONZA_ATALANTA", "FOOTBALL", "Serie A", "Monza vs Atalanta",
                        "ASIAN_HANDICAP", "-0.25", "Atalanta -0.25", "SINGBET",
                        1.83, 2.09, null, "BET365", 1.95, 1.71, "WON",
                        null, null, null, null, null, null, null, null, null, null, null, null, null
                ),
                new MarketOddsRecordDto(
                        "BOSNIA_ROMANIA", "FOOTBALL", "Nations League", "Bosnia vs Rumania",
                        "MATCH_ODDS_1X2", "1X2", "Gana Rumania", "BETFAIR_EXCHANGE",
                        4.08, 1.92, 3.55, "PAF", 4.60, 3.44, "LOST",
                        null, null, null, null, null, null, null, null, null, null, null, null, null
                )
        );

        String recordsJson = objectMapper.writeValueAsString(records);
        DatasetVersionEntity currentVersion = new DatasetVersionEntity();
        currentVersion.setId(UUID.randomUUID());
        currentVersion.setDatasetId(ds.getId());
        currentVersion.setVersionNumber(2);
        currentVersion.setStageType("STEP2_CLEANED");
        currentVersion.setRecordCount(2);
        currentVersion.setChecksumSha256(HashUtil.sha256Hex(recordsJson));
        currentVersion.setRecordsPayload(recordsJson);
        currentVersion.setSummaryStats("{}");
        datasetVersionRepository.save(currentVersion);

        AlgorithmEntity alg = new AlgorithmEntity();
        alg.setId(UUID.randomUUID());
        alg.setCode("ALGO_TEST");
        alg.setName("Test Mining Algorithm");
        alg.setCategory("MINING");
        alg.setDescription("Desc");
        algorithmRepository.save(alg);

        AlgorithmVersionEntity algVer = new AlgorithmVersionEntity();
        algVer.setId(UUID.randomUUID());
        algVer.setAlgorithmId(alg.getId());
        algVer.setVersion("1.0.0");
        algVer.setImplementationClass("TestClass");
        algVer.setCodeCommitHash("v0.4.0");
        algVer.setParameterSchema("{}");
        algVer.setDefaultParameters("{}");
        algorithmVersionRepository.save(algVer);

        WorkflowEntity wf = new WorkflowEntity();
        wf.setId(UUID.randomUUID());
        wf.setCode("WF_TEST");
        wf.setName("Test Workflow");
        wf.setMethodologyVersion("1.0.0");
        wf.setDescription("Desc");
        wf.setSourceThreadUrl("https://x.com/Porros365/status/2122822935002132523");
        workflowRepository.save(wf);

        ConfigurationEntity cfg = new ConfigurationEntity();
        cfg.setId(UUID.randomUUID());
        cfg.setName("Test Config");
        cfg.setMethodologyVersion("1.0.0");
        cfg.setGlobalParameters("{}");
        cfg.setStepOverrides("{}");
        cfg.setConfigHash("hash");
        configurationRepository.save(cfg);

        ExecutionEntity exec = new ExecutionEntity();
        exec.setId(UUID.randomUUID());
        exec.setWorkflowId(wf.getId());
        exec.setInitialDatasetVersionId(currentVersion.getId());
        exec.setCurrentDatasetVersionId(currentVersion.getId());
        exec.setConfigurationId(cfg.getId());
        exec.setMethodologyVersion("1.0.0");
        exec.setCodeVersion("v0.4.0");
        exec.setExecutionMode("FULL_PIPELINE");
        executionRepository.save(exec);

        UUID currentDvId = currentVersion.getId();
        String[] stepCodes = {
                "", "", "",
                "SHARP_DEVIGGING_AND_FAIR_ODDS",
                "EV_PLUS_OPPORTUNITY_MINING",
                "STAKE_AND_VOLUME_SIMULATION",
                "CLV_AND_PERFORMANCE_AUDIT"
        };

        for (int stepNum = 3; stepNum <= 6; stepNum++) {
            WorkflowStepEntity ws = new WorkflowStepEntity();
            ws.setId(UUID.randomUUID());
            ws.setWorkflowId(wf.getId());
            ws.setStepNumber(stepNum);
            ws.setStepCode(stepCodes[stepNum]);
            ws.setName("Step " + stepNum);
            ws.setDescription("Desc");
            ws.setObjective("Obj");
            ws.setTargetService("data-mining-service");
            ws.setDefaultAlgorithmId(alg.getId());
            ws.setInputContract("{}");
            ws.setOutputContract("{}");
            ws.setDefaultParameters("{}");
            ws.setValidationCriteria("[]");
            ws.setPossibleErrors("[]");
            ws.setExpectedResults("{}");
            workflowStepRepository.save(ws);

            StepExecutionEntity se = new StepExecutionEntity();
            se.setId(UUID.randomUUID());
            se.setExecutionId(exec.getId());
            se.setWorkflowStepId(ws.getId());
            se.setStepNumber(stepNum);
            se.setStepCode(stepCodes[stepNum]);
            se.setAlgorithmVersionId(algVer.getId());
            se.setInputDatasetVersionId(currentDvId);
            se.setParametersUsed("{}");
            stepExecutionRepository.save(se);

            StepExecutionCommandMessage cmd = new StepExecutionCommandMessage(
                    UUID.randomUUID(),
                    exec.getId(),
                    se.getId(),
                    stepNum,
                    stepCodes[stepNum],
                    algVer.getId(),
                    currentDvId,
                    42L,
                    false,
                    Map.of("simulationTrials", 1000, "projectionVolumeBets", 500),
                    Instant.now()
            );

            MvcResult mvcRes = mockMvc.perform(post("/api/mining/execute-step")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cmd)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COMPLETED")))
                    .andExpect(jsonPath("$.outputDatasetVersionId", notNullValue()))
                    .andExpect(jsonPath("$.miningResultId", notNullValue()))
                    .andReturn();

            Map<?, ?> eventMap = objectMapper.readValue(mvcRes.getResponse().getContentAsString(), Map.class);
            currentDvId = UUID.fromString(eventMap.get("outputDatasetVersionId").toString());
        }

        assertEquals(4, miningResultRepository.findByExecutionIdOrderByStepNumberAsc(exec.getId()).size());
    }
}
