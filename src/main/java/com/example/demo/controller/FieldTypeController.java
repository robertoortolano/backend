package com.example.demo.controller;

import com.example.demo.dto.FieldTypeDescriptorDto;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.FieldTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@PreAuthorize("hasRole('TENANT_ADMIN')")
@RequestMapping("/api/fieldtypes")
@RequiredArgsConstructor
public class FieldTypeController {

    private final FieldTypeService fieldTypeService;

    @GetMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public Map<String, FieldTypeDescriptorDto> getAllFieldTypes(
            @CurrentTenant Tenant tenant
            ) {
        return fieldTypeService.getAllFieldTypes();
    }

}


