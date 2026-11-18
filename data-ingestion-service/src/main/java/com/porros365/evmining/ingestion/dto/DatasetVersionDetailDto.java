package com.porros365.evmining.ingestion.dto;

import com.porros365.evmining.common.dto.MarketOddsRecordDto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DatasetVersionDetailDto(
        UUID id,
        UUID datasetId,
        Integer versionNumber,
        String stageType,
        Integer recordCount,
        String checksumSha256,
        String schemaVersion,
        Map<String, Object> summaryStats,
        List<MarketOddsRecordDto> records,
        UUID parentVersionId,
        Instant createdAt
) {
}
