package com.example.demo.dto;

import java.util.Set;

/**
 * Request per applicare la migrazione selettiva delle permission
 */
public record ItemTypeConfigurationMigrationRequest(
    Long itemTypeConfigurationId,
    
    // Nuovi FieldSet e Workflow a cui applicare la migrazione
    Long newFieldSetId, // ID del nuovo FieldSet (null se non cambia)
    Long newWorkflowId, // ID del nuovo Workflow (null se non cambia)
    
    // Lista di permissionId da PRESERVARE (migrare al nuovo stato)
    Set<Long> preservePermissionIds,
    
    // Flag globali (opzionali, sovrascrivono preservePermissionIds)
    Boolean preserveAllPreservable, // true = preserva tutto ciò che può essere preservato
    Boolean removeAll // true = rimuovi tutto (ignora preservePermissionIds)
) {
    /**
     * Valida che non ci siano flag globali conflittuali
     */
    public boolean isValid() {
        if (preserveAllPreservable != null && removeAll != null) {
            return !(preserveAllPreservable && removeAll); // Non possono essere entrambi true
        }
        return true;
    }
}

