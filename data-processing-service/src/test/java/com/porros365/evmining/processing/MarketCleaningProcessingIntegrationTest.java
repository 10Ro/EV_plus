package com.porros365.evmining.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import com.porros365.evmining.common.entity.DatasetEntity;
import com.porros365.evmining.common.entity.DatasetVersionEntity;
import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.common.repository.DatasetRepository;
import com.porros365.evmining.common.repository.DatasetVersionRepository;
import com.porros365.evmining.common.util.HashUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:processingtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class MarketCleaningProcessingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetVersionRepository datasetVersionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldDeduplicateAndFilterCorruptSharpMarginOutliersInStep2() throws Exception {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(UUID.randomUUID());
        dataset.setName("Step 2 Cleaning Test Dataset");
        dataset.setSourceType("JSON");
        datasetRepository.save(dataset);

        // 1. Valid record (1.90 / 2.00 -> margin 2.63%)
        MarketOddsRecordDto valid1 = new MarketOddsRecordDto(
                "EV_1", "football", "Premier League", "Arsenal vs Chelsea",
                "asian_handicap", "0.0", "Arsenal DNB", "sbobet",
                1.90, 2.00, null, "bet365", 2.05, 1.87, "won",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        // 2. Duplicate of EV_1 with updated soft odds
        MarketOddsRecordDto duplicate1 = new MarketOddsRecordDto(
                "EV_1", "FOOTBALL", "Premier League", "Arsenal vs Chelsea",
                "ASIAN_HANDICAP", "0.0", "Arsenal DNB", "SBOBET",
                1.90, 2.00, null, "BET365", 2.06, 1.87, "WON",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        // 3. Corrupt sharp market with 25% margin (1.60 / 1.60 -> 1.25 overround -> 25% margin > 8% max allowed)
        MarketOddsRecordDto corruptMargin = new MarketOddsRecordDto(
                "CORRUPT_1", "FOOTBALL", "Serie A", "Corrupt Match",
                "ASIAN_HANDICAP", "0.0", "Team X", "SINGBET",
                1.60, 1.60, null, "BET365", 2.00, 1.90, "LOST",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        String payload = objectMapper.writeValueAsString(List.of(valid1, duplicate1, corruptMargin));
        DatasetVersionEntity inputVersion = new DatasetVersionEntity();
        inputVersion.setId(UUID.randomUUID());
        inputVersion.setDatasetId(dataset.getId());
        inputVersion.setVersionNumber(1);
        inputVersion.setStageType("STEP1_CONSOLIDATED");
        inputVersion.setRecordCount(3);
        inputVersion.setChecksumSha256(HashUtil.sha256Hex(payload));
        inputVersion.setRecordsPayload(payload);
        inputVersion.setSummaryStats("{}");
        datasetVersionRepository.save(inputVersion);

        StepExecutionCommandMessage cmd = new StepExecutionCommandMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                "MARKET_CLEANING_AND_NORMALIZATION",
                UUID.randomUUID(),
                inputVersion.getId(),
                42L,
                false,
                Map.of(
                        "deduplicationStrategy", "KEEP_LATEST",
                        "maxSharpMarginAllowed", 0.08,
                        "minSharpMarginAllowed", -0.01
                ),
                Instant.now()
        );

        mockMvc.perform(post("/api/processing/step2/clean")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.outputDatasetVersionId", notNullValue()))
                .andExpect(jsonPath("$.outputSummary.inputRecords", is(3)))
                .andExpect(jsonPath("$.outputSummary.cleanedRecords", is(1)))
                .andExpect(jsonPath("$.outputSummary.droppedDuplicates", is(1)))
                .andExpect(jsonPath("$.outputSummary.droppedMarginOutliers", is(1)));
    }
}
