package com.porros365.evmining.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import com.porros365.evmining.common.messaging.StepExecutionCommandMessage;
import com.porros365.evmining.ingestion.dto.CreateDatasetRequest;
import com.porros365.evmining.ingestion.dto.ImportCsvDatasetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ingestiontestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class DatasetIngestionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldImportJsonAndCsvDatasetsAndRunStep1Consolidation() throws Exception {
        MarketOddsRecordDto monzaAtalanta = new MarketOddsRecordDto(
                "MONZA_ATALANTA_10", "FOOTBALL", "Serie A", "Monza vs Atalanta",
                "ASIAN_HANDICAP", "-0.25", "Atalanta -0.25", "SINGBET",
                1.83, 2.09, null, "BET365", 1.95, 1.71, "WON",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        MarketOddsRecordDto bosniaRomania = new MarketOddsRecordDto(
                "BOSNIA_ROMANIA_11", "FOOTBALL", "Nations League", "Bosnia vs Rumania",
                "MATCH_ODDS_1X2", "1X2", "Gana Rumania", "BETFAIR_EXCHANGE",
                4.08, 1.92, 3.55, "PAF", 4.60, 3.44, "LOST",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        CreateDatasetRequest request = new CreateDatasetRequest(
                "Porros365 Real Examples Dataset",
                "Monza vs Atalanta & Bosnia vs Rumania",
                "JSON",
                "https://x.com/Porros365/status/2122822935002132523",
                List.of(monzaAtalanta, bosniaRomania)
        );

        MvcResult createdResult = mockMvc.perform(post("/api/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.latestVersionId", notNullValue()))
                .andExpect(jsonPath("$.recordCount", is(2)))
                .andReturn();

        Map<?, ?> createdMap = objectMapper.readValue(createdResult.getResponse().getContentAsString(), Map.class);
        UUID datasetId = UUID.fromString(createdMap.get("id").toString());
        UUID versionId = UUID.fromString(createdMap.get("latestVersionId").toString());

        // Execute Step 1 Consolidation on this DatasetVersion
        StepExecutionCommandMessage step1Cmd = new StepExecutionCommandMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "ODDS_INGESTION_AND_CONSOLIDATION",
                UUID.randomUUID(),
                versionId,
                42L,
                false,
                Map.of("minAllowedOdds", 1.01, "maxAllowedOdds", 1000.0),
                Instant.now()
        );

        mockMvc.perform(post("/api/datasets/step1/consolidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1Cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.outputDatasetVersionId", notNullValue()))
                .andExpect(jsonPath("$.outputSummary.stageType", is("STEP1_CONSOLIDATED")))
                .andExpect(jsonPath("$.outputSummary.totalRecords", is(2)));

        // Verify CSV import endpoint
        String csvContent = """
                eventId,sport,competition,eventName,marketType,marketLine,selectionName,sharpBookmaker,sharpOddsSelection,sharpOddsOpposite,sharpOddsDraw,softBookmaker,softTakenOdds,closingFairOdds,outcomeStatus
                POST5_EXAMPLE,FOOTBALL,Premier League,Arsenal vs Chelsea,ASIAN_HANDICAP,0.0,Arsenal DNB,SBOBET,1.90,2.00,,BET365,2.05,1.87,WON
                """;
        ImportCsvDatasetRequest csvReq = new ImportCsvDatasetRequest(
                "CSV Imported Dataset", "Post 5 and 6 example", "X-Thread", csvContent
        );

        mockMvc.perform(post("/api/datasets/import-csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(csvReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType", is("CSV")))
                .andExpect(jsonPath("$.recordCount", is(1)));

        mockMvc.perform(get("/api/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        mockMvc.perform(get("/api/datasets/{datasetId}/versions", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void shouldRejectIncomplete1X2MarketMissingDrawOdds() throws Exception {
        MarketOddsRecordDto invalid1X2 = new MarketOddsRecordDto(
                "INVALID_1X2", "FOOTBALL", "LaLiga", "Team A vs Team B",
                "MATCH_ODDS_1X2", "1X2", "Team A", "SBOBET",
                2.10, 3.50, null, "BET365", 2.25, 2.00, "WON",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        CreateDatasetRequest request = new CreateDatasetRequest(
                "Invalid 1X2 Dataset", "Missing draw odds", "JSON", "test", List.of(invalid1X2)
        );

        mockMvc.perform(post("/api/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("INGESTION_VALIDATION_ERROR")));
    }
}
