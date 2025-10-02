package com.example.demo.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String message,
        Long tenantId,
        boolean success
) {}