package com.example.demo.controller;

import com.example.demo.dto.FieldSetRemovalImpactDto;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.FieldSetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@RestController
@RequestMapping("/api/field-sets")
@RequiredArgsConstructor
public class FieldSetImpactController {

    private final FieldSetService fieldSetService;
    private final ObjectMapper objectMapper;

    /**
     * Analizza gli impatti della rimozione di FieldConfiguration da un FieldSet
     */
    @PostMapping("/{fieldSetId}/analyze-removal-impact")
    @PreAuthorize("@securityService.canEditFieldSet(principal, #tenant, #fieldSetId)")
    public ResponseEntity<FieldSetRemovalImpactDto> analyzeRemovalImpact(
            @PathVariable Long fieldSetId,
            @RequestBody Set<Long> removedFieldConfigIds,
            @CurrentTenant Tenant tenant
    ) {
        FieldSetRemovalImpactDto impact = fieldSetService.analyzeFieldSetRemovalImpact(
                tenant, fieldSetId, removedFieldConfigIds);
        
        return ResponseEntity.ok(impact);
    }

    /**
     * Esporta il report degli impatti in formato JSON
     */
    @PostMapping("/{fieldSetId}/export-removal-impact")
    @PreAuthorize("@securityService.canEditFieldSet(principal, #tenant, #fieldSetId)")
    public ResponseEntity<byte[]> exportRemovalImpact(
            @PathVariable Long fieldSetId,
            @RequestBody Set<Long> removedFieldConfigIds,
            @CurrentTenant Tenant tenant
    ) throws IOException {
        FieldSetRemovalImpactDto impact = fieldSetService.analyzeFieldSetRemovalImpact(
                tenant, fieldSetId, removedFieldConfigIds);
        
        // Configura ObjectMapper per output leggibile
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Converti in JSON
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        objectMapper.writeValue(outputStream, impact);
        byte[] jsonBytes = outputStream.toByteArray();
        
        // Genera nome file con timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("fieldset_removal_impact_%d_%s.json", fieldSetId, timestamp);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(jsonBytes.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(jsonBytes);
    }

    /**
     * Esporta il report degli impatti in formato CSV
     */
    @PostMapping("/{fieldSetId}/export-removal-impact-csv")
    @PreAuthorize("@securityService.canEditFieldSet(principal, #tenant, #fieldSetId)")
    public ResponseEntity<byte[]> exportRemovalImpactCsv(
            @PathVariable Long fieldSetId,
            @RequestBody Set<Long> removedFieldConfigIds,
            @CurrentTenant Tenant tenant
    ) throws IOException {
        try {
            FieldSetRemovalImpactDto impact = fieldSetService.analyzeFieldSetRemovalImpact(
                    tenant, fieldSetId, removedFieldConfigIds);
        
        StringBuilder csv = new StringBuilder();
        
        // Header del CSV
        csv.append("Permission Type,ItemTypeSet ID,ItemTypeSet Name,Project ID,Project Name,");
        csv.append("Field Configuration ID,Field Configuration Name,Workflow Status ID,Workflow Status Name,");
        csv.append("Assigned Roles,Has Assignments\n");
        
        // Field Owner Permissions
        for (FieldSetRemovalImpactDto.PermissionImpact perm : impact.getFieldOwnerPermissions()) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,,,%s,%s\n",
                escapeCsv(perm.getPermissionType()),
                perm.getItemTypeSetId(),
                escapeCsv(perm.getItemTypeSetName()),
                perm.getProjectId() != null ? perm.getProjectId().toString() : "",
                perm.getProjectName() != null ? escapeCsv(perm.getProjectName()) : "",
                perm.getFieldConfigurationId() != null ? perm.getFieldConfigurationId().toString() : "",
                escapeCsv(perm.getFieldConfigurationName() != null ? perm.getFieldConfigurationName() : ""),
                escapeCsv(String.join(";", perm.getAssignedRoles())),
                perm.isHasAssignments()
            ));
        }
        
        // Field Status Permissions
        for (FieldSetRemovalImpactDto.PermissionImpact perm : impact.getFieldStatusPermissions()) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                escapeCsv(perm.getPermissionType()),
                perm.getItemTypeSetId(),
                escapeCsv(perm.getItemTypeSetName()),
                perm.getProjectId() != null ? perm.getProjectId().toString() : "",
                perm.getProjectName() != null ? escapeCsv(perm.getProjectName()) : "",
                perm.getFieldConfigurationId() != null ? perm.getFieldConfigurationId().toString() : "",
                escapeCsv(perm.getFieldConfigurationName() != null ? perm.getFieldConfigurationName() : ""),
                perm.getWorkflowStatusId() != null ? perm.getWorkflowStatusId().toString() : "",
                escapeCsv(perm.getWorkflowStatusName() != null ? perm.getWorkflowStatusName() : ""),
                escapeCsv(String.join(";", perm.getAssignedRoles())),
                perm.isHasAssignments()
            ));
        }
        
        // ItemTypeSet Roles
        for (FieldSetRemovalImpactDto.PermissionImpact perm : impact.getItemTypeSetRoles()) {
            csv.append(String.format("%s,%s,%s,%s,%s,,,,,%s,%s\n",
                escapeCsv(perm.getPermissionType()),
                perm.getItemTypeSetId(),
                escapeCsv(perm.getItemTypeSetName()),
                perm.getProjectId() != null ? perm.getProjectId().toString() : "",
                perm.getProjectName() != null ? escapeCsv(perm.getProjectName()) : "",
                escapeCsv(String.join(";", perm.getAssignedRoles())),
                perm.isHasAssignments()
            ));
        }
        
        // Genera nome file con timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("fieldset_removal_impact_%d_%s.csv", fieldSetId, timestamp);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(csv.length());
        
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csv.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Error generating CSV export: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Error generating CSV export: " + e.getMessage(), e);
        }
    }
    
    /**
     * Rimuove le permissions orfane dopo la conferma dell'utente
     */
    @PostMapping("/{fieldSetId}/remove-orphaned-permissions")
    @PreAuthorize("@securityService.canEditFieldSet(principal, #tenant, #fieldSetId)")
    public ResponseEntity<String> removeOrphanedPermissions(
            @PathVariable Long fieldSetId,
            @RequestBody Set<Long> removedFieldConfigIds,
            @CurrentTenant Tenant tenant
    ) {
        fieldSetService.removeOrphanedPermissions(tenant, fieldSetId, removedFieldConfigIds);
        
        return ResponseEntity.ok("Permissions orfane rimosse con successo");
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
