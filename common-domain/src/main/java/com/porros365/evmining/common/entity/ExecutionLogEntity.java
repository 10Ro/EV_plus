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
@Table(name = "execution_logs")
public class ExecutionLogEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "step_execution_id")
    private UUID stepExecutionId;

    @Column(name = "step_number")
    private Integer stepNumber;

    @Column(name = "service_name", nullable = false, length = 64)
    private String serviceName;

    @Column(nullable = false, length = 16)
    private String level;

    @Column(name = "event_code", nullable = false, length = 64)
    private String eventCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_data")
    private String contextData;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public ExecutionLogEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }
    public UUID getStepExecutionId() { return stepExecutionId; }
    public void setStepExecutionId(UUID stepExecutionId) { this.stepExecutionId = stepExecutionId; }
    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getEventCode() { return eventCode; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getContextData() { return contextData; }
    public void setContextData(String contextData) { this.contextData = contextData; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
