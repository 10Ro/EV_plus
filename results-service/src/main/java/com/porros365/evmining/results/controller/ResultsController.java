package com.porros365.evmining.results.controller;

import com.porros365.evmining.results.service.ResultsAndComparisonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ResultsController {

    private final ResultsAndComparisonService resultsService;

    public ResultsController(ResultsAndComparisonService resultsService) {
        this.resultsService = resultsService;
    }

    @GetMapping("/executions/{executionId}/results")
    public ResponseEntity<List<Map<String, Object>>> getExecutionResults(@PathVariable UUID executionId) {
        return ResponseEntity.ok(resultsService.getResultsForExecution(executionId));
    }

    @GetMapping("/executions/compare")
    public ResponseEntity<Map<String, Object>> compareExecutions(
            @RequestParam("first") UUID firstExecutionId,
            @RequestParam("second") UUID secondExecutionId) {
        return ResponseEntity.ok(resultsService.compareExecutions(firstExecutionId, secondExecutionId));
    }

    @GetMapping("/results/executions/{executionId}/report")
    public ResponseEntity<Map<String, Object>> exportExecutionReport(@PathVariable UUID executionId) {
        return ResponseEntity.ok(resultsService.generateExecutionReport(executionId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "RESULTS_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
