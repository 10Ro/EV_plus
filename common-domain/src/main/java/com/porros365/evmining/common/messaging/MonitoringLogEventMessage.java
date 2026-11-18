package com.porros365.evmining.common.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MonitoringLogEventMessage(
        UUID logId,
        UUID executionId,
        UUID stepExecutionId,
        Integer stepNumber,
        String serviceName,
        String level,
        String eventCode,
        String message,
        Map<String, Object> contextData,
        Instant timestamp
) {
}
