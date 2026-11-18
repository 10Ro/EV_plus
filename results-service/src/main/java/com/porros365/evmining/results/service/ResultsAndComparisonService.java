package com.porros365.evmining.results.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.entity.ConfigurationEntity;
import com.porros365.evmining.common.entity.ExecutionEntity;
import com.porros365.evmining.common.entity.MiningResultEntity;
import com.porros365.evmining.common.repository.ConfigurationRepository;
import com.porros365.evmining.common.repository.ExecutionRepository;
import com.porros365.evmining.common.repository.MiningResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResultsAndComparisonService {

    private final MiningResultRepository miningResultRepository;
    private final ExecutionRepository executionRepository;
    private final ConfigurationRepository configurationRepository;
    private final ObjectMapper objectMapper;

    public ResultsAndComparisonService(
            MiningResultRepository miningResultRepository,
            ExecutionRepository executionRepository,
            ConfigurationRepository configurationRepository,
            ObjectMapper objectMapper) {
        this.miningResultRepository = miningResultRepository;
        this.executionRepository = executionRepository;
        this.configurationRepository = configurationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getResultsForExecution(UUID executionId) {
        List<MiningResultEntity> results = miningResultRepository.findByExecutionIdOrderByStepNumberAsc(executionId);
        List<Map<String, Object>> dtos = new ArrayList<>(results.size());
        for (MiningResultEntity r : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("executionId", r.getExecutionId());
            item.put("stepExecutionId", r.getStepExecutionId());
            item.put("stepNumber", r.getStepNumber());
            item.put("datasetVersionId", r.getDatasetVersionId());
            item.put("algorithmVersionId", r.getAlgorithmVersionId());
            item.put("codeVersion", r.getCodeVersion());
            item.put("randomSeed", r.getRandomSeed());
            item.put("metricsSummary", parseJsonMap(r.getMetricsSummary()));
            item.put("analyticalPayload", parseJsonMap(r.getAnalyticalPayload()));
            item.put("createdAt", r.getCreatedAt());
            dtos.add(item);
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compareExecutions(UUID firstId, UUID secondId) {
        ExecutionEntity firstExec = executionRepository.findById(firstId)
                .orElseThrow(() -> new IllegalArgumentException("First execution not found: " + firstId));
        ExecutionEntity secondExec = executionRepository.findById(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Second execution not found: " + secondId));

        ConfigurationEntity firstCfg = configurationRepository.findById(firstExec.getConfigurationId()).orElse(null);
        ConfigurationEntity secondCfg = configurationRepository.findById(secondExec.getConfigurationId()).orElse(null);

        Map<String, Object> firstStep4 = getStepMetrics(firstId, 4);
        Map<String, Object> secondStep4 = getStepMetrics(secondId, 4);

        Map<String, Object> firstStep5 = getStepMetrics(firstId, 5);
        Map<String, Object> secondStep5 = getStepMetrics(secondId, 5);

        Map<String, Object> firstStep6 = getStepMetrics(firstId, 6);
        Map<String, Object> secondStep6 = getStepMetrics(secondId, 6);

        Map<String, Object> parameterDifferences = new LinkedHashMap<>();
        parameterDifferences.put("firstConfigurationName", firstCfg != null ? firstCfg.getName() : "N/A");
        parameterDifferences.put("secondConfigurationName", secondCfg != null ? secondCfg.getName() : "N/A");
        parameterDifferences.put("firstRandomSeed", firstCfg != null ? firstCfg.getRandomSeed() : null);
        parameterDifferences.put("secondRandomSeed", secondCfg != null ? secondCfg.getRandomSeed() : null);
        parameterDifferences.put("firstGlobalParameters", firstCfg != null ? parseJsonMap(firstCfg.getGlobalParameters()) : Map.of());
        parameterDifferences.put("secondGlobalParameters", secondCfg != null ? parseJsonMap(secondCfg.getGlobalParameters()) : Map.of());

        double firstEvCount = getNumeric(firstStep4, "evPlusOpportunitiesFound");
        double secondEvCount = getNumeric(secondStep4, "evPlusOpportunitiesFound");
        double firstAvgEvPct = getNumeric(firstStep4, "averageEvPlusOnlyPct");
        double secondAvgEvPct = getNumeric(secondStep4, "averageEvPlusOnlyPct");
        double firstAvgClvPct = getNumeric(firstStep6, "averageClvEvPlusOnlyPct");
        double secondAvgClvPct = getNumeric(secondStep6, "averageClvEvPlusOnlyPct");
        double firstProjProfit = getNumeric(firstStep5, "projectedAnalyticalExpectedProfitUnits");
        double secondProjProfit = getNumeric(secondStep5, "projectedAnalyticalExpectedProfitUnits");
        double firstActualProfit = getNumeric(firstStep6, "actualProfitUnits");
        double secondActualProfit = getNumeric(secondStep6, "actualProfitUnits");

        Map<String, Object> metricDifferences = new LinkedHashMap<>();
        metricDifferences.put("evPlusOpportunitiesFound", Map.of("first", firstEvCount, "second", secondEvCount, "delta", round4(secondEvCount - firstEvCount)));
        metricDifferences.put("averageEvPlusPct", Map.of("first", firstAvgEvPct, "second", secondAvgEvPct, "delta", round4(secondAvgEvPct - firstAvgEvPct)));
        metricDifferences.put("averageClvEvPlusPct", Map.of("first", firstAvgClvPct, "second", secondAvgClvPct, "delta", round4(secondAvgClvPct - firstAvgClvPct)));
        metricDifferences.put("projected500BetsExpectedProfitUnits", Map.of("first", firstProjProfit, "second", secondProjProfit, "delta", round4(secondProjProfit - firstProjProfit)));
        metricDifferences.put("actualProfitUnits", Map.of("first", firstActualProfit, "second", secondActualProfit, "delta", round4(secondActualProfit - firstActualProfit)));

        String conclusion = String.format(
                "La segunda ejecucion (%s) detecto %.0f oportunidades EV+ (delta %+.0f), con un EV medio de %.2f%% (vs %.2f%%) y un CLV medio de %.2f%% (vs %.2f%%).",
                secondId, secondEvCount, (secondEvCount - firstEvCount), secondAvgEvPct, firstAvgEvPct, secondAvgClvPct, firstAvgClvPct
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("firstExecutionId", firstId);
        response.put("secondExecutionId", secondId);
        response.put("firstStatus", firstExec.getStatus());
        response.put("secondStatus", secondExec.getStatus());
        response.put("parameterDifferences", parameterDifferences);
        response.put("metricDifferences", metricDifferences);
        response.put("summaryConclusion", conclusion);
        response.put("comparedAt", Instant.now().toString());
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> generateExecutionReport(UUID executionId) {
        ExecutionEntity exec = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        List<Map<String, Object>> results = getResultsForExecution(executionId);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportTitle", "Informe Técnico de Ejecución Data Mining EV+ & CLV (@Porros365)");
        report.put("executionId", exec.getId());
        report.put("workflowId", exec.getWorkflowId());
        report.put("methodologyVersion", exec.getMethodologyVersion());
        report.put("codeVersion", exec.getCodeVersion());
        report.put("status", exec.getStatus());
        report.put("startedAt", exec.getStartedAt());
        report.put("finishedAt", exec.getFinishedAt());
        report.put("durationMs", exec.getDurationMs());
        report.put("stepResults", results);
        report.put("generatedAt", Instant.now().toString());
        return report;
    }

    private Map<String, Object> getStepMetrics(UUID executionId, int stepNumber) {
        return miningResultRepository.findByExecutionIdAndStepNumber(executionId, stepNumber)
                .map(r -> parseJsonMap(r.getMetricsSummary()))
                .orElse(Map.of());
    }

    private double getNumeric(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return 0.0;
        }
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double round4(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 10000.0) / 10000.0;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
