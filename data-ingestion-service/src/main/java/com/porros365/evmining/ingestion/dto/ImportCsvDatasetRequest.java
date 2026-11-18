package com.porros365.evmining.ingestion.dto;

import jakarta.validation.constraints.NotBlank;

public record ImportCsvDatasetRequest(
        @NotBlank String name,
        String description,
        String sourceReference,
        @NotBlank String csvContent
) {
}
