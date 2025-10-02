package com.example.demo.controller;

import com.example.demo.entity.Tenant;
import com.example.demo.enums.ItemTypeCategory;
import com.example.demo.security.CurrentTenant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/item-types")
public class ItemTypeCategoryController {

    @GetMapping("/categories")

    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public List<String> getCategories(
            @CurrentTenant Tenant tenant
            ) {
        return Arrays.stream(ItemTypeCategory.values())
                .map(Enum::name)
                .toList();
    }
}

