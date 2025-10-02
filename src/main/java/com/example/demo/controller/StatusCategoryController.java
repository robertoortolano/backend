package com.example.demo.controller;

import com.example.demo.entity.Tenant;
import com.example.demo.enums.StatusCategory;
import com.example.demo.security.CurrentTenant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/statuses")
public class StatusCategoryController {

    @GetMapping("/categories")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public List<String> getStatusCategories(
            @CurrentTenant Tenant tenant
    ) {
        // Restituisce i nomi delle categorie come lista di stringhe
        return Arrays.stream(StatusCategory.values())
                .map(Enum::name)
                .toList();
    }
}
