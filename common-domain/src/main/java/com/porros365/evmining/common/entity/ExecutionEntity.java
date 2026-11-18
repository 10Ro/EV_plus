package com.porros365.evmining.common.entity;

import com.porros365.evmining.common.domain.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "executions")
public class ExecutionEntity {

    @Id
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "initial_dataset_version_id", nullable = false)
    private UUID initialDatasetVersionId;

    @Column(name = "current_dataset_version_id", nullable = false)
    private UUID currentDatasetVersionId;

    @Column(name = "configuration_id", nullable = false)
    private UUID configurationId;

    @Column(name = "methodology_version", nullable = false, length = 32)
    private String methodologyVersion;

    @Column(name = "code_version", nullable = false, length = 64)
    private String codeVersion;

    @Column(name = "execution_mode", nullable = false, length = 32)
    private String executionMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(name = "triggered_by_user_id")
    private UUID triggeredByUserId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Version
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ExecutionEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkflowId() { return workflowId; }
    public void setWorkflowId(UUID workflowId) { this.workflowId = workflowId; }
    public UUID getInitialDatasetVersionId() { return initialDatasetVersionId; }
    public void setInitialDatasetVersionId(UUID initialDatasetVersionId) { this.initialDatasetVersionId = initialDatasetVersionId; }
    public UUID getCurrentDatasetVersionId() { return currentDatasetVersionId; }
    public void setCurrentDatasetVersionId(UUID currentDatasetVersionId) { this.currentDatasetVersionId = currentDatasetVersionId; }
    public UUID getConfigurationId() { return configurationId; }
    public void setConfigurationId(UUID configurationId) { this.configurationId = configurationId; }
    public String getMethodologyVersion() { return methodologyVersion; }
    public void setMethodologyVersion(String methodologyVersion) { this.methodologyVersion = methodologyVersion; }
    public String getCodeVersion() { return codeVersion; }
    public void setCodeVersion(String codeVersion) { this.codeVersion = codeVersion; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
    public UUID getTriggeredByUserId() { return triggeredByUserId; }
    public void setTriggeredByUserId(UUID triggeredByUserId) { this.triggeredByUserId = triggeredByUserId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
