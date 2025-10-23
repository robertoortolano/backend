package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.FieldConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fieldconfigurations")
@RequiredArgsConstructor
public class FieldConfigurationController {

    private final FieldConfigurationService fieldConfigurationService;

    @PostMapping("/project/{projectId}")
    @PreAuthorize("@securityService.canCreateFieldConfiguration(principal, #tenant, #projectId)")
    public ResponseEntity<FieldConfigurationViewDto> createProject(
            @Valid @RequestBody FieldConfigurationCreateDto createDto,
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant
    ) {
        FieldConfigurationViewDto created = fieldConfigurationService.createProjectFieldConfiguration(createDto, tenant, projectId);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("@securityService.canCreateFieldConfiguration(principal, #tenant, #projectId)")
    public ResponseEntity<List<FieldConfigurationViewDto>> getAllProjectFieldConfigurations(
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant
    ) {
        List<FieldConfigurationViewDto> configurations = fieldConfigurationService.getAllProjectFieldConfigurations(tenant, projectId);
        return ResponseEntity.ok(configurations);
    }

    @PostMapping
    @PreAuthorize("@securityService.canCreateFieldConfiguration(principal, #tenant, null)")
    public ResponseEntity<FieldConfigurationViewDto> createGlobal(
            @Valid @RequestBody FieldConfigurationCreateDto createDto,
            @CurrentTenant Tenant tenant
    ) {
        FieldConfigurationViewDto created = fieldConfigurationService.createGlobalFieldConfiguration(createDto, tenant);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.canDeleteFieldConfiguration(principal, #tenant, #id)")
    public ResponseEntity<Void> deleteFieldConfiguration(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        fieldConfigurationService.deleteFieldConfiguration(tenant, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.canEditFieldConfiguration(principal, #tenant, #id)")
    public ResponseEntity<FieldConfigurationViewDto> update(
            @PathVariable Long id,
            @RequestBody FieldConfigurationUpdateDto dto,
            @CurrentTenant Tenant tenant
    ) {

        var updated = fieldConfigurationService.updateConfiguration(tenant, id, dto);
        return ResponseEntity.ok(updated);
    }

    // Lista tutte le configurazioni di tutti i FieldSet? Oppure solo di un FieldSet specifico?
    @GetMapping
    @PreAuthorize("@securityService.canCreateFieldConfiguration(principal, #tenant, null)")  // scegli autorizzazioni giuste
    public ResponseEntity<List<FieldConfigurationViewDto>> getAllGlobalFieldConfigurations(
            @CurrentTenant Tenant tenant
    ) {
        // magari aggiungi filtro per fieldSetId se vuoi limitare
        List<FieldConfigurationViewDto> list = fieldConfigurationService.getAllGlobalFieldConfigurations(tenant);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.canCreateFieldConfiguration(principal, #tenant, null)")
    public ResponseEntity<FieldConfigurationViewDto> getGlobalById(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        var dto = fieldConfigurationService.getById(id, tenant);
        return ResponseEntity.ok(dto);
    }

}
