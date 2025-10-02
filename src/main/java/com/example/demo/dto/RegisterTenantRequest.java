package com.example.demo.dto;

public record RegisterTenantRequest (
    String subdomain,
    String licenseKey
) {}
