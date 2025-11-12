package com.example.demo.service.itemtypeset;

import com.example.demo.dto.ItemTypeConfigurationCreateDto;
import com.example.demo.entity.ItemType;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Workflow;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.service.ItemTypeLookup;
import com.example.demo.service.WorkflowLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemTypeSetWorkflowOrchestrator {

    private final ItemTypeLookup itemTypeLookup;
    private final WorkflowLookup workflowLookup;

    public ItemTypeConfiguration applyWorkflowUpdates(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            ItemTypeConfigurationCreateDto dto,
            ItemTypeConfiguration existingConfiguration
    ) {
        ItemType itemType = itemTypeLookup.getById(tenant, dto.itemTypeId());
        Workflow workflow = workflowLookup.getByIdEntity(tenant, dto.workflowId());

        // Per ItemTypeSet di progetto: verifica che il workflow sia di progetto e appartenga allo stesso progetto
        if (!ScopeType.TENANT.equals(itemTypeSet.getScope()) && itemTypeSet.getProject() != null) {
            if (workflow.getScope() != ScopeType.PROJECT) {
                throw new ApiException(
                    "Per ItemTypeSet di progetto, è possibile utilizzare solo workflow definiti nel progetto stesso. " +
                    "Il workflow selezionato è globale."
                );
            }

            if (workflow.getProject() == null || !workflow.getProject().getId().equals(itemTypeSet.getProject().getId())) {
                throw new ApiException(
                    "Per ItemTypeSet di progetto, è possibile utilizzare solo workflow definiti nel progetto stesso. " +
                    "Il workflow selezionato appartiene a un progetto diverso."
                );
            }
        }

        ItemTypeConfiguration target = existingConfiguration != null ? existingConfiguration : new ItemTypeConfiguration();

        if (existingConfiguration == null) {
            target.setTenant(tenant);
            target.setScope(itemTypeSet.getScope());
            if (!ScopeType.TENANT.equals(itemTypeSet.getScope()) && itemTypeSet.getProject() != null) {
                target.setProject(itemTypeSet.getProject());
            } else {
                target.setProject(null);
            }
        }

        target.setItemType(itemType);
        target.setCategory(dto.category());
        target.setWorkflow(workflow);

        if (!ScopeType.TENANT.equals(itemTypeSet.getScope()) && itemTypeSet.getProject() != null) {
            target.setProject(itemTypeSet.getProject());
        }

        return target;
    }
}


