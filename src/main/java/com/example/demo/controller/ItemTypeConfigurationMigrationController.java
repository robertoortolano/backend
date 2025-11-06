package com.example.demo.controller;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.dto.ItemTypeConfigurationMigrationRequest;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ItemTypeConfigurationMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller per gestire la migrazione interattiva delle permission
 * quando cambiano FieldSet e/o Workflow in un ItemTypeConfiguration
 */
@RestController
@RequestMapping("/api/item-type-configurations")
@RequiredArgsConstructor
@Slf4j
public class ItemTypeConfigurationMigrationController {
    
    private final ItemTypeConfigurationMigrationService migrationService;
    
    /**
     * Analizza l'impatto della migrazione quando cambiano FieldSet e/o Workflow
     * 
     * @param itemTypeConfigurationId ID della ItemTypeConfiguration da analizzare
     * @param newFieldSetId ID del nuovo FieldSet (opzionale, se null non cambia)
     * @param newWorkflowId ID del nuovo Workflow (opzionale, se null non cambia)
     * @param tenant Tenant corrente
     * @return Report di impatto con permission preservabili selezionabili
     */
    @GetMapping("/{itemTypeConfigurationId}/migration-impact")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeConfigurationMigrationImpactDto> analyzeMigrationImpact(
            @PathVariable Long itemTypeConfigurationId,
            @RequestParam(required = false) Long newFieldSetId,
            @RequestParam(required = false) Long newWorkflowId,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeConfigurationMigrationImpactDto impact = migrationService.analyzeMigrationImpact(
                tenant,
                itemTypeConfigurationId,
                newFieldSetId,
                newWorkflowId
        );
        return ResponseEntity.ok(impact);
    }
    
