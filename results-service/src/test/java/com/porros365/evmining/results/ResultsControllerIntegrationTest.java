package com.porros365.evmining.results;

import com.porros365.evmining.common.domain.ExecutionStatus;
import com.porros365.evmining.common.entity.AlgorithmEntity;
import com.porros365.evmining.common.entity.AlgorithmVersionEntity;
import com.porros365.evmining.common.entity.ConfigurationEntity;
import com.porros365.evmining.common.entity.DatasetEntity;
import com.porros365.evmining.common.entity.DatasetVersionEntity;
import com.porros365.evmining.common.entity.ExecutionEntity;
import com.porros365.evmining.common.entity.MiningResultEntity;
import com.porros365.evmining.common.entity.StepExecutionEntity;
import com.porros365.evmining.common.entity.WorkflowEntity;
import com.porros365.evmining.common.entity.WorkflowStepEntity;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:resultstestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class ResultsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void shouldRetrieveMiningResultsCompareTwoExecutionsAndExportReport() throws Exception {
        DatasetEntity ds = new DatasetEntity();
        ds.setId(UUID.randomUUID());
        ds.setName("Results Test Dataset");
        ds.setSourceType("SEED");
        datasetRepository.save(ds);

        DatasetVersionEntity dv = new DatasetVersionEntity();
        dv.setId(UUID.randomUUID());
        dv.setDatasetId(ds.getId());
        dv.setVersionNumber(1);
        dv.setStageType("STEP6_CLV_AUDITED");
        dv.setRecordCount(10);
        dv.setChecksumSha256("abc");
        dv.setRecordsPayload("[]");
        dv.setSummaryStats("{}");
        datasetVersionRepository.save(dv);

        AlgorithmEntity alg = new AlgorithmEntity();
        alg.setId(UUID.randomUUID());
        alg.setCode("ALGO_RES");
        alg.setName("Alg");
        alg.setCategory("CAT");
        alg.setDescription("Desc");
        algorithmRepository.save(alg);

        AlgorithmVersionEntity algVer = new AlgorithmVersionEntity();
        algVer.setId(UUID.randomUUID());
        algVer.setAlgorithmId(alg.getId());
        algVer.setVersion("1.0.0");
        algVer.setImplementationClass("Cls");
        algVer.setCodeCommitHash("v0.4.0");
        algVer.setParameterSchema("{}");
        algVer.setDefaultParameters("{}");
        algorithmVersionRepository.save(algVer);

        WorkflowEntity wf = new WorkflowEntity();
        wf.setId(UUID.randomUUID());
        wf.setCode("WF_RES");
        wf.setName("WF");
        wf.setMethodologyVersion("1.0.0");
        wf.setDescription("Desc");
        wf.setSourceThreadUrl("url");
        workflowRepository.save(wf);

        WorkflowStepEntity ws4 = new WorkflowStepEntity();
        ws4.setId(UUID.randomUUID());
        ws4.setWorkflowId(wf.getId());
        ws4.setStepNumber(4);
        ws4.setStepCode("EV_PLUS_OPPORTUNITY_MINING");
        ws4.setName("Step 4");
        ws4.setDescription("D");
        ws4.setObjective("O");
        ws4.setTargetService("data-mining-service");
        ws4.setDefaultAlgorithmId(alg.getId());
        ws4.setInputContract("{}");
        ws4.setOutputContract("{}");
        ws4.setDefaultParameters("{}");
        ws4.setValidationCriteria("[]");
        ws4.setPossibleErrors("[]");
        ws4.setExpectedResults("{}");
        workflowStepRepository.save(ws4);

        ConfigurationEntity cfg1 = new ConfigurationEntity();
        cfg1.setId(UUID.randomUUID());
        cfg1.setName("Multiplicative 2%");
        cfg1.setMethodologyVersion("1.0.0");
        cfg1.setGlobalParameters("{\"devigMethod\":\"MULTIPLICATIVE\",\"minEvThreshold\":0.02}");
        cfg1.setStepOverrides("{}");
        cfg1.setConfigHash("h1");
        configurationRepository.save(cfg1);

        ConfigurationEntity cfg2 = new ConfigurationEntity();
        cfg2.setId(UUID.randomUUID());
        cfg2.setName("Power 3%");
        cfg2.setMethodologyVersion("1.0.0");
        cfg2.setGlobalParameters("{\"devigMethod\":\"POWER\",\"minEvThreshold\":0.03}");
        cfg2.setStepOverrides("{}");
        cfg2.setConfigHash("h2");
        configurationRepository.save(cfg2);

        ExecutionEntity exec1 = createExecution(wf.getId(), dv.getId(), cfg1.getId(), ws4.getId(), algVer.getId(), 9, 6.2);
        ExecutionEntity exec2 = createExecution(wf.getId(), dv.getId(), cfg2.getId(), ws4.getId(), algVer.getId(), 7, 7.5);

        mockMvc.perform(get("/api/executions/{id}/results", exec1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stepNumber", is(4)))
                .andExpect(jsonPath("$[0].metricsSummary.evPlusOpportunitiesFound", is(9)));

        mockMvc.perform(get("/api/executions/compare")
                        .param("first", exec1.getId().toString())
                        .param("second", exec2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricDifferences.evPlusOpportunitiesFound.first", is(9.0)))
                .andExpect(jsonPath("$.metricDifferences.evPlusOpportunitiesFound.second", is(7.0)))
                .andExpect(jsonPath("$.metricDifferences.evPlusOpportunitiesFound.delta", is(-2.0)))
                .andExpect(jsonPath("$.summaryConclusion", notNullValue()));

        mockMvc.perform(get("/api/results/executions/{id}/report", exec1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId", is(exec1.getId().toString())))
                .andExpect(jsonPath("$.stepResults", hasSize(1)));
    }

    private ExecutionEntity createExecution(
            UUID wfId, UUID dvId, UUID cfgId, UUID ws4Id, UUID algVerId, int evCount, double avgEvPct) {
        ExecutionEntity exec = new ExecutionEntity();
        exec.setId(UUID.randomUUID());
        exec.setWorkflowId(wfId);
        exec.setInitialDatasetVersionId(dvId);
        exec.setCurrentDatasetVersionId(dvId);
        exec.setConfigurationId(cfgId);
        exec.setMethodologyVersion("1.0.0");
        exec.setCodeVersion("v0.4.0");
        exec.setExecutionMode("FULL_PIPELINE");
        exec.setStatus(ExecutionStatus.COMPLETED);
        executionRepository.save(exec);

        StepExecutionEntity se4 = new StepExecutionEntity();
        se4.setId(UUID.randomUUID());
        se4.setExecutionId(exec.getId());
        se4.setWorkflowStepId(ws4Id);
        se4.setStepNumber(4);
        se4.setStepCode("EV_PLUS_OPPORTUNITY_MINING");
        se4.setAlgorithmVersionId(algVerId);
        se4.setInputDatasetVersionId(dvId);
        se4.setOutputDatasetVersionId(dvId);
        se4.setStatus(ExecutionStatus.COMPLETED);
        se4.setParametersUsed("{}");
        stepExecutionRepository.save(se4);

        MiningResultEntity mr4 = new MiningResultEntity();
        mr4.setId(UUID.randomUUID());
        mr4.setExecutionId(exec.getId());
        mr4.setStepExecutionId(se4.getId());
        mr4.setStepNumber(4);
        mr4.setDatasetVersionId(dvId);
        mr4.setAlgorithmVersionId(algVerId);
        mr4.setCodeVersion("v0.4.0");
        mr4.setRandomSeed(42L);
        mr4.setMetricsSummary("{\"evPlusOpportunitiesFound\":" + evCount + ",\"averageEvPlusOnlyPct\":" + avgEvPct + "}");
        mr4.setAnalyticalPayload("{\"topEvPlusOpportunities\":[]}");
        miningResultRepository.save(mr4);

        return exec;
    }
}
