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
@Table(name = "configurations")
public class ConfigurationEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "methodology_version", nullable = false, length = 32)
    private String methodologyVersion;

    @Column(name = "random_seed", nullable = false)
    private Long randomSeed = 42L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "global_parameters", nullable = false)
    private String globalParameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_overrides", nullable = false)
    private String stepOverrides;

    @Column(name = "config_hash", nullable = false, length = 64)
    private String configHash;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ConfigurationEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMethodologyVersion() { return methodologyVersion; }
    public void setMethodologyVersion(String methodologyVersion) { this.methodologyVersion = methodologyVersion; }
    public Long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(Long randomSeed) { this.randomSeed = randomSeed; }
    public String getGlobalParameters() { return globalParameters; }
    public void setGlobalParameters(String globalParameters) { this.globalParameters = globalParameters; }
    public String getStepOverrides() { return stepOverrides; }
    public void setStepOverrides(String stepOverrides) { this.stepOverrides = stepOverrides; }
    public String getConfigHash() { return configHash; }
    public void setConfigHash(String configHash) { this.configHash = configHash; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
