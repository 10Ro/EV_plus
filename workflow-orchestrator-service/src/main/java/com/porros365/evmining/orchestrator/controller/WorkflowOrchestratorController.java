package com.porros365.evmining.orchestrator.controller;

import com.porros365.evmining.common.entity.WorkflowEntity;
import com.porros365.evmining.common.messaging.StepExecutionEventMessage;
import com.porros365.evmining.orchestrator.dto.CreateExecutionRequest;
import com.porros365.evmining.orchestrator.dto.CreateWorkflowRequest;
import com.porros365.evmining.orchestrator.service.WorkflowOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class WorkflowOrchestratorController {

    private final WorkflowOrchestrationService orchestrationService;

    public WorkflowOrchestratorController(WorkflowOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @GetMapping("/workflows")
    public ResponseEntity<List<WorkflowEntity>> listWorkflows() {
        return ResponseEntity.ok(orchestrationService.listWorkflows());
    }

    @PostMapping("/workflows")
    public ResponseEntity<WorkflowEntity> createWorkflow(@Valid @RequestBody CreateWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orchestrationService.createWorkflow(request));
    }

    @GetMapping("/workflows/{workflowId}/steps")
    public ResponseEntity<List<Map<String, Object>>> getWorkflowSteps(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(orchestrationService.getWorkflowStepsSheets(workflowId));
    }

    @PostMapping("/executions")
    public ResponseEntity<Map<String, Object>> createExecution(@RequestBody CreateExecutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orchestrationService.createExecution(request));
    }

    @GetMapping("/executions")
    public ResponseEntity<List<Map<String, Object>>> listExecutions() {
        return ResponseEntity.ok(orchestrationService.listExecutions());
    }

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecution(@PathVariable UUID executionId) {
        return ResponseEntity.ok(orchestrationService.getExecutionDetails(executionId));
    }

    @PostMapping("/executions/{executionId}/run")
    public ResponseEntity<Map<String, Object>> runFullPipeline(@PathVariable UUID executionId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(orchestrationService.startFullPipelineExecution(executionId));
    }

    @PostMapping("/executions/{executionId}/steps/{stepNumber}/run")
    public ResponseEntity<Map<String, Object>> runIndividualStep(
            @PathVariable UUID executionId,
            @PathVariable int stepNumber,
            @RequestParam(name = "independentMode", defaultValue = "false") boolean independentMode) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(orchestrationService.runIndividualStep(executionId, stepNumber, independentMode));
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelExecution(@PathVariable UUID executionId) {
        return ResponseEntity.ok(orchestrationService.cancelExecution(executionId));
    }

    @PostMapping("/executions/{executionId}/steps/{stepNumber}/retry")
    public ResponseEntity<Map<String, Object>> retryStep(
            @PathVariable UUID executionId,
            @PathVariable int stepNumber) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(orchestrationService.retryFailedStep(executionId, stepNumber));
    }

    @PostMapping("/executions/events")
    public ResponseEntity<Map<String, Object>> handleStepEventCallback(@RequestBody StepExecutionEventMessage event) {
        return ResponseEntity.ok(orchestrationService.handleStepExecutionEvent(event));
    }

    @ExceptionHandler(WorkflowOrchestrationService.DependencyNotCompletedException.class)
    public ResponseEntity<Map<String, Object>> handleDependencyError(
            WorkflowOrchestrationService.DependencyNotCompletedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "DEPENDENCY_NOT_COMPLETED",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "ORCHESTRATOR_BAD_REQUEST",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
