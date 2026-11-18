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
@Table(name = "algorithm_versions")
public class AlgorithmVersionEntity {

    @Id
    private UUID id;

    @Column(name = "algorithm_id", nullable = false)
    private UUID algorithmId;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "implementation_class", nullable = false, length = 200)
    private String implementationClass;

    @Column(name = "code_commit_hash", nullable = false, length = 64)
    private String codeCommitHash;

    @Column(name = "supports_random_seed", nullable = false)
    private boolean supportsRandomSeed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameter_schema", nullable = false)
    private String parameterSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_parameters", nullable = false)
    private String defaultParameters;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AlgorithmVersionEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAlgorithmId() { return algorithmId; }
    public void setAlgorithmId(UUID algorithmId) { this.algorithmId = algorithmId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getImplementationClass() { return implementationClass; }
    public void setImplementationClass(String implementationClass) { this.implementationClass = implementationClass; }
    public String getCodeCommitHash() { return codeCommitHash; }
    public void setCodeCommitHash(String codeCommitHash) { this.codeCommitHash = codeCommitHash; }
    public boolean isSupportsRandomSeed() { return supportsRandomSeed; }
    public void setSupportsRandomSeed(boolean supportsRandomSeed) { this.supportsRandomSeed = supportsRandomSeed; }
    public String getParameterSchema() { return parameterSchema; }
    public void setParameterSchema(String parameterSchema) { this.parameterSchema = parameterSchema; }
    public String getDefaultParameters() { return defaultParameters; }
    public void setDefaultParameters(String defaultParameters) { this.defaultParameters = defaultParameters; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
