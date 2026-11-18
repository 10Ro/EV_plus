package com.porros365.evmining.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkflowRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String methodologyVersion,
        String description,
        String sourceThreadUrl
) {
}
