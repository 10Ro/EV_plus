package com.porros365.evmining.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.domain.ExecutionStatus;
import com.porros365.evmining.common.entity.AlgorithmEntity;
import com.porros365.evmining.common.entity.AlgorithmVersionEntity;
import com.porros365.evmining.common.entity.ConfigurationEntity;
import com.porros365.evmining.common.entity.DatasetVersionEntity;
import com.porros365.evmining.common.entity.ExecutionEntity;
import com.porros365.evmining.common.entity.StepExecutionEntity;
import com.porros365.evmining.common.entity.WorkflowEntity;
import com.porros365.evmining.common.entity.WorkflowStepEntity;
import com.porros365.evmining.common.messaging.DistributedLogPublisher;
import com.porros365.evmining.common.messaging.MessagingConstants;
import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.common.messaging.StepExecutionEventMessage;
import com.porros365.evmining.common.repository.AlgorithmRepository;
import com.porros365.evmining.common.repository.AlgorithmVersionRepository;
import com.porros365.evmining.common.repository.ConfigurationRepository;
import com.porros365.evmining.common.repository.DatasetVersionRepository;
import com.porros365.evmining.common.repository.ExecutionRepository;
import com.porros365.evmining.common.repository.StepExecutionRepository;
import com.porros365.evmining.common.repository.WorkflowRepository;
import com.porros365.evmining.common.repository.WorkflowStepRepository;
import com.porros365.evmining.common.util.HashUtil;
import com.porros365.evmining.orchestrator.dto.CreateExecutionRequest;
import com.porros365.evmining.orchestrator.dto.CreateWorkflowRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrationService.class);
    private static final String CODE_VERSION = "v0.5.0";
    private static final String DEFAULT_WORKFLOW_CODE = "PORROS365_EV_CLV_V1";

    private final WorkflowRepository workflowRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final AlgorithmRepository algorithmRepository;
    private final AlgorithmVersionRepository algorithmVersionRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final ConfigurationRepository configurationRepository;
    private final ExecutionRepository executionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DistributedLogPublisher logPublisher;
    private final ObjectMapper objectMapper;

    public WorkflowOrchestrationService(
            WorkflowRepository workflowRepository,
            WorkflowStepRepository workflowStepRepository,
            AlgorithmRepository algorithmRepository,
            AlgorithmVersionRepository algorithmVersionRepository,
            DatasetVersionRepository datasetVersionRepository,
            ConfigurationRepository configurationRepository,
            ExecutionRepository executionRepository,
            StepExecutionRepository stepExecutionRepository,
            RabbitTemplate rabbitTemplate,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            DistributedLogPublisher logPublisher,
            ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.workflowStepRepository = workflowStepRepository;
        this.algorithmRepository = algorithmRepository;
        this.algorithmVersionRepository = algorithmVersionRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.configurationRepository = configurationRepository;
        this.executionRepository = executionRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.logPublisher = logPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<WorkflowEntity> listWorkflows() {
        return workflowRepository.findAll();
    }

    @Transactional
    public WorkflowEntity createWorkflow(CreateWorkflowRequest request) {
        WorkflowEntity wf = new WorkflowEntity();
        wf.setId(UUID.randomUUID());
        wf.setCode(request.code().trim().toUpperCase());
        wf.setName(request.name().trim());
        wf.setMethodologyVersion(request.methodologyVersion().trim());
        wf.setDescription(request.description() != null ? request.description() : "");
        wf.setSourceThreadUrl(request.sourceThreadUrl() != null
                ? request.sourceThreadUrl()
                : "https://x.com/Porros365/status/2122822935002132523");
        wf.setActive(true);
        wf.setCreatedAt(Instant.now());
        return workflowRepository.save(wf);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getWorkflowStepsSheets(UUID workflowId) {
        List<WorkflowStepEntity> steps = workflowStepRepository.findByWorkflowIdOrderByStepNumberAsc(workflowId);
        List<Map<String, Object>> sheets = new ArrayList<>(steps.size());
        for (WorkflowStepEntity s : steps) {
            AlgorithmEntity alg = algorithmRepository.findById(s.getDefaultAlgorithmId()).orElse(null);
            Map<String, Object> sheet = new LinkedHashMap<>();
            sheet.put("id", s.getId());
            sheet.put("workflowId", s.getWorkflowId());
            sheet.put("stepNumber", s.getStepNumber());
            sheet.put("stepCode", s.getStepCode());
            sheet.put("name", s.getName());
            sheet.put("description", s.getDescription());
            sheet.put("objective", s.getObjective());
            sheet.put("targetService", s.getTargetService());
            sheet.put("defaultAlgorithmId", s.getDefaultAlgorithmId());
            sheet.put("algorithmCode", alg != null ? alg.getCode() : "UNKNOWN");
            sheet.put("algorithmName", alg != null ? alg.getName() : "Unknown Algorithm");
            sheet.put("dependsOnStepNumber", s.getDependsOnStepNumber());
            sheet.put("inputContract", parseJsonObject(s.getInputContract()));
            sheet.put("outputContract", parseJsonObject(s.getOutputContract()));
            sheet.put("defaultParameters", parseJsonObject(s.getDefaultParameters()));
            sheet.put("validationCriteria", parseJsonObject(s.getValidationCriteria()));
            sheet.put("possibleErrors", parseJsonObject(s.getPossibleErrors()));
            sheet.put("expectedResults", parseJsonObject(s.getExpectedResults()));
            sheets.add(sheet);
        }
        return sheets;
    }

    @Transactional
    public Map<String, Object> createExecution(CreateExecutionRequest request) {
        WorkflowEntity workflow;
        if (request.workflowId() != null) {
            workflow = workflowRepository.findById(request.workflowId())
                    .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + request.workflowId()));
        } else {
            String code = (request.workflowCode() != null && !request.workflowCode().isBlank())
                    ? request.workflowCode()
                    : DEFAULT_WORKFLOW_CODE;
            workflow = workflowRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Workflow not found by code: " + code));
        }

        DatasetVersionEntity initialVersion = datasetVersionRepository.findById(request.datasetVersionId())
                .orElseThrow(() -> new IllegalArgumentException("Initial DatasetVersion not found: " + request.datasetVersionId()));

        List<WorkflowStepEntity> steps = workflowStepRepository.findByWorkflowIdOrderByStepNumberAsc(workflow.getId());
        if (steps.isEmpty()) {
            throw new IllegalStateException("Workflow has no configured steps: " + workflow.getCode());
        }

        long seed = request.randomSeed() != null ? request.randomSeed() : 42L;
        Map<String, Object> globalParams = request.globalParameters() != null ? request.globalParameters() : Map.of();
        Map<String, Map<String, Object>> stepOverrides = request.stepOverrides() != null ? request.stepOverrides() : Map.of();

        String globalParamsJson = toJson(globalParams);
        String stepOverridesJson = toJson(stepOverrides);

        ConfigurationEntity config = new ConfigurationEntity();
        config.setId(UUID.randomUUID());
        config.setName(request.configurationName() != null && !request.configurationName().isBlank()
                ? request.configurationName()
                : "Config " + workflow.getCode() + " (seed=" + seed + ")");
        config.setMethodologyVersion(workflow.getMethodologyVersion());
        config.setRandomSeed(seed);
        config.setGlobalParameters(globalParamsJson);
        config.setStepOverrides(stepOverridesJson);
        config.setConfigHash(HashUtil.sha256Hex(globalParamsJson + "|" + stepOverridesJson + "|" + seed));
        config.setCreatedAt(Instant.now());
        configurationRepository.save(config);

        ExecutionEntity execution = new ExecutionEntity();
        execution.setId(UUID.randomUUID());
        execution.setWorkflowId(workflow.getId());
        execution.setInitialDatasetVersionId(initialVersion.getId());
        execution.setCurrentDatasetVersionId(initialVersion.getId());
        execution.setConfigurationId(config.getId());
        execution.setMethodologyVersion(workflow.getMethodologyVersion());
        execution.setCodeVersion(CODE_VERSION);
        execution.setExecutionMode("SINGLE_STEP");
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setCreatedAt(Instant.now());
        executionRepository.save(execution);

        for (WorkflowStepEntity ws : steps) {
            AlgorithmVersionEntity algVer = algorithmVersionRepository
                    .findFirstByAlgorithmIdOrderByCreatedAtDesc(ws.getDefaultAlgorithmId())
                    .orElseThrow(() -> new IllegalStateException("No AlgorithmVersion for algorithm " + ws.getDefaultAlgorithmId()));

            Map<String, Object> mergedParams = mergeParameters(
                    parseJsonMap(ws.getDefaultParameters()),
                    globalParams,
                    stepOverrides.get(String.valueOf(ws.getStepNumber()))
            );

            StepExecutionEntity se = new StepExecutionEntity();
            se.setId(UUID.randomUUID());
            se.setExecutionId(execution.getId());
            se.setWorkflowStepId(ws.getId());
            se.setStepNumber(ws.getStepNumber());
            se.setStepCode(ws.getStepCode());
            se.setAlgorithmVersionId(algVer.getId());
            se.setInputDatasetVersionId(ws.getStepNumber() == 1 ? initialVersion.getId() : null);
            se.setStatus(ExecutionStatus.PENDING);
            se.setIndependentMode(false);
            se.setAttemptNumber(1);
            se.setRandomSeed(seed);
            se.setParametersUsed(toJson(mergedParams));
            stepExecutionRepository.save(se);
        }

        logPublisher.publishLog(
                execution.getId(),
                null,
                null,
                "INFO",
                "EXECUTION_CREATED",
                "Ejecucion creada con 6 pasos en estado PENDING para datasetVersion " + initialVersion.getId(),
                Map.of("workflowCode", workflow.getCode(), "randomSeed", seed)
        );

        return getExecutionDetails(execution.getId());
    }

    /**
     * Lanza la ejecucion secuencial completa del pipeline (Pasos 1 a 6).
     * Arranca por el primer paso que no este COMPLETED y encadena los siguientes al recibir cada StepExecutionEventMessage.
     */
    @Transactional
    public Map<String, Object> startFullPipelineExecution(UUID executionId) {
        ExecutionEntity execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        clearCancelFlagInRedis(executionId);
        execution.setExecutionMode("FULL_PIPELINE");
        execution.setStatus(ExecutionStatus.RUNNING);
        if (execution.getStartedAt() == null) {
            execution.setStartedAt(Instant.now());
        }
        execution.setErrorMessage(null);
        executionRepository.save(execution);

        List<StepExecutionEntity> steps = stepExecutionRepository.findByExecutionIdOrderByStepNumberAsc(executionId);
        StepExecutionEntity nextStep = steps.stream()
                .filter(s -> s.getStatus() != ExecutionStatus.COMPLETED)
                .findFirst()
                .orElse(steps.get(0));

        UUID inputDvId = resolveInputDatasetVersionForStep(execution, steps, nextStep.getStepNumber(), false);
        dispatchStepExecution(execution, nextStep, inputDvId, false);

        return getExecutionDetails(executionId);
    }

    /**
     * Ejecuta un paso individual verificando sus dependencias previas (salvo que independentMode == true).
     */
    @Transactional
    public Map<String, Object> runIndividualStep(UUID executionId, int stepNumber, boolean independentMode) {
        ExecutionEntity execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        List<StepExecutionEntity> steps = stepExecutionRepository.findByExecutionIdOrderByStepNumberAsc(executionId);
        StepExecutionEntity targetStep = steps.stream()
                .filter(s -> s.getStepNumber() == stepNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step number " + stepNumber + " not found in execution " + executionId));

        WorkflowStepEntity ws = workflowStepRepository.findById(targetStep.getWorkflowStepId())
                .orElseThrow(() -> new IllegalStateException("WorkflowStep metadata missing"));

        if (ws.getDependsOnStepNumber() != null && !independentMode) {
            int requiredStepNum = ws.getDependsOnStepNumber();
            StepExecutionEntity parentStep = steps.stream()
                    .filter(s -> s.getStepNumber() == requiredStepNum)
                    .findFirst()
                    .orElseThrow();

            if (parentStep.getStatus() != ExecutionStatus.COMPLETED) {
                throw new DependencyNotCompletedException(
                        "DEPENDENCY_NOT_COMPLETED: Step " + stepNumber + " (" + targetStep.getStepCode()
                                + ") depends on Step " + requiredStepNum + " (" + parentStep.getStepCode()
                                + ") which is currently in state " + parentStep.getStatus()
                                + ". Complete Step " + requiredStepNum + " first or enable independentMode=true."
                );
            }
        }

        clearCancelFlagInRedis(executionId);
        execution.setExecutionMode(independentMode ? "INDEPENDENT_STEP" : "SINGLE_STEP");
        execution.setStatus(ExecutionStatus.RUNNING);
        if (execution.getStartedAt() == null) {
            execution.setStartedAt(Instant.now());
        }
        execution.setErrorMessage(null);
        executionRepository.save(execution);

        UUID inputDvId = resolveInputDatasetVersionForStep(execution, steps, stepNumber, independentMode);
        dispatchStepExecution(execution, targetStep, inputDvId, independentMode);

        return toStepExecutionMap(targetStep);
    }

    /**
     * Pausa o cancela una ejecucion en curso, registrando la bandera cooperativa en Redis.
     */
    @Transactional
    public Map<String, Object> cancelExecution(UUID executionId) {
        ExecutionEntity execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        setCancelFlagInRedis(executionId);
        execution.setStatus(ExecutionStatus.CANCELLED);
        execution.setFinishedAt(Instant.now());
        if (execution.getStartedAt() != null) {
            execution.setDurationMs(Math.max(1L, Duration.between(execution.getStartedAt(), execution.getFinishedAt()).toMillis()));
        }
        executionRepository.save(execution);

        List<StepExecutionEntity> steps = stepExecutionRepository.findByExecutionIdOrderByStepNumberAsc(executionId);
        for (StepExecutionEntity se : steps) {
            if (se.getStatus() == ExecutionStatus.RUNNING) {
                se.setStatus(ExecutionStatus.CANCELLED);
                se.setFinishedAt(Instant.now());
                stepExecutionRepository.save(se);
            }
        }

        logPublisher.publishLog(
                executionId,
                null,
                null,
                "WARN",
                "EXECUTION_CANCELLED",
                "Ejecucion cancelada por solicitud del usuario",
                Map.of()
        );

        return getExecutionDetails(executionId);
    }

    /**
     * Reintenta un paso fallido o cancelado incrementando attemptNumber.
     */
    @Transactional
    public Map<String, Object> retryFailedStep(UUID executionId, int stepNumber) {
        ExecutionEntity execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        StepExecutionEntity targetStep = stepExecutionRepository.findByExecutionIdAndStepNumber(executionId, stepNumber)
                .orElseThrow(() -> new IllegalArgumentException("Step " + stepNumber + " not found"));

        if (targetStep.getStatus() != ExecutionStatus.FAILED && targetStep.getStatus() != ExecutionStatus.CANCELLED) {
            throw new IllegalArgumentException("STEP_NOT_RETRYABLE: Step " + stepNumber + " is in state "
                    + targetStep.getStatus() + " (only FAILED or CANCELLED steps can be retried)");
        }

        targetStep.setAttemptNumber(targetStep.getAttemptNumber() + 1);
        targetStep.setErrorCode(null);
        targetStep.setErrorMessage(null);
        stepExecutionRepository.save(targetStep);

        return runIndividualStep(executionId, stepNumber, targetStep.isIndependentMode());
    }

    /**
     * Procesa el evento de resultado enviado por Data Ingestion, Data Processing o Data Mining.
     * Si el modo de la ejecucion es FULL_PIPELINE y el paso actual termino COMPLETED, lanza automaticamente el siguiente paso.
     */
    @RabbitListener(queues = MessagingConstants.QUEUE_ORCHESTRATOR_EVENTS)
    @Transactional
    public void onStepExecutionStatusEvent(StepExecutionEventMessage event) {
        handleStepExecutionEvent(event);
    }

    @Transactional
    public Map<String, Object> handleStepExecutionEvent(StepExecutionEventMessage event) {
        ExecutionEntity execution = executionRepository.findById(event.executionId())
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + event.executionId()));

        StepExecutionEntity step = stepExecutionRepository
                .findByExecutionIdAndStepNumber(event.executionId(), event.stepNumber())
                .orElseThrow(() -> new IllegalArgumentException("StepExecution not found for step " + event.stepNumber()));

        if (execution.getStatus() == ExecutionStatus.CANCELLED || isCancelledInRedis(execution.getId())) {
            step.setStatus(ExecutionStatus.CANCELLED);
            step.setFinishedAt(Instant.now());
            stepExecutionRepository.save(step);
            return getExecutionDetails(execution.getId());
        }

        step.setStatus(event.status());
        step.setOutputDatasetVersionId(event.outputDatasetVersionId());
        step.setOutputSummary(toJson(event.outputSummary() != null ? event.outputSummary() : Map.of()));
        step.setErrorCode(event.errorCode());
        step.setErrorMessage(event.errorMessage());
        step.setFinishedAt(event.finishedAt() != null ? event.finishedAt() : Instant.now());
        step.setDurationMs(event.durationMs());
        stepExecutionRepository.save(step);

        if (event.outputDatasetVersionId() != null) {
            execution.setCurrentDatasetVersionId(event.outputDatasetVersionId());
        }

        if (event.status() == ExecutionStatus.FAILED) {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorMessage("Step " + event.stepNumber() + " (" + event.stepCode() + ") failed: " + event.errorMessage());
            execution.setFinishedAt(Instant.now());
            if (execution.getStartedAt() != null) {
                execution.setDurationMs(Math.max(1L, Duration.between(execution.getStartedAt(), execution.getFinishedAt()).toMillis()));
            }
            executionRepository.save(execution);
            return getExecutionDetails(execution.getId());
        }

        List<StepExecutionEntity> allSteps = stepExecutionRepository.findByExecutionIdOrderByStepNumberAsc(execution.getId());

        if ("FULL_PIPELINE".equalsIgnoreCase(execution.getExecutionMode())) {
            int nextStepNumber = event.stepNumber() + 1;
            StepExecutionEntity nextStep = allSteps.stream()
                    .filter(s -> s.getStepNumber() == nextStepNumber)
                    .findFirst()
                    .orElse(null);

            if (nextStep != null) {
                UUID nextInputDvId = event.outputDatasetVersionId() != null
                        ? event.outputDatasetVersionId()
                        : execution.getCurrentDatasetVersionId();
                dispatchStepExecution(execution, nextStep, nextInputDvId, false);
                executionRepository.save(execution);
                return getExecutionDetails(execution.getId());
            }
        }

        boolean allCompleted = allSteps.stream().allMatch(s -> s.getStatus() == ExecutionStatus.COMPLETED);
        if (allCompleted || !"FULL_PIPELINE".equalsIgnoreCase(execution.getExecutionMode())) {
            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setFinishedAt(Instant.now());
            if (execution.getStartedAt() != null) {
                execution.setDurationMs(Math.max(1L, Duration.between(execution.getStartedAt(), execution.getFinishedAt()).toMillis()));
            }
            executionRepository.save(execution);

            logPublisher.publishLog(
                    execution.getId(),
                    step.getId(),
                    step.getStepNumber(),
                    "INFO",
                    "EXECUTION_COMPLETED",
                    "Ejecucion finalizada en estado COMPLETED (modo=" + execution.getExecutionMode() + ")",
                    Map.of("currentDatasetVersionId", String.valueOf(execution.getCurrentDatasetVersionId()))
            );
        } else {
            executionRepository.save(execution);
        }

        return getExecutionDetails(execution.getId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listExecutions() {
        return executionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(e -> getExecutionDetails(e.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getExecutionDetails(UUID executionId) {
        ExecutionEntity e = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        WorkflowEntity wf = workflowRepository.findById(e.getWorkflowId()).orElse(null);
        ConfigurationEntity cfg = configurationRepository.findById(e.getConfigurationId()).orElse(null);
        List<StepExecutionEntity> steps = stepExecutionRepository.findByExecutionIdOrderByStepNumberAsc(executionId);

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", e.getId());
        dto.put("workflowId", e.getWorkflowId());
        dto.put("workflowCode", wf != null ? wf.getCode() : "UNKNOWN");
        dto.put("workflowName", wf != null ? wf.getName() : "Unknown Workflow");
        dto.put("methodologyVersion", e.getMethodologyVersion());
        dto.put("initialDatasetVersionId", e.getInitialDatasetVersionId());
        dto.put("currentDatasetVersionId", e.getCurrentDatasetVersionId());
        dto.put("configurationId", e.getConfigurationId());
        dto.put("configurationName", cfg != null ? cfg.getName() : "Default");
        dto.put("randomSeed", cfg != null ? cfg.getRandomSeed() : 42L);
        dto.put("codeVersion", e.getCodeVersion());
        dto.put("executionMode", e.getExecutionMode());
        dto.put("status", e.getStatus());
        dto.put("startedAt", e.getStartedAt());
        dto.put("finishedAt", e.getFinishedAt());
        dto.put("durationMs", e.getDurationMs());
        dto.put("errorMessage", e.getErrorMessage());
        dto.put("createdAt", e.getCreatedAt());
        dto.put("steps", steps.stream().map(this::toStepExecutionMap).toList());
        return dto;
    }

    private void dispatchStepExecution(
            ExecutionEntity execution,
            StepExecutionEntity step,
            UUID inputDatasetVersionId,
            boolean independentMode) {

        step.setStatus(ExecutionStatus.RUNNING);
        step.setIndependentMode(independentMode);
        step.setInputDatasetVersionId(inputDatasetVersionId);
        step.setStartedAt(Instant.now());
        step.setFinishedAt(null);
        step.setErrorCode(null);
        step.setErrorMessage(null);
        stepExecutionRepository.save(step);

        StepExecutionCommandMessage command = new StepExecutionCommandMessage(
                UUID.randomUUID(),
                execution.getId(),
                step.getId(),
                step.getStepNumber(),
                step.getStepCode(),
                step.getAlgorithmVersionId(),
                inputDatasetVersionId,
                step.getRandomSeed(),
                independentMode,
                parseJsonMap(step.getParametersUsed()),
                Instant.now()
        );

        String routingKey = resolveRoutingKeyForStep(step.getStepNumber());
        try {
            rabbitTemplate.convertAndSend(MessagingConstants.EXCHANGE_NAME, routingKey, command);
            log.info("Dispatched Step {} ({}) command for execution {} via routing key {}",
                    step.getStepNumber(), step.getStepCode(), execution.getId(), routingKey);
        } catch (Exception ex) {
            log.warn("RabbitMQ broker not reachable during step dispatch ({}): command remains in RUNNING state for event callback",
                    ex.getMessage());
        }
    }

    private String resolveRoutingKeyForStep(int stepNumber) {
        return switch (stepNumber) {
            case 1 -> MessagingConstants.ROUTING_CMD_INGESTION;
            case 2 -> MessagingConstants.ROUTING_CMD_PROCESSING;
            default -> MessagingConstants.ROUTING_CMD_MINING;
        };
    }

    private UUID resolveInputDatasetVersionForStep(
            ExecutionEntity execution,
            List<StepExecutionEntity> steps,
            int stepNumber,
            boolean independentMode) {
        if (stepNumber == 1 || independentMode) {
            return execution.getCurrentDatasetVersionId() != null
                    ? execution.getCurrentDatasetVersionId()
                    : execution.getInitialDatasetVersionId();
        }
        return steps.stream()
                .filter(s -> s.getStepNumber() == (stepNumber - 1))
                .findFirst()
                .map(s -> s.getOutputDatasetVersionId() != null ? s.getOutputDatasetVersionId() : execution.getCurrentDatasetVersionId())
                .orElse(execution.getInitialDatasetVersionId());
    }

    private Map<String, Object> toStepExecutionMap(StepExecutionEntity se) {
        WorkflowStepEntity ws = workflowStepRepository.findById(se.getWorkflowStepId()).orElse(null);
        AlgorithmVersionEntity av = algorithmVersionRepository.findById(se.getAlgorithmVersionId()).orElse(null);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", se.getId());
        map.put("executionId", se.getExecutionId());
        map.put("stepNumber", se.getStepNumber());
        map.put("stepCode", se.getStepCode());
        map.put("stepName", ws != null ? ws.getName() : se.getStepCode());
        map.put("targetService", ws != null ? ws.getTargetService() : "data-mining-service");
        map.put("dependsOnStepNumber", ws != null ? ws.getDependsOnStepNumber() : null);
        map.put("status", se.getStatus());
        map.put("independentMode", se.isIndependentMode());
        map.put("attemptNumber", se.getAttemptNumber());
        map.put("algorithmVersionId", se.getAlgorithmVersionId());
        map.put("algorithmVersion", av != null ? av.getVersion() : "1.0.0");
        map.put("inputDatasetVersionId", se.getInputDatasetVersionId());
        map.put("outputDatasetVersionId", se.getOutputDatasetVersionId());
        map.put("randomSeed", se.getRandomSeed());
        map.put("parametersUsed", parseJsonMap(se.getParametersUsed()));
        map.put("outputSummary", parseJsonMap(se.getOutputSummary()));
        map.put("errorCode", se.getErrorCode());
        map.put("errorMessage", se.getErrorMessage());
        map.put("startedAt", se.getStartedAt());
        map.put("finishedAt", se.getFinishedAt());
        map.put("durationMs", se.getDurationMs());
        return map;
    }

    private void setCancelFlagInRedis(UUID executionId) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("exec:cancel:" + executionId, "true", Duration.ofHours(6));
            } catch (Exception ignored) {
            }
        }
    }

    private void clearCancelFlagInRedis(UUID executionId) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete("exec:cancel:" + executionId);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isCancelledInRedis(UUID executionId) {
        if (redisTemplate != null) {
            try {
                return "true".equalsIgnoreCase(redisTemplate.opsForValue().get("exec:cancel:" + executionId));
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private Map<String, Object> mergeParameters(
            Map<String, Object> defaults,
            Map<String, Object> globals,
            Map<String, Object> stepOverride) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (defaults != null) merged.putAll(defaults);
        if (globals != null) merged.putAll(globals);
        if (stepOverride != null) merged.putAll(stepOverride);
        return merged;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj != null ? obj : Map.of());
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Object parseJsonObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    public static class DependencyNotCompletedException extends RuntimeException {
        public DependencyNotCompletedException(String message) {
            super(message);
        }
    }
}
