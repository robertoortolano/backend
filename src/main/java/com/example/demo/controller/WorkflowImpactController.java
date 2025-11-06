package com.example.demo.controller;

import com.example.demo.dto.StatusRemovalImpactDto;
import com.example.demo.dto.TransitionRemovalImpactDto;
import com.example.demo.dto.WorkflowStatusUpdateDto;
import com.example.demo.dto.WorkflowUpdateDto;
import com.example.demo.dto.WorkflowViewDto;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.WorkflowService;
import com.example.demo.util.CsvUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowImpactController {

    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    /**
     * Analizza gli impatti della rimozione di Transition da un Workflow
     */
    @PostMapping("/{workflowId}/analyze-transition-removal-impact")
    @PreAuthorize("@securityService.canEditWorkflow(principal, #tenant, #workflowId)")
    public ResponseEntity<TransitionRemovalImpactDto> analyzeTransitionRemovalImpact(
            @PathVariable Long workflowId,
            @RequestBody Set<Long> removedTransitionIds,
            @CurrentTenant Tenant tenant
    ) {
        TransitionRemovalImpactDto impact = workflowService.analyzeTransitionRemovalImpact(
                tenant, workflowId, removedTransitionIds);
        
        return ResponseEntity.ok(impact);
    }

    /**
     * Esporta il report degli impatti in formato JSON
     */
    @PostMapping("/{workflowId}/export-transition-removal-impact")
    @PreAuthorize("@securityService.canEditWorkflow(principal, #tenant, #workflowId)")
    public ResponseEntity<byte[]> exportTransitionRemovalImpact(
            @PathVariable Long workflowId,
            @RequestBody Set<Long> removedTransitionIds,
            @CurrentTenant Tenant tenant
    ) throws IOException {
        TransitionRemovalImpactDto impact = workflowService.analyzeTransitionRemovalImpact(
                tenant, workflowId, removedTransitionIds);
        
        // Configura ObjectMapper per output leggibile
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Converti in JSON
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        objectMapper.writeValue(outputStream, impact);
        byte[] jsonBytes = outputStream.toByteArray();
        
        // Genera nome file con timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("workflow_transition_removal_impact_%d_%s.json", workflowId, timestamp);
        
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
    @PostMapping("/{workflowId}/export-transition-removal-impact-csv")
    @PreAuthorize("@securityService.canEditWorkflow(principal, #tenant, #workflowId)")
    public ResponseEntity<byte[]> exportTransitionRemovalImpactCsv(
            @PathVariable Long workflowId,
            @RequestBody Set<Long> removedTransitionIds,
            @CurrentTenant Tenant tenant
    ) throws IOException {
        try {
            TransitionRemovalImpactDto impact = workflowService.analyzeTransitionRemovalImpact(
                    tenant, workflowId, removedTransitionIds);
        
            StringBuilder csv = new StringBuilder();
            
            // Header del CSV
            csv.append("Permission Type,ItemTypeSet ID,ItemTypeSet Name,Project ID,Project Name,");
            csv.append("Transition ID,Transition Name,From Status,To Status,");
            csv.append("Assigned Roles,Has Assignments\n");
            
            // Executor Permissions
            for (TransitionRemovalImpactDto.PermissionImpact perm : impact.getExecutorPermissions()) {
                csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    CsvUtils.escapeCsv(perm.getPermissionType()),
                    perm.getItemTypeSetId(),
                    CsvUtils.escapeCsv(perm.getItemTypeSetName()),
                    perm.getProjectId() != null ? perm.getProjectId().toString() : "",
                    perm.getProjectName() != null ? CsvUtils.escapeCsv(perm.getProjectName()) : "",
                    perm.getTransitionId() != null ? perm.getTransitionId().toString() : "",
                    CsvUtils.escapeCsv(perm.getTransitionName() != null ? perm.getTransitionName() : ""),
                    CsvUtils.escapeCsv(perm.getFromStatusName() != null ? perm.getFromStatusName() : ""),
                    CsvUtils.escapeCsv(perm.getToStatusName() != null ? perm.getToStatusName() : ""),
                    CsvUtils.escapeCsv(String.join(";", perm.getAssignedRoles())),
                    perm.isHasAssignments()
                ));
            }
            
            return CsvUtils.createCsvResponse(csv.toString(), "workflow_transition_removal_impact", workflowId);
        } catch (Exception e) {
            log.error("Error generating CSV export", e);
            throw new com.example.demo.exception.ApiException("Error generating CSV export: " + e.getMessage(), e);
        }
    }
    
    /**
     * Rimuove una Transition in modo sicuro dopo aver analizzato gli impatti
     */
    @DeleteMapping("/{workflowId}/transitions/{transitionId}")
    @PreAuthorize("@securityService.canEditWorkflow(principal, #tenant, #workflowId)")
    public ResponseEntity<TransitionRemovalImpactDto> removeTransitionWithImpactAnalysis(
            @PathVariable Long workflowId,
            @PathVariable Long transitionId,
            @CurrentTenant Tenant tenant
    ) {
        // Prima analizza gli impatti
        Set<Long> transitionIds = Set.of(transitionId);
        TransitionRemovalImpactDto impact = workflowService.analyzeTransitionRemovalImpact(
                tenant, workflowId, transitionIds);
        
        // Se ci sono permission con assegnazioni, restituisci il report per conferma
        if (impact.getExecutorPermissions().stream().anyMatch(TransitionRemovalImpactDto.PermissionImpact::isHasAssignments)) {
            return ResponseEntity.ok(impact);
        }
        
        // Se non ci sono assegnazioni, procedi direttamente con la rimozione
        workflowService.removeOrphanedExecutorPermissions(tenant, workflowId, transitionIds);
        workflowService.removeTransition(tenant, transitionId);
        
        return ResponseEntity.ok(impact);
    }
    
    /**
     * Conferma la rimozione di una Transition dopo aver analizzato gli impatti
     */
    @PostMapping("/{workflowId}/transitions/{transitionId}/confirm-removal")
    @PreAuthorize("@securityService.canEditWorkflow(principal, #tenant, #workflowId)")
    public ResponseEntity<String> confirmTransitionRemoval(
            @PathVariable Long workflowId,
            @PathVariable Long transitionId,
            @CurrentTenant Tenant tenant
    ) {
        Set<Long> transitionIds = Set.of(transitionId);
        
        // Rimuovi le ExecutorPermissions orfane
        workflowService.removeOrphanedExecutorPermissions(tenant, workflowId, transitionIds);
        
        // Rimuovi la Transition
        workflowService.removeTransition(tenant, transitionId);
        
        return ResponseEntity.ok("Transition rimossa con successo");
    }
    
    /**
     * Conferma la rimozione delle Transition dopo l'analisi degli impatti
     */
    @PostMapping("/{workflowId}/confirm-transition-removal")
    @PreAuthorize("@securityService.canEditWorkflow(principal, #tenant, #workflowId)")
    public ResponseEntity<WorkflowViewDto> confirmTransitionRemoval(
            @PathVariable Long workflowId,
            @RequestBody WorkflowUpdateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        WorkflowViewDto result = workflowService.confirmTransitionRemoval(workflowId, dto, tenant);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{workflowId}/analyze-status-removal-impact")
    @PreAuthorize("@securityService.canEditWorkflow(principal, #tenant, #workflowId)")
    public ResponseEntity<StatusRemovalImpactDto> analyzeStatusRemovalImpact(
            @PathVariable Long workflowId,
            @RequestBody WorkflowUpdateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        // Identifica gli Status che verranno rimossi
        Set<Long> existingStatusIds = workflowService.getWorkflowStatusIds(workflowId, tenant);
        Set<Long> newStatusIds = dto.workflowStatuses().stream()
                .filter(ws -> ws.id() != null)
                .map(WorkflowStatusUpdateDto::id)
                .collect(Collectors.toSet());
        
        Set<Long> removedStatusIds = existingStatusIds.stream()
                .filter(id -> !newStatusIds.contains(id))
                .collect(Collectors.toSet());

        StatusRemovalImpactDto impact = workflowService.analyzeStatusRemovalImpact(tenant, workflowId, removedStatusIds);
        return ResponseEntity.ok(impact);
    }

    @PostMapping("/{workflowId}/confirm-status-removal")
    @PreAuthorize("@securityService.canEditWorkflow(principal, #tenant, #workflowId)")
    public ResponseEntity<WorkflowViewDto> confirmStatusRemoval(
            @PathVariable Long workflowId,
            @RequestBody WorkflowUpdateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        WorkflowViewDto result = workflowService.confirmStatusRemoval(workflowId, dto, tenant);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{workflowId}/export-status-removal-impact-csv")
    @PreAuthorize("@securityService.canEditWorkflow(principal, #tenant, #workflowId)")
    public ResponseEntity<byte[]> exportStatusRemovalImpactCsv(
            @PathVariable Long workflowId,
            @RequestBody Set<Long> removedStatusIds,
            @CurrentTenant Tenant tenant
    ) throws IOException {
        try {
            StatusRemovalImpactDto impact = workflowService.analyzeStatusRemovalImpact(
                    tenant, workflowId, removedStatusIds);
        
            StringBuilder csv = new StringBuilder();
            
            // Header del CSV
            csv.append("Permission Type,ItemTypeSet ID,ItemTypeSet Name,Project ID,Project Name,");
            csv.append("Workflow Status ID,Status Name,Status Category,");
            csv.append("Assigned Roles,Has Assignments\n");
            
            // Status Owner Permissions
            for (StatusRemovalImpactDto.PermissionImpact perm : impact.getStatusOwnerPermissions()) {
                csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    CsvUtils.escapeCsv(perm.getPermissionType()),
                    perm.getItemTypeSetId(),
                    CsvUtils.escapeCsv(perm.getItemTypeSetName()),
                    perm.getProjectId() != null ? perm.getProjectId().toString() : "",
                    perm.getProjectName() != null ? CsvUtils.escapeCsv(perm.getProjectName()) : "",
                    perm.getWorkflowStatusId() != null ? perm.getWorkflowStatusId().toString() : "",
                    CsvUtils.escapeCsv(perm.getStatusName() != null ? perm.getStatusName() : ""),
                    CsvUtils.escapeCsv(perm.getStatusCategory() != null ? perm.getStatusCategory() : ""),
                    CsvUtils.escapeCsv(String.join(";", perm.getAssignedRoles())),
                    perm.isHasAssignments()
                ));
            }
            
            return CsvUtils.createCsvResponse(csv.toString(), "workflow_status_removal_impact", workflowId);
        } catch (Exception e) {
            log.error("Error generating Status CSV export", e);
            throw new com.example.demo.exception.ApiException("Error generating Status CSV export: " + e.getMessage(), e);
        }
    }
    
}
