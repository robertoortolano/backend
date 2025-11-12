package com.example.demo.service.migration.analysis;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Workflow;

/**
 * Shared context for permission analysis strategies executed during ItemTypeConfiguration migration impact evaluation.
 */
public record MigrationAnalysisContext(
        ItemTypeConfiguration configuration,
        FieldSet oldFieldSet,
        FieldSet newFieldSet,
        Workflow oldWorkflow,
        Workflow newWorkflow,
        ItemTypeConfigurationMigrationImpactDto.FieldSetInfo oldFieldSetInfo,
        ItemTypeConfigurationMigrationImpactDto.FieldSetInfo newFieldSetInfo,
        ItemTypeConfigurationMigrationImpactDto.WorkflowInfo oldWorkflowInfo,
        ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo,
        boolean fieldSetChanged,
        boolean workflowChanged,
        Long itemTypeSetId,
        ItemTypeSet owningItemTypeSet,
        String itemTypeSetName
) {
}