    /**
     * Applica la migrazione selettiva delle permission
     * 
     * @param itemTypeConfigurationId ID della ItemTypeConfiguration
     * @param request Request con lista di permission da preservare e flag globali
     * @param tenant Tenant corrente
     * @return 204 No Content se successo
     */
    @PostMapping("/{itemTypeConfigurationId}/migrate-permissions")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<Void> applyMigration(
            @PathVariable Long itemTypeConfigurationId,
            @RequestBody ItemTypeConfigurationMigrationRequest request,
            @CurrentTenant Tenant tenant
    ) {
        if (!request.itemTypeConfigurationId().equals(itemTypeConfigurationId)) {
            return ResponseEntity.badRequest().build();
        }
        
        migrationService.applyMigration(tenant, itemTypeConfigurationId, request);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Esporta il report di migrazione in formato CSV
     * 
     * @param itemTypeConfigurationId ID della ItemTypeConfiguration
     * @param newFieldSetId ID del nuovo FieldSet (opzionale)
     * @param newWorkflowId ID del nuovo Workflow (opzionale)
     * @param tenant Tenant corrente
     * @return CSV file con tutte le permission e i dettagli di migrazione
     */
    @GetMapping("/{itemTypeConfigurationId}/export-migration-impact-csv")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<byte[]> exportMigrationImpactCsv(
            @PathVariable Long itemTypeConfigurationId,
            @RequestParam(required = false) Long newFieldSetId,
            @RequestParam(required = false) Long newWorkflowId,
            @CurrentTenant Tenant tenant
    ) {
        try {
            ItemTypeConfigurationMigrationImpactDto impact = migrationService.analyzeMigrationImpact(
                    tenant,
                    itemTypeConfigurationId,
                    newFieldSetId,
                    newWorkflowId
            );
            
            StringBuilder csv = new StringBuilder();
            
            // Header informativo
            csv.append("ItemTypeConfiguration Migration Impact Report\n");
            csv.append(String.format("ItemTypeConfiguration ID: %d\n", impact.getItemTypeConfigurationId()));
            csv.append(String.format("ItemTypeConfiguration Name: %s\n", escapeCsv(impact.getItemTypeConfigurationName())));
            csv.append(String.format("ItemTypeSet: %s\n", escapeCsv(impact.getItemTypeSetName() != null ? impact.getItemTypeSetName() : "N/A")));
            csv.append(String.format("ItemType: %s\n", escapeCsv(impact.getItemTypeName())));
            if (impact.isFieldSetChanged()) {
                csv.append(String.format("FieldSet: %s -> %s\n", 
                    escapeCsv(impact.getOldFieldSet() != null ? impact.getOldFieldSet().getFieldSetName() : "N/A"),
                    escapeCsv(impact.getNewFieldSet() != null ? impact.getNewFieldSet().getFieldSetName() : "N/A")));
            }
            if (impact.isWorkflowChanged()) {
                csv.append(String.format("Workflow: %s -> %s\n", 
                    escapeCsv(impact.getOldWorkflow() != null ? impact.getOldWorkflow().getWorkflowName() : "N/A"),
                    escapeCsv(impact.getNewWorkflow() != null ? impact.getNewWorkflow().getWorkflowName() : "N/A")));
            }
            csv.append("\n");
            
            // Header del CSV per le permission (16 colonne totali)
            csv.append("Permission Type,Permission ID,ItemTypeSet ID,ItemTypeSet Name,Project ID,Project Name,");
            csv.append("Entity Type,Entity ID,Entity Name,Field ID,Field Name,WorkflowStatus ID,WorkflowStatus Name,");
            csv.append("Matching Entity ID,Matching Entity Name,Assigned Roles\n");
            
            // Helper method per scrivere una riga di permission in modo uniforme (16 colonne totali)
            // Header: Permission Type,Permission ID,ItemTypeSet ID,ItemTypeSet Name,Project ID,Project Name,
            //         Entity Type,Entity ID,Entity Name,Field ID,Field Name,WorkflowStatus ID,WorkflowStatus Name,
            //         Matching Entity ID,Matching Entity Name,Assigned Roles
            java.util.function.Function<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact, String> writePermissionRow = perm -> {
                StringBuilder row = new StringBuilder();
                row.append(escapeCsv(perm.getPermissionType())).append(",");
                row.append(perm.getPermissionId()).append(",");
                row.append(perm.getItemTypeSetId() != null ? perm.getItemTypeSetId().toString() : "").append(",");
                row.append(escapeCsv(perm.getItemTypeSetName() != null ? perm.getItemTypeSetName() : "")).append(",");
                row.append(perm.getProjectId() != null ? perm.getProjectId().toString() : "").append(",");
                row.append(escapeCsv(perm.getProjectName() != null ? perm.getProjectName() : "")).append(",");
                
                // Entity Type (7)
                if ("FIELD_OWNERS".equals(perm.getPermissionType())) {
                    row.append("Field,");
                } else if ("STATUS_OWNERS".equals(perm.getPermissionType())) {
                    row.append("WorkflowStatus,");
                } else if ("EXECUTORS".equals(perm.getPermissionType())) {
                    row.append("Transition,");
                } else {
                    row.append("FieldStatus,");
                }
                
                // Entity ID (8)
                row.append(perm.getEntityId() != null ? perm.getEntityId().toString() : "").append(",");
                // Entity Name (9)
                row.append(escapeCsv(perm.getEntityName() != null ? perm.getEntityName() : "")).append(",");
                
                // Field ID (10) - solo per FieldStatus
                row.append(perm.getFieldId() != null ? perm.getFieldId().toString() : "").append(",");
                // Field Name (11) - solo per FieldStatus
                row.append(escapeCsv(perm.getFieldName() != null ? perm.getFieldName() : "")).append(",");
                // WorkflowStatus ID (12) - solo per FieldStatus
                row.append(perm.getWorkflowStatusId() != null ? perm.getWorkflowStatusId().toString() : "").append(",");
                // WorkflowStatus Name (13) - solo per FieldStatus
                row.append(escapeCsv(perm.getWorkflowStatusName() != null ? perm.getWorkflowStatusName() : "")).append(",");
                
                // Matching Entity ID (14)
                row.append(perm.getMatchingEntityId() != null ? perm.getMatchingEntityId().toString() : "").append(",");
                // Matching Entity Name (15)
                row.append(escapeCsv(perm.getMatchingEntityName() != null ? perm.getMatchingEntityName() : "")).append(",");
                // Assigned Roles (16)
                row.append(escapeCsv(String.join(";", perm.getAssignedRoles()))).append("\n");
                
                return row.toString();
            };
            
            // Field Owner Permissions (solo quelle con ruoli)
            for (ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact perm : impact.getFieldOwnerPermissions()) {
                if (perm.isHasAssignments()) {
                    csv.append(writePermissionRow.apply(perm));
                }
            }
            
            // Status Owner Permissions (solo quelle con ruoli)
            for (ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact perm : impact.getStatusOwnerPermissions()) {
                if (perm.isHasAssignments()) {
                    csv.append(writePermissionRow.apply(perm));
                }
            }
            
            // Field Status Permissions (solo quelle con ruoli)
            for (ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact perm : impact.getFieldStatusPermissions()) {
                if (perm.isHasAssignments()) {
                    csv.append(writePermissionRow.apply(perm));
                }
            }
            
            // Executor Permissions (solo quelle con ruoli)
            for (ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact perm : impact.getExecutorPermissions()) {
                if (perm.isHasAssignments()) {
                    csv.append(writePermissionRow.apply(perm));
                }
            }
            
            // Genera nome file con timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("itemtypeconfiguration_migration_impact_%d_%s.csv", itemTypeConfigurationId, timestamp);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(csv.length());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csv.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error generating CSV export", e);
            throw new com.example.demo.exception.ApiException("Error generating CSV export: " + e.getMessage(), e);
        }
    }
    
    /**
     * Utility method per escape dei valori CSV
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Se il valore contiene virgole, virgolette o newline, lo racchiudiamo tra virgolette
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

