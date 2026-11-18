package com.porros365.evmining.orchestrator.dto;

import java.util.Map;
import java.util.UUID;

public record CreateExecutionRequest(
        UUID workflowId,
        String workflowCode,
        UUID datasetVersionId,
        String configurationName,
        Long randomSeed,
        Map<String, Object> globalParameters,
        Map<String, Map<String, Object>> stepOverrides
) {
}
