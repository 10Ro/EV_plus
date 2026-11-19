package com.porros365.evmining.orchestrator.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String username,
        String role,
        long expiresInSeconds
) {
}
