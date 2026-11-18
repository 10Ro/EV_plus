package com.porros365.evmining.ingestion.controller;

import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.common.messaging.StepExecutionEventMessage;
import com.porros365.evmining.ingestion.dto.CreateDatasetRequest;
import com.porros365.evmining.ingestion.dto.DatasetSummaryDto;
import com.porros365.evmining.ingestion.dto.DatasetVersionDetailDto;
import com.porros365.evmining.ingestion.dto.ImportCsvDatasetRequest;
import com.porros365.evmining.ingestion.service.DataIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
@RequestMapping("/api/datasets")
public class DatasetIngestionController {

    private final DataIngestionService dataIngestionService;

    public DatasetIngestionController(DataIngestionService dataIngestionService) {
        this.dataIngestionService = dataIngestionService;
    }

    @PostMapping
    public ResponseEntity<DatasetSummaryDto> createDataset(@Valid @RequestBody CreateDatasetRequest request) {
        DatasetSummaryDto created = dataIngestionService.createDataset(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/import-csv")
    public ResponseEntity<DatasetSummaryDto> importCsvDataset(@Valid @RequestBody ImportCsvDatasetRequest request) {
        DatasetSummaryDto created = dataIngestionService.importCsvDataset(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<DatasetSummaryDto>> listDatasets() {
        return ResponseEntity.ok(dataIngestionService.listDatasets());
    }

    @GetMapping("/{datasetId}/versions")
    public ResponseEntity<List<DatasetVersionDetailDto>> listDatasetVersions(@PathVariable UUID datasetId) {
        return ResponseEntity.ok(dataIngestionService.listDatasetVersions(datasetId));
    }

    @GetMapping("/{datasetId}/versions/{versionId}")
    public ResponseEntity<DatasetVersionDetailDto> getDatasetVersion(
            @PathVariable UUID datasetId,
            @PathVariable UUID versionId) {
        return ResponseEntity.ok(dataIngestionService.getDatasetVersion(datasetId, versionId));
    }

    @PostMapping("/step1/consolidate")
    public ResponseEntity<StepExecutionEventMessage> runStep1Directly(@RequestBody StepExecutionCommandMessage command) {
        return ResponseEntity.ok(dataIngestionService.executeStep1Consolidation(command));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "INGESTION_VALIDATION_ERROR",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
