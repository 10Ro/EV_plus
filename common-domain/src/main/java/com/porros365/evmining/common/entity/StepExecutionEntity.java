package com.porros365.evmining.common.entity;

import com.porros365.evmining.common.domain.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "step_executions")
public class StepExecutionEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "workflow_step_id", nullable = false)
    private UUID workflowStepId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "step_code", nullable = false, length = 64)
    private String stepCode;

    @Column(name = "algorithm_version_id", nullable = false)
    private UUID algorithmVersionId;

    @Column(name = "input_dataset_version_id")
    private UUID inputDatasetVersionId;

    @Column(name = "output_dataset_version_id")
    private UUID outputDatasetVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(name = "independent_mode", nullable = false)
    private boolean independentMode = false;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(name = "random_seed")
    private Long randomSeed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_used", nullable = false)
    private String parametersUsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_summary")
    private String inputSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_summary")
    private String outputSummary;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    public StepExecutionEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }
    public UUID getWorkflowStepId() { return workflowStepId; }
    public void setWorkflowStepId(UUID workflowStepId) { this.workflowStepId = workflowStepId; }
    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public UUID getAlgorithmVersionId() { return algorithmVersionId; }
    public void setAlgorithmVersionId(UUID algorithmVersionId) { this.algorithmVersionId = algorithmVersionId; }
    public UUID getInputDatasetVersionId() { return inputDatasetVersionId; }
    public void setInputDatasetVersionId(UUID inputDatasetVersionId) { this.inputDatasetVersionId = inputDatasetVersionId; }
    public UUID getOutputDatasetVersionId() { return outputDatasetVersionId; }
    public void setOutputDatasetVersionId(UUID outputDatasetVersionId) { this.outputDatasetVersionId = outputDatasetVersionId; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
    public boolean isIndependentMode() { return independentMode; }
    public void setIndependentMode(boolean independentMode) { this.independentMode = independentMode; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public Long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(Long randomSeed) { this.randomSeed = randomSeed; }
    public String getParametersUsed() { return parametersUsed; }
    public void setParametersUsed(String parametersUsed) { this.parametersUsed = parametersUsed; }
    public String getInputSummary() { return inputSummary; }
    public void setInputSummary(String inputSummary) { this.inputSummary = inputSummary; }
    public String getOutputSummary() { return outputSummary; }
    public void setOutputSummary(String outputSummary) { this.outputSummary = outputSummary; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
