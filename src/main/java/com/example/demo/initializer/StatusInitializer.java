package com.example.demo.initializer;

import com.example.demo.config.DefaultConfig;
import com.example.demo.config.DefaultConfigLoader;
import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.repository.StatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Order(4)
@RequiredArgsConstructor
public class StatusInitializer implements TenantInitializer {

    private final StatusRepository statusRepository;

    // Mappa per caching locale per tenant
    private final Map<Long, Map<String, Status>> tenantStatusMap = new HashMap<>();

    @Override
    public void initialize(Tenant tenant) {
        DefaultConfig config = DefaultConfigLoader.load();
        Map<String, Status> statusMap = new HashMap<>();

        for (var dto : config.getStatus()) {
            String name = dto.getName().trim();

            // category ignorata perché non è nello Status
            Status status = statusRepository
                    .findByTenantIdAndName(tenant.getId(), name)
                    .orElseGet(() -> {
                        Status s = new Status();
                        s.setTenant(tenant);
                        s.setName(name);
                        s.setDefaultStatus(true);
                        return statusRepository.save(s);
                    });

            statusMap.put(name, status);
        }

        tenantStatusMap.put(tenant.getId(), statusMap);
    }
}
