package com.example.demo.dto;

import com.example.demo.enums.ScopeType;

import java.util.List;


public record ItemTypeSetViewDto (
    Long id,
    String name,
    ScopeType scope,
    boolean defaultItemTypeSet,
    List<ProjectSummaryDto> projectsAssociation,
    List<ItemTypeConfigurationViewDto> itemTypeConfigurations,
    ProjectSummaryDto project
) {}
