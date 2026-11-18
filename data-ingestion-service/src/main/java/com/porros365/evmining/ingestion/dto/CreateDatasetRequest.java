package com.porros365.evmining.ingestion.dto;

import com.porros365.evmining.common.dto.MarketOddsRecordDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateDatasetRequest(
        @NotBlank String name,
        String description,
        @NotBlank String sourceType,
        String sourceReference,
        @NotEmpty List<@Valid MarketOddsRecordDto> records
) {
}
