package com.example.demo.dto;

import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.enums.ScopeType;

import java.util.List;

public record WorkflowDetailDto (
        Long id,
        String name,
        ScopeType scope,
        boolean defaultWorkflow,
        List<ItemTypeConfiguration> itemTypeConfigurations
){}
