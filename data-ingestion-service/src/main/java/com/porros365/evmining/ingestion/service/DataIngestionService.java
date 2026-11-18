package com.porros365.evmining.ingestion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.domain.ExecutionStatus;
import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import com.porros365.evmining.common.entity.DatasetEntity;
import com.porros365.evmining.common.entity.DatasetVersionEntity;
import com.porros365.evmining.common.messaging.DistributedLogPublisher;
import com.porros365.evmining.common.messaging.MessagingConstants;
import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.common.messaging.StepExecutionEventMessage;
import com.porros365.evmining.common.repository.DatasetRepository;
import com.porros365.evmining.common.repository.DatasetVersionRepository;
import com.porros365.evmining.common.util.HashUtil;
import com.porros365.evmining.ingestion.dto.CreateDatasetRequest;
import com.porros365.evmining.ingestion.dto.DatasetSummaryDto;
import com.porros365.evmining.ingestion.dto.DatasetVersionDetailDto;
import com.porros365.evmining.ingestion.dto.ImportCsvDatasetRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DataIngestionService.class);

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final DistributedLogPublisher logPublisher;

    public DataIngestionService(
            DatasetRepository datasetRepository,
            DatasetVersionRepository datasetVersionRepository,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            DistributedLogPublisher logPublisher) {
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.logPublisher = logPublisher;
    }

    @Transactional
    public DatasetSummaryDto createDataset(CreateDatasetRequest request) {
        validateRecords(request.records(), 1.01, 1000.0);

        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(UUID.randomUUID());
        dataset.setName(request.name().trim());
        dataset.setDescription(request.description());
        dataset.setSourceType(request.sourceType().trim().toUpperCase());
        dataset.setSourceReference(request.sourceReference());
        dataset.setCreatedAt(Instant.now());
        datasetRepository.save(dataset);

        DatasetVersionEntity version = createVersionEntity(
                dataset.getId(),
                1,
                "RAW_IMPORT",
                request.records(),
                null,
                buildSummaryStats(request.records(), "RAW_IMPORT")
        );
        datasetVersionRepository.save(version);

        return toSummaryDto(dataset, version);
    }

    @Transactional
    public DatasetSummaryDto importCsvDataset(ImportCsvDatasetRequest request) {
        List<MarketOddsRecordDto> parsed = parseCsvContent(request.csvContent());
        CreateDatasetRequest createReq = new CreateDatasetRequest(
                request.name(),
                request.description(),
                "CSV",
                request.sourceReference(),
                parsed
        );
        return createDataset(createReq);
    }

    @Transactional(readOnly = true)
    public List<DatasetSummaryDto> listDatasets() {
        List<DatasetEntity> datasets = datasetRepository.findAllByOrderByCreatedAtDesc();
        List<DatasetSummaryDto> result = new ArrayList<>();
        for (DatasetEntity ds : datasets) {
            DatasetVersionEntity latest = datasetVersionRepository
                    .findFirstByDatasetIdOrderByVersionNumberDesc(ds.getId())
                    .orElse(null);
            result.add(toSummaryDto(ds, latest));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<DatasetVersionDetailDto> listDatasetVersions(UUID datasetId) {
        return datasetVersionRepository.findByDatasetIdOrderByVersionNumberDesc(datasetId)
                .stream()
                .map(this::toVersionDetailDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public DatasetVersionDetailDto getDatasetVersion(UUID datasetId, UUID versionId) {
        DatasetVersionEntity entity = datasetVersionRepository.findById(versionId)
                .filter(v -> v.getDatasetId().equals(datasetId))
                .orElseThrow(() -> new IllegalArgumentException("DatasetVersion not found: " + versionId));
        return toVersionDetailDto(entity);
    }

    /**
     * Paso 1 de la Metodologia (@Porros365): ODDS_INGESTION_AND_CONSOLIDATION
     * Ejecutable tanto por comando AMQP del Orquestador como de forma directa.
     */
    @Transactional
    public StepExecutionEventMessage executeStep1Consolidation(StepExecutionCommandMessage command) {
        long startTime = System.currentTimeMillis();
        try {
            logPublisher.publishLog(
                    command.executionId(),
                    command.stepExecutionId(),
                    1,
                    "INFO",
                    "STEP_1_INGESTION_STARTED",
                    "Iniciando Paso 1: Validacion e Ingesta Consolidada de Mercados Sharp y Soft",
                    Map.of("inputDatasetVersionId", String.valueOf(command.inputDatasetVersionId()))
            );

            DatasetVersionEntity inputVersion = datasetVersionRepository.findById(command.inputDatasetVersionId())
                    .orElseThrow(() -> new IllegalArgumentException("Input DatasetVersion not found: " + command.inputDatasetVersionId()));

            List<MarketOddsRecordDto> records = deserializeRecords(inputVersion.getRecordsPayload());

            double minOdds = getDoubleParam(command.parameters(), "minAllowedOdds", 1.01);
            double maxOdds = getDoubleParam(command.parameters(), "maxAllowedOdds", 1000.0);

            validateRecords(records, minOdds, maxOdds);

            int nextVersionNum = datasetVersionRepository
                    .findFirstByDatasetIdOrderByVersionNumberDesc(inputVersion.getDatasetId())
                    .map(v -> v.getVersionNumber() + 1)
                    .orElse(inputVersion.getVersionNumber() + 1);

            Map<String, Object> summary = buildSummaryStats(records, "STEP1_CONSOLIDATED");
            DatasetVersionEntity outputVersion = createVersionEntity(
                    inputVersion.getDatasetId(),
                    nextVersionNum,
                    "STEP1_CONSOLIDATED",
                    records,
                    inputVersion.getId(),
                    summary
            );
            datasetVersionRepository.save(outputVersion);

            long durationMs = Math.max(1L, System.currentTimeMillis() - startTime);
            logPublisher.publishLog(
                    command.executionId(),
                    command.stepExecutionId(),
                    1,
                    "INFO",
                    "STEP_1_INGESTION_COMPLETED",
                    "Paso 1 completado: " + records.size() + " mercados Sharp/Soft consolidados en version v" + nextVersionNum,
                    summary
            );

            return new StepExecutionEventMessage(
                    command.executionId(),
                    command.stepExecutionId(),
                    1,
                    "ODDS_INGESTION_AND_CONSOLIDATION",
                    ExecutionStatus.COMPLETED,
                    outputVersion.getId(),
                    null,
                    durationMs,
                    summary,
                    null,
                    null,
                    Instant.now()
            );
        } catch (Exception ex) {
            long durationMs = Math.max(1L, System.currentTimeMillis() - startTime);
            log.error("Step 1 failed for execution {}: {}", command.executionId(), ex.getMessage());
            logPublisher.publishLog(
                    command.executionId(),
                    command.stepExecutionId(),
                    1,
                    "ERROR",
                    "STEP_1_INGESTION_FAILED",
                    ex.getMessage(),
                    Map.of()
            );
            return new StepExecutionEventMessage(
                    command.executionId(),
                    command.stepExecutionId(),
                    1,
                    "ODDS_INGESTION_AND_CONSOLIDATION",
                    ExecutionStatus.FAILED,
                    null,
                    null,
                    durationMs,
                    Map.of(),
                    "STEP_1_VALIDATION_ERROR",
                    ex.getMessage(),
                    Instant.now()
            );
        }
    }

    @RabbitListener(queues = MessagingConstants.QUEUE_INGESTION_STEPS)
    public void onStep1Command(StepExecutionCommandMessage command) {
        StepExecutionEventMessage resultEvent = executeStep1Consolidation(command);
        rabbitTemplate.convertAndSend(
                MessagingConstants.EXCHANGE_NAME,
                MessagingConstants.ROUTING_EVENT_STATUS,
                resultEvent
        );
    }

    public void validateRecords(List<MarketOddsRecordDto> records, double minOdds, double maxOdds) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("EMPTY_DATASET: Dataset must contain at least one market record");
        }
        for (int i = 0; i < records.size(); i++) {
            MarketOddsRecordDto r = records.get(i);
            if (r.eventId() == null || r.eventId().isBlank()) {
                throw new IllegalArgumentException("MISSING_EVENT_ID at index " + i);
            }
            if (r.sharpOddsSelection() == null || r.sharpOddsSelection() < minOdds || r.sharpOddsSelection() > maxOdds) {
                throw new IllegalArgumentException("INVALID_ODDS_VALUE for sharpOddsSelection at event " + r.eventId());
            }
            if (r.sharpOddsOpposite() == null || r.sharpOddsOpposite() < minOdds || r.sharpOddsOpposite() > maxOdds) {
                throw new IllegalArgumentException("INVALID_ODDS_VALUE for sharpOddsOpposite at event " + r.eventId());
            }
            if ("MATCH_ODDS_1X2".equalsIgnoreCase(r.marketType())) {
                if (r.sharpOddsDraw() == null || r.sharpOddsDraw() < minOdds || r.sharpOddsDraw() > maxOdds) {
                    throw new IllegalArgumentException("INCOMPLETE_MARKET_OUTCOMES: 1X2 market requires sharpOddsDraw at event " + r.eventId());
                }
            }
            if (r.softTakenOdds() == null || r.softTakenOdds() < minOdds || r.softTakenOdds() > maxOdds) {
                throw new IllegalArgumentException("INVALID_ODDS_VALUE for softTakenOdds at event " + r.eventId());
            }
        }
    }

    private List<MarketOddsRecordDto> parseCsvContent(String csvContent) {
        String[] lines = csvContent.strip().split("\\r?\\n");
        if (lines.length < 2) {
            throw new IllegalArgumentException("CSV must include a header line and at least one data row");
        }
        List<MarketOddsRecordDto> records = new ArrayList<>();
        // Header expected:
        // eventId,sport,competition,eventName,marketType,marketLine,selectionName,sharpBookmaker,sharpOddsSelection,sharpOddsOpposite,sharpOddsDraw,softBookmaker,softTakenOdds,closingFairOdds,outcomeStatus
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length < 13) {
                throw new IllegalArgumentException("Invalid CSV row at line " + (i + 1) + ": expected at least 13 columns");
            }
            Double sharpDraw = cols[10].isBlank() ? null : Double.parseDouble(cols[10].trim());
            Double closingOdds = (cols.length > 13 && !cols[13].isBlank()) ? Double.parseDouble(cols[13].trim()) : null;
            String outcome = (cols.length > 14 && !cols[14].isBlank()) ? cols[14].trim() : "PENDING";

            records.add(new MarketOddsRecordDto(
                    cols[0].trim(),
                    cols[1].trim(),
                    cols[2].trim(),
                    cols[3].trim(),
                    cols[4].trim(),
                    cols[5].trim(),
                    cols[6].trim(),
                    cols[7].trim(),
                    Double.parseDouble(cols[8].trim()),
                    Double.parseDouble(cols[9].trim()),
                    sharpDraw,
                    cols[11].trim(),
                    Double.parseDouble(cols[12].trim()),
                    closingOdds,
                    outcome,
                    null, null, null, null, null, null, null, null, null, null, null, null, null
            ));
        }
        return records;
    }

    private DatasetVersionEntity createVersionEntity(
            UUID datasetId,
            int versionNumber,
            String stageType,
            List<MarketOddsRecordDto> records,
            UUID parentVersionId,
            Map<String, Object> summaryStats) {
        try {
            String recordsJson = objectMapper.writeValueAsString(records);
            String summaryJson = objectMapper.writeValueAsString(summaryStats);
            DatasetVersionEntity entity = new DatasetVersionEntity();
            entity.setId(UUID.randomUUID());
            entity.setDatasetId(datasetId);
            entity.setVersionNumber(versionNumber);
            entity.setStageType(stageType);
            entity.setRecordCount(records.size());
            entity.setChecksumSha256(HashUtil.sha256Hex(recordsJson));
            entity.setSchemaVersion("1.0");
            entity.setRecordsPayload(recordsJson);
            entity.setSummaryStats(summaryJson);
            entity.setParentVersionId(parentVersionId);
            entity.setCreatedAt(Instant.now());
            return entity;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize dataset version", e);
        }
    }

    private Map<String, Object> buildSummaryStats(List<MarketOddsRecordDto> records, String stageType) {
        Set<String> sharpBooks = records.stream().map(MarketOddsRecordDto::sharpBookmaker).collect(Collectors.toSet());
        Set<String> softBooks = records.stream().map(MarketOddsRecordDto::softBookmaker).collect(Collectors.toSet());
        double avgSoftOdds = records.stream().mapToDouble(MarketOddsRecordDto::softTakenOdds).average().orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("stageType", stageType);
        stats.put("totalRecords", records.size());
        stats.put("sharpBookmakers", sharpBooks);
        stats.put("softBookmakers", softBooks);
        stats.put("averageSoftTakenOdds", Math.round(avgSoftOdds * 1000.0) / 1000.0);
        return stats;
    }

    private List<MarketOddsRecordDto> deserializeRecords(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<MarketOddsRecordDto>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize dataset records", e);
        }
    }

    private Map<String, Object> deserializeMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private DatasetSummaryDto toSummaryDto(DatasetEntity ds, DatasetVersionEntity latest) {
        return new DatasetSummaryDto(
                ds.getId(),
                ds.getName(),
                ds.getDescription(),
                ds.getSourceType(),
                ds.getSourceReference(),
                latest != null ? latest.getId() : null,
                latest != null ? latest.getVersionNumber() : 0,
                latest != null ? latest.getStageType() : "NONE",
                latest != null ? latest.getRecordCount() : 0,
                ds.getCreatedAt()
        );
    }

    private DatasetVersionDetailDto toVersionDetailDto(DatasetVersionEntity v) {
        return new DatasetVersionDetailDto(
                v.getId(),
                v.getDatasetId(),
                v.getVersionNumber(),
                v.getStageType(),
                v.getRecordCount(),
                v.getChecksumSha256(),
                v.getSchemaVersion(),
                deserializeMap(v.getSummaryStats()),
                deserializeRecords(v.getRecordsPayload()),
                v.getParentVersionId(),
                v.getCreatedAt()
        );
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultVal) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return defaultVal;
        }
        Object val = params.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(val.toString());
    }
}
