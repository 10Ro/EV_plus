package com.porros365.evmining.mining.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.domain.ExecutionStatus;
import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import com.porros365.evmining.common.entity.AlgorithmEntity;
import com.porros365.evmining.common.entity.AlgorithmVersionEntity;
import com.porros365.evmining.common.entity.DatasetVersionEntity;
import com.porros365.evmining.common.entity.MiningResultEntity;
import com.porros365.evmining.common.messaging.DistributedLogPublisher;
import com.porros365.evmining.common.messaging.MessagingConstants;
import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.common.messaging.StepExecutionEventMessage;
import com.porros365.evmining.common.repository.AlgorithmRepository;
import com.porros365.evmining.common.repository.AlgorithmVersionRepository;
import com.porros365.evmining.common.repository.DatasetVersionRepository;
import com.porros365.evmining.common.repository.MiningResultRepository;
import com.porros365.evmining.common.util.HashUtil;
import com.porros365.evmining.mining.engine.ClvPerformanceAuditEngine;
import com.porros365.evmining.mining.engine.EvPlusOpportunityEngine;
import com.porros365.evmining.mining.engine.SharpDeviggingEngine;
import com.porros365.evmining.mining.engine.StakeAndMonteCarloEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DataMiningExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DataMiningExecutionService.class);
    private static final String CODE_VERSION = "v0.4.0";

    private final DatasetVersionRepository datasetVersionRepository;
    private final MiningResultRepository miningResultRepository;
    private final AlgorithmRepository algorithmRepository;
    private final AlgorithmVersionRepository algorithmVersionRepository;
    private final SharpDeviggingEngine sharpDeviggingEngine;
    private final EvPlusOpportunityEngine evPlusOpportunityEngine;
    private final StakeAndMonteCarloEngine stakeAndMonteCarloEngine;
    private final ClvPerformanceAuditEngine clvPerformanceAuditEngine;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final DistributedLogPublisher logPublisher;

    public DataMiningExecutionService(
            DatasetVersionRepository datasetVersionRepository,
            MiningResultRepository miningResultRepository,
            AlgorithmRepository algorithmRepository,
            AlgorithmVersionRepository algorithmVersionRepository,
            SharpDeviggingEngine sharpDeviggingEngine,
            EvPlusOpportunityEngine evPlusOpportunityEngine,
            StakeAndMonteCarloEngine stakeAndMonteCarloEngine,
            ClvPerformanceAuditEngine clvPerformanceAuditEngine,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            DistributedLogPublisher logPublisher) {
        this.datasetVersionRepository = datasetVersionRepository;
        this.miningResultRepository = miningResultRepository;
        this.algorithmRepository = algorithmRepository;
        this.algorithmVersionRepository = algorithmVersionRepository;
        this.sharpDeviggingEngine = sharpDeviggingEngine;
        this.evPlusOpportunityEngine = evPlusOpportunityEngine;
        this.stakeAndMonteCarloEngine = stakeAndMonteCarloEngine;
        this.clvPerformanceAuditEngine = clvPerformanceAuditEngine;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.logPublisher = logPublisher;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCatalogAlgorithms() {
        List<AlgorithmEntity> algorithms = algorithmRepository.findAll();
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (AlgorithmEntity alg : algorithms) {
            List<AlgorithmVersionEntity> versions = algorithmVersionRepository.findByAlgorithmIdOrderByCreatedAtDesc(alg.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", alg.getId());
            item.put("code", alg.getCode());
            item.put("name", alg.getName());
            item.put("category", alg.getCategory());
            item.put("description", alg.getDescription());
            item.put("versions", versions);
            catalog.add(item);
        }
        return catalog;
    }

    @Transactional
    public StepExecutionEventMessage executeMiningStep(StepExecutionCommandMessage command) {
        long startTime = System.currentTimeMillis();
        int stepNum = command.stepNumber();
        try {
            logPublisher.publishLog(
                    command.executionId(),
                    command.stepExecutionId(),
                    stepNum,
                    "INFO",
                    "STEP_" + stepNum + "_MINING_STARTED",
                    "Iniciando ejecucion de Data Mining del Paso " + stepNum + " (" + command.stepCode() + ")",
                    command.parameters()
            );

            DatasetVersionEntity inputVersion = datasetVersionRepository.findById(command.inputDatasetVersionId())
                    .orElseThrow(() -> new IllegalArgumentException("Input DatasetVersion not found: " + command.inputDatasetVersionId()));

            List<MarketOddsRecordDto> inputRecords = deserializeRecords(inputVersion.getRecordsPayload());

            List<MarketOddsRecordDto> outputRecords;
            String outputStageType;
            Map<String, Object> metricsSummary;
            Map<String, Object> analyticalPayload = new LinkedHashMap<>();

            long seed = command.randomSeed() != null ? command.randomSeed() : 42L;

            switch (stepNum) {
                case 3 -> {
                    String devigMethod = getStringParam(command.parameters(), "devigMethod", "MULTIPLICATIVE");
                    SharpDeviggingEngine.DevigBatchResult res = sharpDeviggingEngine.executeDevigging(inputRecords, devigMethod);
                    outputRecords = res.records();
                    outputStageType = "STEP3_DEVIGGED";
                    metricsSummary = res.metricsSummary();
                    analyticalPayload.put("deviggedMarkets", outputRecords);
                }
                case 4 -> {
                    double minEv = getDoubleParam(command.parameters(), "minEvThreshold", 0.02);
                    double maxEv = getDoubleParam(command.parameters(), "maxEvThreshold", 0.35);
                    EvPlusOpportunityEngine.EvMiningBatchResult res = evPlusOpportunityEngine.mineEvPlusOpportunities(inputRecords, minEv, maxEv);
                    outputRecords = res.records();
                    outputStageType = "STEP4_EV_MINED";
                    metricsSummary = res.metricsSummary();
                    analyticalPayload.put("topEvPlusOpportunities", res.topOpportunities());
                }
                case 5 -> {
                    String strategy = getStringParam(command.parameters(), "stakingStrategy", "FLAT_UNIT");
                    double unitStake = getDoubleParam(command.parameters(), "defaultUnitStake", 1.0);
                    double kellyFraction = getDoubleParam(command.parameters(), "kellyFraction", 0.15);
                    double maxStake = getDoubleParam(command.parameters(), "maxStakeUnits", 2.0);
                    int projVolume = getIntParam(command.parameters(), "projectionVolumeBets", 500);
                    int trials = getIntParam(command.parameters(), "simulationTrials", 5000);
                    StakeAndMonteCarloEngine.StakeSimulationBatchResult res = stakeAndMonteCarloEngine.executeStakingAndMonteCarlo(
                            inputRecords, strategy, unitStake, kellyFraction, maxStake, projVolume, trials, seed
                    );
                    outputRecords = res.records();
                    outputStageType = "STEP5_STAKED";
                    metricsSummary = res.metricsSummary();
                    analyticalPayload.put("projectionTrajectory", res.projectionTrajectory());
                }
                case 6 -> {
                    double alpha = getDoubleParam(command.parameters(), "significanceLevelAlpha", 0.05);
                    ClvPerformanceAuditEngine.ClvAuditBatchResult res = clvPerformanceAuditEngine.executeClvAudit(inputRecords, alpha);
                    outputRecords = res.records();
                    outputStageType = "STEP6_CLV_AUDITED";
                    metricsSummary = res.metricsSummary();
                    analyticalPayload.put("pickAuditBreakdown", res.pickAuditBreakdown());
                }
                default -> throw new IllegalArgumentException("Unsupported mining stepNumber: " + stepNum);
            }

            int nextVersionNum = datasetVersionRepository
                    .findFirstByDatasetIdOrderByVersionNumberDesc(inputVersion.getDatasetId())
                    .map(v -> v.getVersionNumber() + 1)
                    .orElse(inputVersion.getVersionNumber() + 1);

            String recordsJson = objectMapper.writeValueAsString(outputRecords);
            String metricsJson = objectMapper.writeValueAsString(metricsSummary);
            String payloadJson = objectMapper.writeValueAsString(analyticalPayload);

            DatasetVersionEntity outputVersion = new DatasetVersionEntity();
            outputVersion.setId(UUID.randomUUID());
            outputVersion.setDatasetId(inputVersion.getDatasetId());
            outputVersion.setVersionNumber(nextVersionNum);
            outputVersion.setStageType(outputStageType);
            outputVersion.setRecordCount(outputRecords.size());
            outputVersion.setChecksumSha256(HashUtil.sha256Hex(recordsJson));
            outputVersion.setSchemaVersion("1.0");
            outputVersion.setRecordsPayload(recordsJson);
            outputVersion.setSummaryStats(metricsJson);
            outputVersion.setParentVersionId(inputVersion.getId());
            outputVersion.setCreatedAt(Instant.now());
            datasetVersionRepository.save(outputVersion);

            MiningResultEntity miningResult = new MiningResultEntity();
            miningResult.setId(UUID.randomUUID());
            miningResult.setExecutionId(command.executionId());
            miningResult.setStepExecutionId(command.stepExecutionId());
            miningResult.setStepNumber(stepNum);
            miningResult.setDatasetVersionId(outputVersion.getId());
            miningResult.setAlgorithmVersionId(command.algorithmVersionId());
            miningResult.setCodeVersion(CODE_VERSION);
            miningResult.setRandomSeed(seed);
            miningResult.setMetricsSummary(metricsJson);
            miningResult.setAnalyticalPayload(payloadJson);
            miningResult.setCreatedAt(Instant.now());
            miningResultRepository.save(miningResult);

            long durationMs = Math.max(1L, System.currentTimeMillis() - startTime);
            logPublisher.publishLog(
                    command.executionId(),
                    command.stepExecutionId(),
                    stepNum,
                    "INFO",
                    "STEP_" + stepNum + "_MINING_COMPLETED",
                    "Paso " + stepNum + " (" + command.stepCode() + ") completado en " + durationMs + " ms",
                    metricsSummary
            );

            return new StepExecutionEventMessage(
                    command.executionId(),
                    command.stepExecutionId(),
                    stepNum,
                    command.stepCode(),
                    ExecutionStatus.COMPLETED,
                    outputVersion.getId(),
                    miningResult.getId(),
                    durationMs,
                    metricsSummary,
                    null,
                    null,
                    Instant.now()
            );
        } catch (Exception ex) {
            long durationMs = Math.max(1L, System.currentTimeMillis() - startTime);
            log.error("Mining step {} failed for execution {}: {}", stepNum, command.executionId(), ex.getMessage());
            logPublisher.publishLog(
                    command.executionId(),
                    command.stepExecutionId(),
                    stepNum,
                    "ERROR",
                    "STEP_" + stepNum + "_MINING_FAILED",
                    ex.getMessage(),
                    Map.of()
            );
            return new StepExecutionEventMessage(
                    command.executionId(),
                    command.stepExecutionId(),
                    stepNum,
                    command.stepCode(),
                    ExecutionStatus.FAILED,
                    null,
                    null,
                    durationMs,
                    Map.of(),
                    "MINING_STEP_" + stepNum + "_ERROR",
                    ex.getMessage(),
                    Instant.now()
            );
        }
    }

    @RabbitListener(queues = MessagingConstants.QUEUE_MINING_STEPS)
    public void onMiningStepCommand(StepExecutionCommandMessage command) {
        StepExecutionEventMessage resultEvent = executeMiningStep(command);
        rabbitTemplate.convertAndSend(
                MessagingConstants.EXCHANGE_NAME,
                MessagingConstants.ROUTING_EVENT_STATUS,
                resultEvent
        );
    }

    private List<MarketOddsRecordDto> deserializeRecords(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<MarketOddsRecordDto>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize dataset records", e);
        }
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultVal) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return defaultVal;
        }
        return params.get(key).toString();
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultVal) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return defaultVal;
        }
        Object val = params.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(val.toString());
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultVal) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return defaultVal;
        }
        Object val = params.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(val.toString());
    }
}
