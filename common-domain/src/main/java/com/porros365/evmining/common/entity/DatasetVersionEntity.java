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
@Table(name = "dataset_versions")
public class DatasetVersionEntity {

    @Id
    private UUID id;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "stage_type", nullable = false, length = 40)
    private String stageType;

    @Column(name = "record_count", nullable = false)
    private Integer recordCount;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion = "1.0";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "records_payload", nullable = false)
    private String recordsPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_stats", nullable = false)
    private String summaryStats;

    @Column(name = "parent_version_id")
    private UUID parentVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public DatasetVersionEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public String getStageType() { return stageType; }
    public void setStageType(String stageType) { this.stageType = stageType; }
    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getRecordsPayload() { return recordsPayload; }
    public void setRecordsPayload(String recordsPayload) { this.recordsPayload = recordsPayload; }
    public String getSummaryStats() { return summaryStats; }
    public void setSummaryStats(String summaryStats) { this.summaryStats = summaryStats; }
    public UUID getParentVersionId() { return parentVersionId; }
    public void setParentVersionId(UUID parentVersionId) { this.parentVersionId = parentVersionId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
