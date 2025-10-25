package com.example.demo.controller;

import com.example.demo.dto.WorkflowCreateDto;
import com.example.demo.dto.WorkflowDetailDto;
import com.example.demo.dto.WorkflowViewDto;
import com.example.demo.dto.WorkflowUpdateDto;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.WorkflowLookup;
import com.example.demo.service.WorkflowService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowLookup workflowLookup;

    // Get list of workflows (view DTO)
    @GetMapping
    @PreAuthorize("@securityService.canCreateWorkflow(principal, #tenant, null)")
    public ResponseEntity<List<WorkflowViewDto>> getAllWorkflows(
            @CurrentTenant Tenant tenant
            ) {
        List<WorkflowViewDto> workflows = workflowLookup.getAllByTenant(tenant);
        return ResponseEntity.ok(workflows);
    }

    // Get workflow detail by id
    @GetMapping("/{id}")
    @PreAuthorize("@securityService.canCreateWorkflow(principal, #tenant, null)")
    public ResponseEntity<WorkflowViewDto> getWorkflowById(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        WorkflowViewDto workflow = workflowLookup.getById(tenant, id);
        return ResponseEntity.ok(workflow);
    }

    // Create new workflow
    @PostMapping
    @PreAuthorize("@securityService.canCreateWorkflow(principal, #tenant, null)")
    public ResponseEntity<WorkflowViewDto> createGlobalWorkflow(
            @Valid @RequestBody WorkflowCreateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        WorkflowViewDto created = workflowService.createGlobal(dto, tenant);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowViewDto> updateWorkflow(
            @PathVariable Long id,
            @RequestBody WorkflowUpdateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        // ✅ Consistenza: se l'id in path e body non combaciano
        if (!id.equals(dto.id())) {
            return ResponseEntity.badRequest().build();
        }

        WorkflowViewDto updated = workflowService.updateWorkflow(id, dto, tenant);
        return ResponseEntity.ok(updated);
    }

    // Delete workflow (disabilitato per defaultWorkflow nel service)
    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.canDeleteWorkflow(principal, #tenant, #id)")
    public ResponseEntity<Void> deleteWorkflow(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        workflowService.delete(tenant, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("@securityService.canCreateWorkflow(principal, #tenant, null)")
    public ResponseEntity<WorkflowDetailDto> getWorkflowDetails(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        WorkflowDetailDto dto = workflowService.getWorkflowDetail(id, tenant);
        return ResponseEntity.ok(dto);
    }
}
