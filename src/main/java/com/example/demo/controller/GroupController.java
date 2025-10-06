package com.example.demo.controller;

import com.example.demo.dto.GroupCreateDto;
import com.example.demo.dto.GroupUpdateDto;
import com.example.demo.dto.GroupViewDto;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<GroupViewDto>> getAllGroups(
            @CurrentTenant Tenant tenant,
            Principal principal) {
        List<GroupViewDto> groups = groupService.getAllForTenant(tenant);
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<GroupViewDto> getGroupById(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant,
            Principal principal) {
        GroupViewDto group = groupService.getById(id, tenant);
        return ResponseEntity.ok(group);
    }

    @PostMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<GroupViewDto> createGroup(
            @Valid @RequestBody GroupCreateDto dto,
            @CurrentTenant Tenant tenant,
            Principal principal) {
        GroupViewDto created = groupService.createGroup(tenant, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<GroupViewDto> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody GroupUpdateDto dto,
            @CurrentTenant Tenant tenant,
            Principal principal) {
        GroupViewDto updated = groupService.updateGroup(id, tenant, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant,
            Principal principal) {
        groupService.deleteGroup(id, tenant);
        return ResponseEntity.noContent().build();
    }
}
