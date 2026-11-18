package com.porros365.evmining.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "workflow_steps")
public class WorkflowStepEntity {

    @Id
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "step_code", nullable = false, length = 64)
    private String stepCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String objective;

    @Column(name = "target_service", nullable = false, length = 64)
    private String targetService;

    @Column(name = "default_algorithm_id", nullable = false)
    private UUID defaultAlgorithmId;

    @Column(name = "depends_on_step_number")
    private Integer dependsOnStepNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_contract", nullable = false)
    private String inputContract;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_contract", nullable = false)
    private String outputContract;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_parameters", nullable = false)
    private String defaultParameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_criteria", nullable = false)
    private String validationCriteria;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "possible_errors", nullable = false)
    private String possibleErrors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_results", nullable = false)
    private String expectedResults;

    public WorkflowStepEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkflowId() { return workflowId; }
    public void setWorkflowId(UUID workflowId) { this.workflowId = workflowId; }
    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }
    public UUID getDefaultAlgorithmId() { return defaultAlgorithmId; }
    public void setDefaultAlgorithmId(UUID defaultAlgorithmId) { this.defaultAlgorithmId = defaultAlgorithmId; }
    public Integer getDependsOnStepNumber() { return dependsOnStepNumber; }
    public void setDependsOnStepNumber(Integer dependsOnStepNumber) { this.dependsOnStepNumber = dependsOnStepNumber; }
    public String getInputContract() { return inputContract; }
    public void setInputContract(String inputContract) { this.inputContract = inputContract; }
    public String getOutputContract() { return outputContract; }
    public void setOutputContract(String outputContract) { this.outputContract = outputContract; }
    public String getDefaultParameters() { return defaultParameters; }
    public void setDefaultParameters(String defaultParameters) { this.defaultParameters = defaultParameters; }
    public String getValidationCriteria() { return validationCriteria; }
    public void setValidationCriteria(String validationCriteria) { this.validationCriteria = validationCriteria; }
    public String getPossibleErrors() { return possibleErrors; }
    public void setPossibleErrors(String possibleErrors) { this.possibleErrors = possibleErrors; }
    public String getExpectedResults() { return expectedResults; }
    public void setExpectedResults(String expectedResults) { this.expectedResults = expectedResults; }
}
