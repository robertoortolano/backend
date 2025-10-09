package com.example.demo.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TenantDTO {
    private Long id;
    private String name;
    private String subdomain;
    private LocalDateTime createdAt;
}