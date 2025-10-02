package com.example.demo.initializer;

import com.example.demo.config.DefaultConfig;
import com.example.demo.config.DefaultConfigLoader;
import com.example.demo.entity.Field;
import com.example.demo.entity.Tenant;
import com.example.demo.service.FieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Order(1)
@RequiredArgsConstructor
public class TenantFieldInitializer implements TenantInitializer {

    private final FieldService fieldService;

    @Override
    public void initialize(Tenant tenant) {
        DefaultConfig config = DefaultConfigLoader.load();

        List<Field> defaults = config.getFields().stream()
                .map(dto -> {
                    Field field = new Field();
                    field.setName(dto.getName());
                    field.setDefaultField(true);
                    field.setTenant(tenant);
                    return field;
                })
                .toList();

        fieldService.saveAll(tenant, defaults);
    }
}
