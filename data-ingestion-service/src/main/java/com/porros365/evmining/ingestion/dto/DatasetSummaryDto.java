package com.porros365.evmining.ingestion.dto;

import java.time.Instant;
import java.util.UUID;

public record DatasetSummaryDto(
        UUID id,
        String name,
        String description,
        String sourceType,
        String sourceReference,
        UUID latestVersionId,
        Integer latestVersionNumber,
        String latestStageType,
        Integer recordCount,
        Instant createdAt
) {
}
