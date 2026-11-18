package com.porros365.evmining.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mining_results")
public class MiningResultEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "step_execution_id", nullable = false)
    private UUID stepExecutionId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "dataset_version_id", nullable = false)
    private UUID datasetVersionId;

    @Column(name = "algorithm_version_id", nullable = false)
    private UUID algorithmVersionId;

    @Column(name = "code_version", nullable = false, length = 64)
    private String codeVersion;

    @Column(name = "random_seed")
    private Long randomSeed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_summary", nullable = false)
    private String metricsSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analytical_payload", nullable = false)
    private String analyticalPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public MiningResultEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }
    public UUID getStepExecutionId() { return stepExecutionId; }
    public void setStepExecutionId(UUID stepExecutionId) { this.stepExecutionId = stepExecutionId; }
    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }
    public UUID getDatasetVersionId() { return datasetVersionId; }
    public void setDatasetVersionId(UUID datasetVersionId) { this.datasetVersionId = datasetVersionId; }
    public UUID getAlgorithmVersionId() { return algorithmVersionId; }
    public void setAlgorithmVersionId(UUID algorithmVersionId) { this.algorithmVersionId = algorithmVersionId; }
    public String getCodeVersion() { return codeVersion; }
    public void setCodeVersion(String codeVersion) { this.codeVersion = codeVersion; }
    public Long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(Long randomSeed) { this.randomSeed = randomSeed; }
    public String getMetricsSummary() { return metricsSummary; }
    public void setMetricsSummary(String metricsSummary) { this.metricsSummary = metricsSummary; }
    public String getAnalyticalPayload() { return analyticalPayload; }
    public void setAnalyticalPayload(String analyticalPayload) { this.analyticalPayload = analyticalPayload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
