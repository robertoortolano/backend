package com.example.demo.initializer;

import com.example.demo.config.DefaultConfig;
import com.example.demo.config.DefaultConfigLoader;
import com.example.demo.entity.ItemType;
import com.example.demo.entity.Tenant;
import com.example.demo.service.ItemTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Order(3)
@RequiredArgsConstructor
public class TenantItemTypeInitializer implements TenantInitializer {

    private final ItemTypeService itemTypeService;

    @Override
    public void initialize(Tenant tenant) {
        DefaultConfig config = DefaultConfigLoader.load();

        List<ItemType> defaults = config.getItemTypes().stream()
                .map(dto -> {
                    ItemType it = new ItemType();
                    it.setName(dto.getName());
                    it.setDefaultItemType(true);
                    it.setTenant(tenant);
                    return it;
                })
                .toList();

        itemTypeService.saveAll(defaults);
    }
}
