package com.porros365.evmining.monitoring.controller;

import com.porros365.evmining.common.entity.ExecutionLogEntity;
import com.porros365.evmining.common.messaging.MonitoringLogEventMessage;
import com.porros365.evmining.monitoring.service.ExecutionLogMonitoringService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MonitoringController {

    private final ExecutionLogMonitoringService monitoringService;

    public MonitoringController(ExecutionLogMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/executions/{executionId}/logs")
    public ResponseEntity<List<ExecutionLogEntity>> getExecutionLogs(@PathVariable UUID executionId) {
        return ResponseEntity.ok(monitoringService.getLogsForExecution(executionId));
    }

    @PostMapping("/monitoring/logs")
    public ResponseEntity<ExecutionLogEntity> recordLogDirectly(@RequestBody MonitoringLogEventMessage eventMessage) {
        ExecutionLogEntity saved = monitoringService.recordLogEvent(eventMessage);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/monitoring/health")
    public ResponseEntity<Map<String, Object>> getDistributedSystemHealth() {
        return ResponseEntity.ok(Map.of(
                "service", "monitoring-service",
                "status", "UP",
                "monitoredMicroservices", List.of(
                        "api-gateway",
                        "data-ingestion-service",
                        "data-processing-service",
                        "data-mining-service",
                        "workflow-orchestrator-service",
                        "results-service",
                        "monitoring-service"
                ),
                "timestamp", Instant.now().toString()
        ));
    }
}
