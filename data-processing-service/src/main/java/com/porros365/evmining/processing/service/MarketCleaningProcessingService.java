package com.porros365.evmining.processing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.domain.ExecutionStatus;
import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import com.porros365.evmining.common.entity.DatasetVersionEntity;
import com.porros365.evmining.common.messaging.DistributedLogPublisher;
import com.porros365.evmining.common.messaging.MessagingConstants;
import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.common.messaging.StepExecutionEventMessage;
import com.porros365.evmining.common.repository.DatasetVersionRepository;
import com.porros365.evmining.common.util.HashUtil;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MarketCleaningProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MarketCleaningProcessingService.class);

    private final DatasetVersionRepository datasetVersionRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final DistributedLogPublisher logPublisher;

    public MarketCleaningProcessingService(
            DatasetVersionRepository datasetVersionRepository,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            DistributedLogPublisher logPublisher) {
        this.datasetVersionRepository = datasetVersionRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.logPublisher = logPublisher;
    }

    /**
     * Paso 2 de la Metodologia (@Porros365): MARKET_CLEANING_AND_NORMALIZATION
     * Deduplica mercados, normaliza identificadores, trata nulos y filtra outliers de margen sharp.
     */
    @Transactional
    public StepExecutionEventMessage executeStep2Cleaning(StepExecutionCommandMessage command) {
        long startTime = System.currentTimeMillis();
        try {
            logPublisher.publishLog(
                    command.executionId(),
                    command.stepExecutionId(),
                    2,
                    "INFO",
                    "STEP_2_CLEANING_STARTED",
                    "Iniciando Paso 2: Limpieza, deduplicacion y filtrado de anomalias de margen Sharp",
                    Map.of("inputDatasetVersionId", String.valueOf(command.inputDatasetVersionId()))
            );

            DatasetVersionEntity inputVersion = datasetVersionRepository.findById(command.inputDatasetVersionId())
                    .orElseThrow(() -> new IllegalArgumentException("Input DatasetVersion not found: " + command.inputDatasetVersionId()));

            List<MarketOddsRecordDto> rawRecords = deserializeRecords(inputVersion.getRecordsPayload());

            String dedupStrategy = getStringParam(command.parameters(), "deduplicationStrategy", "KEEP_LATEST");
            double maxSharpMargin = getDoubleParam(command.parameters(), "maxSharpMarginAllowed", 0.08);
            double minSharpMargin = getDoubleParam(command.parameters(), "minSharpMarginAllowed", -0.01);
            String nullClosingPolicy = getStringParam(command.parameters(), "nullClosingOddsPolicy", "ALLOW_FOR_PREMATCH");

            CleaningOutcome outcome = cleanAndNormalizeRecords(
                    rawRecords,
                    dedupStrategy,
                    minSharpMargin,
                    maxSharpMargin,
                    nullClosingPolicy
            );

            if (outcome.cleanedRecords().isEmpty()) {
                throw new IllegalArgumentException("EMPTY_CLEANED_DATASET: All market records were filtered out during Step 2 cleaning");
            }

            int nextVersionNum = datasetVersionRepository
                    .findFirstByDatasetIdOrderByVersionNumberDesc(inputVersion.getDatasetId())
                    .map(v -> v.getVersionNumber() + 1)
                    .orElse(inputVersion.getVersionNumber() + 1);

            String cleanedJson = objectMapper.writeValueAsString(outcome.cleanedRecords());
            String summaryJson = objectMapper.writeValueAsString(outcome.summaryStats());

            DatasetVersionEntity outputVersion = new DatasetVersionEntity();
            outputVersion.setId(UUID.randomUUID());
            outputVersion.setDatasetId(inputVersion.getDatasetId());
            outputVersion.setVersionNumber(nextVersionNum);
            outputVersion.setStageType("STEP2_CLEANED");
            outputVersion.setRecordCount(outcome.cleanedRecords().size());
            outputVersion.setChecksumSha256(HashUtil.sha256Hex(cleanedJson));
            outputVersion.setSchemaVersion("1.0");
            outputVersion.setRecordsPayload(cleanedJson);
            outputVersion.setSummaryStats(summaryJson);
            outputVersion.setParentVersionId(inputVersion.getId());
            outputVersion.setCreatedAt(Instant.now());
            datasetVersionRepository.save(outputVersion);

            long durationMs = Math.max(1L, System.currentTimeMillis() - startTime);
            logPublisher.publishLog(
                    command.executionId(),
                    command.stepExecutionId(),
                    2,
                    "INFO",
                    "STEP_2_CLEANING_COMPLETED",
                    "Paso 2 completado: " + outcome.cleanedRecords().size() + " mercados limpios (duplicados eliminados="
                            + outcome.droppedDuplicates() + ", anomalias de margen=" + outcome.droppedMarginOutliers() + ")",
                    outcome.summaryStats()
            );

            return new StepExecutionEventMessage(
                    command.executionId(),
                    command.stepExecutionId(),
                    2,
                    "MARKET_CLEANING_AND_NORMALIZATION",
                    ExecutionStatus.COMPLETED,
                    outputVersion.getId(),
                    null,
                    durationMs,
                    outcome.summaryStats(),
                    null,
                    null,
                    Instant.now()
            );
        } catch (Exception ex) {
            long durationMs = Math.max(1L, System.currentTimeMillis() - startTime);
            log.error("Step 2 failed for execution {}: {}", command.executionId(), ex.getMessage());
            logPublisher.publishLog(
                    command.executionId(),
                    command.stepExecutionId(),
                    2,
                    "ERROR",
                    "STEP_2_CLEANING_FAILED",
                    ex.getMessage(),
                    Map.of()
            );
            return new StepExecutionEventMessage(
                    command.executionId(),
                    command.stepExecutionId(),
                    2,
                    "MARKET_CLEANING_AND_NORMALIZATION",
                    ExecutionStatus.FAILED,
                    null,
                    null,
                    durationMs,
                    Map.of(),
                    "STEP_2_CLEANING_ERROR",
                    ex.getMessage(),
                    Instant.now()
            );
        }
    }

    @RabbitListener(queues = MessagingConstants.QUEUE_PROCESSING_STEPS)
    public void onStep2Command(StepExecutionCommandMessage command) {
        StepExecutionEventMessage resultEvent = executeStep2Cleaning(command);
        rabbitTemplate.convertAndSend(
                MessagingConstants.EXCHANGE_NAME,
                MessagingConstants.ROUTING_EVENT_STATUS,
                resultEvent
        );
    }

    public CleaningOutcome cleanAndNormalizeRecords(
            List<MarketOddsRecordDto> inputRecords,
            String deduplicationStrategy,
            double minSharpMarginAllowed,
            double maxSharpMarginAllowed,
            String nullClosingOddsPolicy) {

        Map<String, MarketOddsRecordDto> deduplicatedMap = new LinkedHashMap<>();
        int droppedDuplicates = 0;

        for (MarketOddsRecordDto raw : inputRecords) {
            MarketOddsRecordDto normalized = normalizeRecord(raw);
            String compositeKey = normalized.eventId() + "|" + normalized.marketType() + "|"
                    + normalized.selectionName() + "|" + normalized.softBookmaker();

            if (deduplicatedMap.containsKey(compositeKey)) {
                droppedDuplicates++;
                if (!"KEEP_FIRST".equalsIgnoreCase(deduplicationStrategy)) {
                    deduplicatedMap.put(compositeKey, normalized);
                }
            } else {
                deduplicatedMap.put(compositeKey, normalized);
            }
        }

        List<MarketOddsRecordDto> cleaned = new ArrayList<>();
        int droppedMarginOutliers = 0;
        int droppedNullClosing = 0;
        DescriptiveStatistics marginStats = new DescriptiveStatistics();

        for (MarketOddsRecordDto r : deduplicatedMap.values()) {
            if ("DROP_RECORD".equalsIgnoreCase(nullClosingOddsPolicy) && r.closingFairOdds() == null) {
                droppedNullClosing++;
                continue;
            }
            double overroundSum = (1.0 / r.sharpOddsSelection()) + (1.0 / r.sharpOddsOpposite());
            if ("MATCH_ODDS_1X2".equalsIgnoreCase(r.marketType()) && r.sharpOddsDraw() != null) {
                overroundSum += (1.0 / r.sharpOddsDraw());
            }
            double sharpMargin = overroundSum - 1.0;

            if (sharpMargin < minSharpMarginAllowed || sharpMargin > maxSharpMarginAllowed) {
                droppedMarginOutliers++;
                continue;
            }
            marginStats.addValue(sharpMargin * 100.0);
            cleaned.add(r);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stageType", "STEP2_CLEANED");
        summary.put("inputRecords", inputRecords.size());
        summary.put("cleanedRecords", cleaned.size());
        summary.put("droppedDuplicates", droppedDuplicates);
        summary.put("droppedMarginOutliers", droppedMarginOutliers);
        summary.put("droppedNullClosing", droppedNullClosing);
        summary.put("meanSharpMarginPct", round4(marginStats.getN() > 0 ? marginStats.getMean() : 0.0));
        summary.put("medianSharpMarginPct", round4(marginStats.getN() > 0 ? marginStats.getPercentile(50) : 0.0));

        return new CleaningOutcome(cleaned, droppedDuplicates, droppedMarginOutliers, droppedNullClosing, summary);
    }

    private MarketOddsRecordDto normalizeRecord(MarketOddsRecordDto r) {
        String normSport = r.sport() != null ? r.sport().trim().toUpperCase(Locale.ROOT) : "FOOTBALL";
        String normMarket = r.marketType().trim().toUpperCase(Locale.ROOT);
        String normSharp = r.sharpBookmaker().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        String normSoft = r.softBookmaker().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        String normOutcome = (r.outcomeStatus() != null && !r.outcomeStatus().isBlank())
                ? r.outcomeStatus().trim().toUpperCase(Locale.ROOT)
                : "PENDING";

        return new MarketOddsRecordDto(
                r.eventId().trim(),
                normSport,
                r.competition() != null ? r.competition().trim() : "General",
                r.eventName().trim(),
                normMarket,
                r.marketLine() != null ? r.marketLine().trim() : "MAIN",
                r.selectionName().trim(),
                normSharp,
                r.sharpOddsSelection(),
                r.sharpOddsOpposite(),
                r.sharpOddsDraw(),
                normSoft,
                r.softTakenOdds(),
                r.closingFairOdds(),
                normOutcome,
                r.impliedProbabilityRaw(),
                r.sharpMarginPct(),
                r.trueProbability(),
                r.fairOdds(),
                r.expectedValue(),
                r.expectedValuePct(),
                r.evPlusOpportunity(),
                r.assignedStakeUnits(),
                r.expectedProfitUnits(),
                r.clvValue(),
                r.clvPct(),
                r.beatClosingLine(),
                r.actualProfitUnits()
        );
    }

    private List<MarketOddsRecordDto> deserializeRecords(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<MarketOddsRecordDto>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize dataset records", e);
        }
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultVal) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return defaultVal;
        }
        return params.get(key).toString();
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

    private double round4(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return 0.0;
        }
        return Math.round(val * 10000.0) / 10000.0;
    }

    public record CleaningOutcome(
            List<MarketOddsRecordDto> cleanedRecords,
            int droppedDuplicates,
            int droppedMarginOutliers,
            int droppedNullClosing,
            Map<String, Object> summaryStats
    ) {
    }
}
