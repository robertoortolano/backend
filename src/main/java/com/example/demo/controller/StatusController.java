package com.example.demo.controller;

import com.example.demo.dto.StatusCreateDto;
import com.example.demo.dto.StatusDetailDto;
import com.example.demo.dto.StatusViewDto;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.StatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/statuses")
@RequiredArgsConstructor
public class StatusController {

    private final StatusService statusService;

    @PostMapping
    @PreAuthorize("@securityService.canCreateStatus(principal, #tenant)")
    public ResponseEntity<StatusViewDto> createStatus(
            @Valid @RequestBody StatusCreateDto statusCreateDto,
            @CurrentTenant Tenant tenant
    ) {
            return ResponseEntity.ok(statusService.createStatus(statusCreateDto, tenant));
    }

    @GetMapping
    @PreAuthorize("@securityService.canCreateStatus(principal, #tenant)")
    public ResponseEntity<List<StatusViewDto>> getAllStatuses(@CurrentTenant Tenant tenant) {
        return ResponseEntity.ok(statusService.getAllStatuses(tenant));
    }

    @GetMapping("/{statusId}")
    @PreAuthorize("@securityService.canCreateStatus(principal, #tenant)")
    public ResponseEntity<StatusViewDto> getStatus(
            @PathVariable Long statusId,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(statusService.getById(tenant, statusId));
    }

    @PutMapping("/{statusId}")
    @PreAuthorize("@securityService.canEditStatus(principal, #tenant, #statusId)")
    public ResponseEntity<StatusViewDto> updateStatus(
            @PathVariable Long statusId,
            @CurrentTenant Tenant tenant,
            @RequestBody StatusViewDto viewDto
    ) {
        return ResponseEntity.ok(statusService.updateStatus(tenant, statusId, viewDto));
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("@securityService.canCreateStatus(principal, #tenant)")
    public ResponseEntity<StatusDetailDto> getStatusDetails(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        StatusDetailDto dto = statusService.getStatusDetails(tenant, id);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/details")
    @PreAuthorize("@securityService.canCreateStatus(principal, #tenant)")
    public ResponseEntity<List<StatusDetailDto>> getAllStatusDetails(
            @CurrentTenant Tenant tenant
    ) {
        List<StatusDetailDto> dtos = statusService.getAllStatusDetails(tenant);
        return ResponseEntity.ok(dtos);
    }

    @DeleteMapping("/{statusId}")
    @PreAuthorize("@securityService.canEditStatus(principal, #tenant, #statusId)")
    public ResponseEntity<Void> deleteStatus(
            @PathVariable Long statusId,
            @CurrentTenant Tenant tenant
    ) {
        statusService.deleteStatus(tenant, statusId);
        return ResponseEntity.noContent().build();
    }

}
