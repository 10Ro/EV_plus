package com.porros365.evmining.common.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StepExecutionCommandMessage(
        UUID messageId,
        UUID executionId,
        UUID stepExecutionId,
        int stepNumber,
        String stepCode,
        UUID algorithmVersionId,
        UUID inputDatasetVersionId,
        Long randomSeed,
        boolean independentMode,
        Map<String, Object> parameters,
        Instant dispatchedAt
) {
}
