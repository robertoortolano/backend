package com.example.demo.service.itemtypeset;

import com.example.demo.dto.ItemTypeConfigurationCreateDto;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Tenant;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.service.FieldSetLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemTypeSetFieldOrchestrator {

    private final FieldSetLookup fieldSetLookup;

    public void applyFieldSet(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            ItemTypeConfiguration configuration,
            ItemTypeConfigurationCreateDto dto,
            FieldSet defaultFieldSet
    ) {
        FieldSet requestedFieldSet = fieldSetLookup.getById(dto.fieldSetId(), tenant);
        
        if (ScopeType.TENANT.equals(itemTypeSet.getScope())) {
            // Per ItemTypeSet globali, usa direttamente il FieldSet richiesto
            configuration.setFieldSet(requestedFieldSet);
            return;
        }

        // Per ItemTypeSet di progetto: NON creare copie, usare solo FieldSet definiti nel progetto stesso
        if (itemTypeSet.getProject() == null) {
            throw new ApiException("ItemTypeSet di progetto deve avere un progetto associato");
        }

        // Verifica che il FieldSet richiesto sia di progetto e appartenga allo stesso progetto
        if (requestedFieldSet.getScope() != ScopeType.PROJECT) {
            throw new ApiException(
                "Per ItemTypeSet di progetto, è possibile utilizzare solo FieldSet definiti nel progetto stesso. " +
                "Il FieldSet selezionato è globale."
            );
        }

        if (requestedFieldSet.getProject() == null || !requestedFieldSet.getProject().getId().equals(itemTypeSet.getProject().getId())) {
            throw new ApiException(
                "Per ItemTypeSet di progetto, è possibile utilizzare solo FieldSet definiti nel progetto stesso. " +
                "Il FieldSet selezionato appartiene a un progetto diverso."
            );
        }

        // Usa direttamente il FieldSet di progetto senza clonare
        configuration.setFieldSet(requestedFieldSet);
    }
}


