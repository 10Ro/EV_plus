package com.porros365.evmining.common.messaging;

import com.porros365.evmining.common.domain.ExecutionStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StepExecutionEventMessage(
        UUID executionId,
        UUID stepExecutionId,
        int stepNumber,
        String stepCode,
        ExecutionStatus status,
        UUID outputDatasetVersionId,
        UUID miningResultId,
        Long durationMs,
        Map<String, Object> outputSummary,
        String errorCode,
        String errorMessage,
        Instant finishedAt
) {
}
